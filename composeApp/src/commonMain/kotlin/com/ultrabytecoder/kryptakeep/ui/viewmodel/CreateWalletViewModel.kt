package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateWalletUseCase
import com.ultrabytecoder.kryptakeep.security.EntropyCombiner
import com.ultrabytecoder.kryptakeep.security.SecureMnemonicCode
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CreateWalletViewModel(
    private val createWalletUseCase: CreateWalletUseCase
) : ViewModel() {

    enum class Mode { GENERATE_NEW, RESTORE_EXISTING }
    enum class Step { SETUP, PASSPHRASE, GESTURE, REVEAL }

    sealed class Result {
        data class Success(val walletId: Long) : Result()
        data class Error(val message: String) : Result()
    }

    private val _step = MutableStateFlow(Step.SETUP)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _finalMnemonic = MutableStateFlow<CharArray?>(null)
    val finalMnemonic: StateFlow<CharArray?> = _finalMnemonic.asStateFlow()

    // One-shot creation events. A rendezvous Channel is thread-safe (the
    // creation coroutine sends from Dispatchers.Default, the flow collects on
    // Main) and delivers the event instantly — no polling, no retained value
    // that a recomposition could re-observe.
    private val createdWalletChannel = Channel<Long>(Channel.CONFLATED)

    private val _mode = MutableStateFlow(Mode.GENERATE_NEW)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _wordCount = MutableStateFlow(24)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val _useGesture = MutableStateFlow(false)
    val useGesture: StateFlow<Boolean> = _useGesture.asStateFlow()

    private var walletName: String = "My wallet"

    // Secrets — all wiped in onCleared and after consumption.
    private var passphrase: CharArray = CharArray(0)
    private var gestureDigest: ByteArray? = null
    private var generateJob: Job? = null

    val isGenerateMode: Boolean get() = _mode.value == Mode.GENERATE_NEW
    val currentWordCount: Int get() = _wordCount.value
    val useGestureEnabled: Boolean get() = _useGesture.value
    val walletNameValue: String get() = walletName

    fun setWalletName(name: String) {
        walletName = name
    }

    fun setMode(newMode: Mode) {
        if (newMode == _mode.value) return
        _mode.value = newMode
        // Wipe any passphrase the user may have configured on the previous
        // mode so a secret never leaks into the wrong creation path.
        passphrase.wipe()
        passphrase = CharArray(0)
        // The gesture belongs to the generate-new path; never carry its
        // digest across a mode switch.
        gestureDigest?.wipe()
        gestureDigest = null
        invalidateGenerated()
    }

    fun setWordCount(count: Int) {
        require(count in SecureMnemonicCode.SUPPORTED_WORD_COUNTS) {
            "invalid word count $count"
        }
        if (count == _wordCount.value) return
        _wordCount.value = count
        invalidateGenerated()
    }

    fun setUseGesture(use: Boolean) {
        if (use == _useGesture.value) return
        _useGesture.value = use
        if (!use) {
            gestureDigest?.wipe()
            gestureDigest = null
        }
        invalidateGenerated()
    }

    /**
     * Takes ownership of [passphrase], wiping the previous one. An empty
     * [chars] is ignored when a non-empty passphrase is already set: the
     * passphrase screen's local state is lost on back-navigation, and a
     * stale "skip" submission must never silently drop a configured
     * passphrase.
     */
    fun setPassphrase(chars: CharArray) {
        if (chars.isEmpty() && passphrase.isNotEmpty()) return
        passphrase.wipe()
        passphrase = chars
    }

    /** True when a non-empty passphrase has been configured. */
    val hasPassphrase: Boolean get() = passphrase.isNotEmpty()

    /**
     * Takes ownership of [digest], wiping the previous one. A new gesture
     * invalidates any already-generated mnemonic: the next Reveal must be
     * generated from THIS digest, never from the previous gesture.
     */
    fun storeGestureDigest(digest: ByteArray) {
        gestureDigest?.wipe()
        gestureDigest = digest
        invalidateGenerated()
    }

    fun proceedFromSetup() {
        // Generate mode routes through the gesture screen (when enabled)
        // before the passphrase screen; restore mode handles creation
        // inline and does not change step.
        if (_mode.value == Mode.GENERATE_NEW) {
            _step.value = if (_useGesture.value) Step.GESTURE else Step.PASSPHRASE
        }
    }

    fun proceedFromPassphrase() {
        _step.value = Step.REVEAL
    }

    fun proceedFromGesture() {
        _step.value = Step.PASSPHRASE
    }

    fun back() {
        _step.value = when (_step.value) {
            Step.SETUP -> Step.SETUP
            Step.GESTURE -> Step.SETUP
            Step.PASSPHRASE -> if (_useGesture.value) Step.GESTURE else Step.SETUP
            Step.REVEAL -> Step.PASSPHRASE
        }
    }

    /**
     * Generates the final mnemonic ONCE, on a background coroutine. System
     * entropy is drawn here, at Reveal time — never earlier. The result is
     * exposed via [finalMnemonic] and stays stable across recomposition and
     * back/forward navigation, so a written-down backup stays valid.
     */
    fun generateFinalMnemonic() {
        if (_mode.value != Mode.GENERATE_NEW) return
        if (_finalMnemonic.value != null) return
        if (generateJob?.isActive == true) return
        generateJob = viewModelScope.launch(Dispatchers.Default) {
            val mnemonic = EntropyCombiner.generate(_wordCount.value, gestureDigest)
            if (isActive) {
                _finalMnemonic.value = mnemonic
            } else {
                // The ViewModel was cleared while generation was running; the
                // mnemonic must not be left in a dead StateFlow unwiped.
                mnemonic.wipe()
            }
        }
    }

    fun clearCreateError() {
        _createError.value = null
    }

    /** Way B: validate and persist a user-provided mnemonic. */
    fun createWalletFromMnemonic(mnemonic: CharArray) {
        if (_isCreating.value) return
        _createError.value = null
        _isCreating.value = true
        // Snapshot the passphrase on the calling (Main) thread: the field is
        // wiped/replaced by setPassphrase, and the DB write runs on
        // Dispatchers.Default, so the coroutine must own its own copy.
        val name = walletName.trim()
        val pass = passphrase.copyOf()
        viewModelScope.launch(Dispatchers.Default) {
            val result = try {
                val walletId = createWalletUseCase(name, mnemonic, pass)
                Result.Success(walletId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e.message ?: "Invalid mnemonic")
            } finally {
                mnemonic.wipe()
                pass.wipe()
            }
            when (result) {
                is Result.Success -> {
                    passphrase.wipe()
                    passphrase = CharArray(0)
                    createdWalletChannel.trySend(result.walletId)
                }
                is Result.Error -> _createError.value = result.message
            }
            _isCreating.value = false
        }
    }

    /** Way A: persist the internally generated final mnemonic. */
    fun createWalletFromGenerated() {
        if (_isCreating.value) return
        _createError.value = null
        _isCreating.value = true
        // Snapshot the passphrase on the calling (Main) thread (see
        // createWalletFromMnemonic). The final mnemonic is only wiped on
        // success below, so holding the reference across the DB write is safe.
        val name = walletName.trim()
        val pass = passphrase.copyOf()
        viewModelScope.launch(Dispatchers.Default) {
            val mnemonic = _finalMnemonic.value
            val result = if (mnemonic == null) {
                Result.Error("Mnemonic not generated")
            } else {
                try {
                    val walletId = createWalletUseCase(name, mnemonic, pass)
                    Result.Success(walletId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.Error(e.message ?: "Wallet creation failed")
                }
            }
            pass.wipe()
            when (result) {
                is Result.Success -> {
                    _finalMnemonic.value?.wipe()
                    _finalMnemonic.value = null
                    gestureDigest?.wipe()
                    gestureDigest = null
                    passphrase.wipe()
                    passphrase = CharArray(0)
                    createdWalletChannel.trySend(result.walletId)
                }
                is Result.Error -> _createError.value = result.message
            }
            _isCreating.value = false
        }
    }

    /**
     * One-shot creation events. The flow collects this exactly once per
     * successful creation; the channel is conflated so a late collector still
     * receives the most recent event, and each event is delivered to a single
     * collector (no double navigation on recomposition).
     */
    val createdWalletEvents: ReceiveChannel<Long>
        get() = createdWalletChannel

    private fun invalidateGenerated() {
        // Cancel any in-flight generation so it cannot publish a mnemonic
        // that was invalidated (wrong word count / mode / gesture) after the
        // user changed their mind.
        generateJob?.cancel()
        generateJob = null
        _finalMnemonic.value?.wipe()
        _finalMnemonic.value = null
    }

    override fun onCleared() {
        super.onCleared()
        generateJob?.cancel()
        passphrase.wipe()
        gestureDigest?.wipe()
        _finalMnemonic.value?.wipe()
        _finalMnemonic.value = null
        createdWalletChannel.close()
    }
}
