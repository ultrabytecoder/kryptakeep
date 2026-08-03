package com.ultrabytecoder.kryptakeep.data

import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.PinRepository
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import com.ultrabytecoder.kryptakeep.domain.repository.VerifyResult
import com.ultrabytecoder.kryptakeep.security.Pbkdf2
import com.ultrabytecoder.kryptakeep.service.EncryptionService
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
import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64

@Serializable
data class PinSecureData(
    val pinHash: String,
    val salt: String,
    val iterations: Int,
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L,
    val lockoutCount: Int = 0
)

private const val PIN_DATA_KEY = "pin_data"

private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    val maxLen = maxOf(a.size, b.size)
    var diff = a.size xor b.size
    for (i in 0 until maxLen) {
        diff = diff or (a.getOrElse(i) { 0 }.toInt() xor b.getOrElse(i) { 0 }.toInt())
    }
    return diff == 0
}

class PinRepositoryImpl(
    private val settingsStorage: SettingsStorage,
    private val encryptionService: EncryptionService
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
                val encryptedBlob = settingsStorage.getString(PIN_DATA_KEY)
                if (encryptedBlob == null) {
                    _pinStateFlow.value = PinState.NotSetup
                    return@withContext
                }

                try {
                    val data = loadData(encryptedBlob)
                    _pinStateFlow.value = PinState.Setup(
                        failedAttempts = data.failedAttempts,
                        lockedUntil = data.lockedUntil,
                        isCorrupted = false
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

    override suspend fun setupPin(pin: String) = withContext(Dispatchers.Default) {
        require(pin.length == PinConfig.LENGTH && pin.all { it.isDigit() }) {
            "PIN must be ${PinConfig.LENGTH} digits"
        }

        val salt = CryptoRand.Default.nextBytes(ByteArray(PinConfig.SALT_SIZE))
        val hash = Pbkdf2.derive(
            password = pin,
            salt = salt,
            iterations = PinConfig.PBKDF2_ITERATIONS,
            derivedKeyLengthBytes = 32
        )

        val data = PinSecureData(
            pinHash = Base64.Default.encode(hash),
            salt = Base64.Default.encode(salt),
            iterations = PinConfig.PBKDF2_ITERATIONS
        )

        mutex.withLock {
            saveData(data)
            _pinStateFlow.value = PinState.Setup(
                failedAttempts = 0,
                lockedUntil = 0,
                isCorrupted = false
            )
        }
    }

    override suspend fun verifyPin(pin: String): VerifyResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            // Handle corrupted data
            val loaded = try {
                val encryptedBlob = settingsStorage.getString(PIN_DATA_KEY)
                    ?: return@withContext VerifyResult.Corrupted
                loadData(encryptedBlob)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _pinStateFlow.value = PinState.Setup(
                    failedAttempts = PinConfig.MAX_ATTEMPTS,
                    lockedUntil = 0,
                    isCorrupted = true
                )
                return@withContext VerifyResult.Corrupted
            }

            val now = System.currentTimeMillis()

            // Check lockout
            if (now < loaded.lockedUntil) {
                return@withContext VerifyResult.Locked(loaded.lockedUntil)
            }

            // Reset failed attempts if lockout has expired (lockoutCount persists for escalation)
            val currentData = if (loaded.lockedUntil > 0L && now >= loaded.lockedUntil) {
                val resetData = loaded.copy(failedAttempts = 0, lockedUntil = 0L)
                saveData(resetData)
                resetData
            } else {
                loaded
            }

            // PBKDF2 inside the lock — avoids stale data race
            val salt = Base64.Default.decode(currentData.salt)
            val testHash = Pbkdf2.derive(
                password = pin,
                salt = salt,
                iterations = currentData.iterations,
                derivedKeyLengthBytes = 32
            )
            val storedHash = Base64.Default.decode(currentData.pinHash)

            if (constantTimeEquals(testHash, storedHash)) {
                val resetData = currentData.copy(failedAttempts = 0, lockedUntil = 0L, lockoutCount = 0)
                saveData(resetData)
                _pinStateFlow.value = PinState.Setup(
                    failedAttempts = 0,
                    lockedUntil = 0,
                    isCorrupted = false
                )
                VerifyResult.Success
            } else {
                val newFailed = currentData.failedAttempts + 1
                val lockoutSeconds = if (newFailed >= PinConfig.MAX_ATTEMPTS) {
                    val newLockoutCount = currentData.lockoutCount + 1
                    val escalation = newLockoutCount - 1
                    val shift = escalation.coerceAtMost(62)
                    val maxMultiplier = PinConfig.MAX_LOCKOUT.inWholeSeconds / PinConfig.INITIAL_LOCKOUT.inWholeSeconds
                    val multiplier = (1L shl shift).coerceAtMost(maxMultiplier)
                    PinConfig.INITIAL_LOCKOUT.inWholeSeconds * multiplier
                } else {
                    0L
                }

                val lockedUntil = if (lockoutSeconds > 0L) now + lockoutSeconds * 1000 else 0L
                val remaining = PinConfig.MAX_ATTEMPTS - newFailed

                val updatedData = currentData.copy(
                    failedAttempts = newFailed,
                    lockedUntil = lockedUntil,
                    lockoutCount = if (lockoutSeconds > 0L) currentData.lockoutCount + 1 else currentData.lockoutCount
                )
                saveData(updatedData)

                _pinStateFlow.value = PinState.Setup(
                    failedAttempts = newFailed,
                    lockedUntil = lockedUntil,
                    isCorrupted = false
                )

                if (lockedUntil > now) {
                    VerifyResult.Locked(lockedUntil)
                } else {
                    VerifyResult.WrongPin(remaining.coerceAtLeast(1))
                }
            }
        }
    }

    override suspend fun resetLockState() = withContext(Dispatchers.Default) {
        mutex.withLock {
            val encryptedBlob = settingsStorage.getString(PIN_DATA_KEY) ?: return@withContext
            val data = loadData(encryptedBlob)
            val resetData = data.copy(failedAttempts = 0, lockedUntil = 0L, lockoutCount = 0)
            saveData(resetData)
            _pinStateFlow.value = PinState.Setup(
                failedAttempts = 0,
                lockedUntil = 0,
                isCorrupted = false
            )
        }
    }

    private fun loadData(encryptedBlob: String): PinSecureData {
        val encryptedBytes = Base64.Default.decode(encryptedBlob)
        val decrypted = encryptionService.decrypt(encryptedBytes)
        return json.decodeFromString<PinSecureData>(decrypted.decodeToString())
    }

    private fun saveData(data: PinSecureData) {
        val jsonString = json.encodeToString(PinSecureData.serializer(), data)
        val encrypted = encryptionService.encrypt(jsonString.encodeToByteArray())
        settingsStorage.putString(PIN_DATA_KEY, Base64.Default.encode(encrypted))
    }
}