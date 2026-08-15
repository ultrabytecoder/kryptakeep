package com.ultrabytecoder.kryptakeep.data

import com.ultrabytecoder.kryptakeep.domain.repository.ChangePinResult
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import com.ultrabytecoder.kryptakeep.domain.repository.VerifyResult
import com.ultrabytecoder.kryptakeep.security.AesGcmAuthenticationException
import com.ultrabytecoder.kryptakeep.security.HardwareKeyInvalidatedException
import com.ultrabytecoder.kryptakeep.security.HardwareKeyStore
import com.ultrabytecoder.kryptakeep.security.KeyManager
import com.ultrabytecoder.kryptakeep.security.SessionManager
import com.ultrabytecoder.kryptakeep.security.monotonicNowMillis
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PinSecureData(
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L,
    val lockoutCount: Int = 0,
    // Monotonic lockout window (ms since boot, see monotonicNowMillis). Guards the
    // lockout against wall-clock tampering; only authoritative within the same boot
    // (monotonicLockedAt acts as the same-boot marker).
    val monotonicLockedAt: Long = 0L,
    val monotonicLockedUntil: Long = 0L
)

private const val PIN_DATA_KEY = "pin_data"

/**
 * PIN lifecycle: setup, verification (via envelope unwrap of the DEK — the correct PIN
 * is proven by a successful GCM authentication), lockout bookkeeping.
 *
 * The DEK key material lives in [KeyManager]; this repository only tracks attempt
 * counters and lockout state. PIN verification hands the unwrapped DEK to
 * [SessionManager], which opens the SQLCipher database for the session.
 *
 * The lockout state ([PinSecureData]) is stored hardware-encrypted (AES-GCM via the
 * device key): an attacker with file-system access cannot reset the attempt counters
 * or lockout timestamps — any tamper fails authentication and is treated as
 * corruption (max attempts, force recovery).
 */
class PinRepositoryImpl(
    private val settingsStorage: SettingsStorage,
    private val keyManager: KeyManager,
    private val sessionManager: SessionManager,
    private val walletRepository: com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
) : PinRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val initScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _pinStateFlow = MutableStateFlow<PinState>(PinState.Loading)

    override val pinStateFlow: StateFlow<PinState> = _pinStateFlow.asStateFlow()

    init {
        initScope.launch {
            loadState()
        }
    }

    private suspend fun loadState() {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val stored = settingsStorage.getString(PIN_DATA_KEY)
                if (stored == null) {
                    _pinStateFlow.value = PinState.NotSetup
                    return@withContext
                }

                try {
                    val data = loadData(stored)
                    if (isLegacyStored(stored)) {
                        // Migrate plaintext legacy lockout data to hardware-encrypted.
                        saveData(data)
                    }
                    val keyMaterialOk = keyManager.hasPinKeyMaterial()
                    _pinStateFlow.value = PinState.Setup(
                        failedAttempts = data.failedAttempts,
                        lockedUntil = data.lockedUntil,
                        isCorrupted = !keyMaterialOk
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _pinStateFlow.value = PinState.Setup(
                        failedAttempts = PinConfig.MAX_ATTEMPTS,
                        lockedUntil = 0,
                        isCorrupted = true
                    )
                }
            }
        }
    }

    override suspend fun setupPin(pin: CharArray) = withContext(Dispatchers.Default) {
        require(pin.size == PinConfig.LENGTH && pin.all { it.isDigit() }) {
            "PIN must be ${PinConfig.LENGTH} digits"
        }

        mutex.withLock {
            // The PIN is set up first (startup wizard), so no raw DEK exists yet —
            // always generate a fresh one and open the (empty) database with it.
            // Recreating the DB is safe here: there is no wallet data at stake
            // (this is the fresh-setup / recovery path, see SessionManager.unlockRecreating).
            keyManager.deleteAll()
            val dek = try {
                keyManager.generateAndWrapDek(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Hardware key / crypto failure — surface a user-friendly message
                // on the PIN screen and leave no partial key material behind.
                keyManager.deleteAll()
                throw IllegalStateException("Failed to generate key", e)
            }

            val opened = try {
                sessionManager.unlockRecreating(dek)
            } catch (e: CancellationException) {
                dek.wipe()
                keyManager.deleteAll()
                throw e
            } catch (e: Exception) {
                false
            }
            if (!opened) {
                dek.wipe()
                keyManager.deleteAll()
                throw IllegalStateException("Failed to initialize secure storage")
            }
            // Success: sessionManager owns `dek`.

            saveData(PinSecureData())
            _pinStateFlow.value = PinState.Setup(
                failedAttempts = 0,
                lockedUntil = 0,
                isCorrupted = false
            )
        }
    }

    override suspend fun verifyPin(pin: CharArray): VerifyResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            var dek: ByteArray? = null

            val result: VerifyResult = when (val gate = loadLockoutGate()) {
                // Corrupted data (tampered lockout state) — force recovery.
                is LockoutGate.Corrupted -> VerifyResult.Corrupted

                is LockoutGate.Locked -> VerifyResult.Locked(gate.lockedUntil)

                is LockoutGate.Open -> {
                    // Envelope unwrap inside the lock — avoids stale data races.
                    // A correct PIN is proven by the GCM authentication, no stored hash needed.
                    // Hardware-key invalidation is distinct from a wrong PIN → Corrupted.
                    var hwInvalidated = false
                    dek = try {
                        keyManager.unwrapDekWithPin(pin)
                    } catch (e: HardwareKeyInvalidatedException) {
                        hwInvalidated = true
                        null
                    }

                    if (hwInvalidated) {
                        _pinStateFlow.value = PinState.Setup(
                            failedAttempts = PinConfig.MAX_ATTEMPTS,
                            lockedUntil = 0,
                            isCorrupted = true
                        )
                        VerifyResult.Corrupted
                    } else if (dek != null) {
                        val opened = sessionManager.unlock(dek)
                        if (!opened) {
                            dek.wipe()
                            dek = null
                            _pinStateFlow.value = PinState.Setup(
                                failedAttempts = PinConfig.MAX_ATTEMPTS,
                                lockedUntil = 0,
                                isCorrupted = true
                            )
                            VerifyResult.Corrupted
                        } else {
                            val resetData = gate.data.copy(
                                failedAttempts = 0,
                                lockedUntil = 0L,
                                lockoutCount = 0,
                                monotonicLockedAt = 0L,
                                monotonicLockedUntil = 0L
                            )
                            saveData(resetData)
                            _pinStateFlow.value = PinState.Setup(
                                failedAttempts = 0,
                                lockedUntil = 0,
                                isCorrupted = false
                            )
                            VerifyResult.Success
                        }
                    } else {
                        recordWrongPin(gate.data, gate.now, gate.nowMono)
                    }
                }
            }
            // Wipe the unwrapped DEK unless the session took ownership of it
            // (VerifyResult.Success hands the key over to SessionManager).
            if (result !is VerifyResult.Success) {
                dek?.wipe()
            }

            result
        }
    }

    override suspend fun changePin(oldPin: CharArray, newPin: CharArray): ChangePinResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                // 1. Lockout gate shared with verifyPin (NEW-2): a locked-out user
                //    cannot brute-force the old PIN through the change-PIN screen.
                val result: ChangePinResult = when (val gate = loadLockoutGate()) {
                    is LockoutGate.Corrupted ->
                        ChangePinResult.Failed("PIN data is corrupted. Recovery required.")
                    is LockoutGate.Locked ->
                        ChangePinResult.Locked(gate.lockedUntil)
                    is LockoutGate.Open -> {
                        // 2. Verify the old PIN (GCM-authenticated DEK unwrap, no stored
                        //    verifier). The unwrapped DEK is kept for step 3 — a single
                        //    PBKDF2 derivation for the whole change (NEW-12).
                        var oldDek: ByteArray? = null
                        try {
                            oldDek = keyManager.unwrapDekWithPin(oldPin)
                        } catch (e: HardwareKeyInvalidatedException) {
                            return@withLock ChangePinResult.Failed("PIN data is corrupted. Recovery required.")
                        }

                        if (oldDek == null) {
                            // Wrong old PIN — count the attempt through the shared
                            // lockout logic (escalating lockout, NEW-2).
                            return@withLock when (val r = recordWrongPin(gate.data, gate.now, gate.nowMono)) {
                                is VerifyResult.Locked -> ChangePinResult.Locked(r.lockedUntil)
                                else -> ChangePinResult.WrongOldPin
                            }
                        }

                        // 3. Switch the DEK envelope to the new PIN — the mnemonic is stored
                        //    plaintext in the SQLCipher database and needs no re-keying
                        //    (the new DEK encrypts it as soon as the DB is rewritten).
                        //    Any exception (hardware key failure) is treated as a wrap
                        //    failure so the change is aborted (NEW-5).
                        val rewrapped = try {
                            keyManager.rewrapDekWithDek(oldDek, newPin)
                        } catch (e: CancellationException) {
                            oldDek.wipe()
                            throw e
                        } catch (e: Exception) {
                            false
                        } finally {
                            oldDek.wipe()
                        }
                        if (!rewrapped) {
                            ChangePinResult.Failed("Failed to update PIN key material")
                        } else {
                            // 4. Success — the old PIN verified, so clear the attempt counters.
                            val resetData = gate.data.copy(
                                failedAttempts = 0,
                                lockedUntil = 0L,
                                lockoutCount = 0,
                                monotonicLockedAt = 0L,
                                monotonicLockedUntil = 0L
                            )
                            saveData(resetData)
                            _pinStateFlow.value = PinState.Setup(
                                failedAttempts = 0,
                                lockedUntil = 0,
                                isCorrupted = false
                            )
                            ChangePinResult.Success
                        }
                    }
                }
                result
            }
        }

    override suspend fun resetLockState() = withContext(Dispatchers.Default) {
        mutex.withLock {
            val stored = settingsStorage.getString(PIN_DATA_KEY) ?: return@withContext
            val data = try {
                loadData(stored)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Corrupt/tampered lockout state — treat as corrupted, force recovery.
                _pinStateFlow.value = PinState.Setup(
                    failedAttempts = PinConfig.MAX_ATTEMPTS,
                    lockedUntil = 0,
                    isCorrupted = true
                )
                return@withLock
            }
            val resetData = data.copy(
                failedAttempts = 0,
                lockedUntil = 0L,
                lockoutCount = 0,
                monotonicLockedAt = 0L,
                monotonicLockedUntil = 0L
            )
            saveData(resetData)
            _pinStateFlow.value = PinState.Setup(
                failedAttempts = 0,
                lockedUntil = 0,
                isCorrupted = false
            )
        }
    }

    /**
     * Shared lockout gate for every PIN-verified operation ([verifyPin] and
     * [changePin]): loads the stored lockout state, handles corruption, checks the
     * hybrid wall/monotonic lockout clock and resets expired attempt counters.
     */
    private sealed interface LockoutGate {
        /** Lockout state missing/tampered — the caller must force recovery. */
        data object Corrupted : LockoutGate

        /** The PIN is currently locked out; [lockedUntil] is the wall-clock expiry. */
        data class Locked(val lockedUntil: Long) : LockoutGate

        /** PIN verification may proceed; carries the current counters and the
         * wall/monotonic timestamps captured at gate evaluation time. */
        data class Open(
            val data: PinSecureData,
            val now: Long,
            val nowMono: Long
        ) : LockoutGate
    }

    private fun loadLockoutGate(): LockoutGate {
        // Handle corrupted data
        val loaded = try {
            val stored = settingsStorage.getString(PIN_DATA_KEY)
            if (stored == null) null else loadData(stored)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _pinStateFlow.value = PinState.Setup(
                failedAttempts = PinConfig.MAX_ATTEMPTS,
                lockedUntil = 0,
                isCorrupted = true
            )
            null
        }
        if (loaded == null) return LockoutGate.Corrupted

        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val nowMono = monotonicNowMillis()

        // Hybrid lockout clock: wall time (survives reboots) OR monotonic
        // time (immune to user clock changes, authoritative within the same
        // boot — monotonicLockedAt is the same-boot marker; after a reboot
        // monotonic restarts below it and the wall clock takes over).
        val wallLocked = now < loaded.lockedUntil
        val monoLocked = nowMono >= loaded.monotonicLockedAt &&
            nowMono < loaded.monotonicLockedUntil

        if (wallLocked || monoLocked) {
            val remainingMs = maxOf(
                loaded.lockedUntil - now,
                loaded.monotonicLockedUntil - nowMono
            ).coerceAtLeast(0L)
            return LockoutGate.Locked(now + remainingMs)
        }

        // Reset failed attempts if the lockout has expired (lockoutCount persists for escalation)
        val currentData = if (loaded.lockedUntil > 0L || loaded.monotonicLockedUntil > 0L) {
            val resetData = loaded.copy(
                failedAttempts = 0,
                lockedUntil = 0L,
                monotonicLockedAt = 0L,
                monotonicLockedUntil = 0L
            )
            saveData(resetData)
            resetData
        } else {
            loaded
        }
        return LockoutGate.Open(currentData, now, nowMono)
    }

    /**
     * Records a wrong-PIN attempt with escalating lockout (shared by [verifyPin]
     * and [changePin], so no PIN-verified entry point bypasses the rate limit).
     */
    private fun recordWrongPin(data: PinSecureData, now: Long, nowMono: Long): VerifyResult {
        val newFailed = data.failedAttempts + 1
        val lockoutSeconds = if (newFailed >= PinConfig.MAX_ATTEMPTS) {
            val newLockoutCount = data.lockoutCount + 1
            val escalation = newLockoutCount - 1
            val shift = escalation.coerceAtMost(62)
            val maxMultiplier = PinConfig.MAX_LOCKOUT.inWholeSeconds / PinConfig.INITIAL_LOCKOUT.inWholeSeconds
            val multiplier = (1L shl shift).coerceAtMost(maxMultiplier)
            PinConfig.INITIAL_LOCKOUT.inWholeSeconds * multiplier
        } else {
            0L
        }

        val lockedUntil = if (lockoutSeconds > 0L) now + lockoutSeconds * 1000 else 0L
        val monoLockedAt = if (lockoutSeconds > 0L) nowMono else 0L
        val monoLockedUntil = if (lockoutSeconds > 0L) nowMono + lockoutSeconds * 1000 else 0L
        val remaining = PinConfig.MAX_ATTEMPTS - newFailed

        val updatedData = data.copy(
            failedAttempts = newFailed,
            lockedUntil = lockedUntil,
            monotonicLockedAt = monoLockedAt,
            monotonicLockedUntil = monoLockedUntil,
            lockoutCount = if (lockoutSeconds > 0L) data.lockoutCount + 1 else data.lockoutCount
        )
        saveData(updatedData)

        _pinStateFlow.value = PinState.Setup(
            failedAttempts = newFailed,
            lockedUntil = lockedUntil,
            isCorrupted = false
        )

        return if (lockedUntil > now) {
            VerifyResult.Locked(lockedUntil)
        } else {
            VerifyResult.WrongPin(remaining.coerceAtLeast(1))
        }
    }

    private fun isLegacyStored(stored: String): Boolean = stored.startsWith("{")

    /**
     * Decodes [stored] lockout data: legacy installs kept plaintext JSON; current
     * installs hardware-encrypt it (tampering fails authentication here).
     *
     * @throws Exception on corruption/tamper — callers treat it as corrupted state.
     */
    private fun loadData(stored: String): PinSecureData {
        if (isLegacyStored(stored)) {
            return json.decodeFromString<PinSecureData>(stored)
        }
        val blob = Base64.Default.decode(stored)
        val plain = HardwareKeyStore.decrypt(blob)
        return try {
            json.decodeFromString<PinSecureData>(plain.decodeToString())
        } finally {
            plain.wipe()
        }
    }

    private fun saveData(data: PinSecureData) {
        val plain = json.encodeToString(PinSecureData.serializer(), data).encodeToByteArray()
        try {
            val blob = HardwareKeyStore.encrypt(plain)
            settingsStorage.putString(PIN_DATA_KEY, Base64.Default.encode(blob))
        } finally {
            plain.wipe()
        }
    }
}