# KryptaKeep — Custom In-App Keyboard Implementation Plan

> Goal: A custom in-app keyboard is the **only** enabled keyboard in the app. The system/OS soft keyboard is completely disabled to prevent malware keyloggers, IME-based interception, and side-channel attacks on sensitive inputs (PINs, passwords, mnemonics).

---

## 1. Architecture Design

### 1.1 New Package Structure (`ui/keyboard/`)

```
ui/keyboard/
├── model/
│   ├── KeyboardLayoutType.kt    # Enum: Numeric, QwertyLower, QwertyUpper, Symbols, Hex, Mnemonic
│   ├── KeyCode.kt               # Sealed class / enum for each key
│   └── KeyboardState.kt         # Data class: activeLayout, isShifted, activeTargetId
├── state/
│   ├── KeyboardController.kt    # Central state holder + event dispatcher
│   └── KeyboardTarget.kt        # Interface: insert(), delete(), clear(), getText()
├── components/
│   ├── AppKeyboard.kt           # Top-level composable — the visible keyboard panel
│   ├── KeyboardKey.kt           # Single key button (reusable)
│   ├── KeyboardRow.kt           # Horizontal row of keys
│   ├── SecureOutlinedTextField.kt  # Read-only field wrapper with custom cursor
│   └── KeyboardCursor.kt        # Blinking caret animation
├── layouts/
│   ├── NumericNumpadLayout.kt   # 0-9 + backspace (existing Numpad, refactored)
│   ├── QwertyLayout.kt          # a-z, shift, ?123, space, backspace, action
│   ├── SymbolLayout.kt          # 0-9, punctuation, special chars
│   └── MnemonicLayout.kt        # QWERTY + BIP-39 word suggestion bar
└── platform/
    ├── SystemKeyboardBlocker.kt # expect/actual — per-platform IME suppression
    ├── AndroidSystemKeyboardBlocker.kt
    ├── IosSystemKeyboardBlocker.kt
    └── DesktopSystemKeyboardBlocker.kt
```

### 1.2 Core Abstractions

#### `KeyboardController`

Central state holder managed via a composition local (`LocalKeyboardController`). Tracks:
- Whether the keyboard is visible
- Which field is currently the active target
- Current layout type + shift/symbol state
- Dispatches input events (insert char, delete, clear, submit) to the active target

```kotlin
class KeyboardController {
    var isVisible: Boolean by mutableStateOf(false)
    var activeTarget: KeyboardTarget? by mutableStateOf(null)
    var layoutType: KeyboardLayoutType by mutableStateOf(KeyboardLayoutType.Qwerty)
    var isShifted: Boolean by mutableStateOf(false)
    var isSymbolsActive: Boolean by mutableStateOf(false)

    fun show(target: KeyboardTarget, layout: KeyboardLayoutType) { ... }
    fun hide() { ... }
    fun onKey(key: KeyCode) { ... }  // routes to activeTarget
    fun insertChar(c: Char) { activeTarget?.insert(c) }
    fun deleteChar() { activeTarget?.delete() }
}
```

#### `KeyboardTarget`

Interface implemented by text holders that can accept keyboard input:

```kotlin
interface KeyboardTarget {
    val id: String
    val text: String
    fun insert(char: Char)
    fun delete()
    fun clear()
    fun isFull(): Boolean  // for fixed-length fields like PIN
    val maxLines: Int
    val isSingleLine: Boolean
}
```

`SecureTextFieldState` gets a thin adapter implementing this interface. Plain `String` state fields get a simple `MutableStateTarget` wrapper.

#### `KeyboardLayoutType`

```kotlin
enum class KeyboardLayoutType {
    Numeric,      // 0-9 + backspace
    Qwerty,       // full letters + shift + symbols + space + backspace + action
    Symbols,      // numbers + punctuation + special chars
    Mnemonic,     // QWERTY + word suggestion accessory bar
    Hex,          // 0-9 a-f + backspace (for address/fee entry)
}
```

### 1.3 Integration with `SecureTextFieldState`

The `KeyboardController` dispatches character additions and deletions directly to the active `SecureTextFieldState` instance. Keypress handlers mutate the underlying `CharArray` buffer — no `InputConnection`, no OS text service involvement.

```kotlin
// Adapter wrapping SecureTextFieldState as a KeyboardTarget
class SecureTargetAdapter(
    private val state: SecureTextFieldState,
    override val id: String,
    private val onChange: (String) -> Unit
) : KeyboardTarget {
    override val text: String get() = state.text
    override fun insert(char: Char) {
        val newChars = (state.toCharArray() + char)
        state.update(newChars)
        onChange(state.text)
    }
    override fun delete() {
        val chars = state.toCharArray()
        if (chars.isNotEmpty()) {
            state.update(chars.copyOfRange(0, chars.size - 1))
            onChange(state.text)
        }
    }
    // ...
}
```

### 1.4 Integration with Existing `Numpad`

The existing `Numpad` component is **refactored and moved** into `ui/keyboard/layouts/NumericNumpadLayout.kt`. It retains its visual style (Material 3 cards, rounded shapes) while adapting its click callbacks to interface with the unified `KeyboardController`.

### 1.5 Focus Model

- Custom text field wrappers are **`readOnly = true`** — they never request IME focus.
- Tapping a field calls `keyboardController.show(target, layout)`, which:
  1. Sets the active target
  2. Makes the keyboard visible (animated slide-up / fade-in)
  3. Shows a blinking cursor in the field
- Tapping outside any field (or pressing the action key) calls `keyboardController.hide()`.

---

## 2. Keyboard Layouts

### 2.1 PIN / Numpad Layout (existing, refactored)

| Key | Action |
|-----|--------|
| 0–9 | Insert digit |
| ⌫ | Delete last digit |

- **Screens:** `PinScreenSetup`, `PinScreenEnter`, `ChangePinScreen` (PIN mode)
- **Enhancement:** Optional shuffle mode — randomize key positions per session to defeat shoulder-surfing.

### 2.2 Full QWERTY Layout

**Lower layer:**
```
Q W E R T Y U I O P
 A S D F G H J K L
 ⇧ Z X C V B N M ⌫
 ?123    [space]    [action]
```

**Upper layer (shift held or caps locked):**
```
Q W E R T Y U I O P
 A S D F G H J K L
 ⇧ Z X C V B N M ⌫
 ?123    [space]    [action]
```

**Symbol layer (`?123`):**
```
1 2 3 4 5 6 7 8 9 0
- = ( ) [ ] { } @ #
 $ ^ & * _ + % ~ ` ⌫
 ?ABC   [space]    [action]
```

- **Screens:** `SetPasswordScreen`, `PassphraseScreen`, `ChangePinScreen` (password mode)

### 2.3 Mnemonic Layout

QWERTY + **accessory suggestion bar** above the keyboard:
- Shows top 3 BIP-39 word suggestions matching current partial word
- Tapping a suggestion inserts the full word + trailing space
- Enforces single-space between words
- **Screens:** `CreateWalletSetupScreen` (mnemonic field)

### 2.4 Hex / URI Accessory Layout

QWERTY + **quick-access symbol bar** with blockchain-specific delimiters:
- `0x` `://` `/` `-` `.` `:`
- **Screens:** `SendScreen` (address, fees), `CustomNodesScreen` (URLs)

### 2.5 Layout → Screen Mapping

| Screen | Layout |
|--------|--------|
| `PinScreenSetup` | Numeric |
| `PinScreenEnter` | Numeric |
| `ChangePinScreen` (PIN) | Numeric |
| `ChangePinScreen` (password) | Qwerty |
| `SetPasswordScreen` | Qwerty |
| `PassphraseScreen` | Qwerty |
| `CreateWalletSetupScreen` (wallet name) | Qwerty |
| `CreateWalletSetupScreen` (mnemonic) | Mnemonic |
| `CreateWalletSetupScreen` (passphrase) | Qwerty |
| `SendScreen` (address) | Qwerty + Hex accessory |
| `SendScreen` (amount) | Numeric (decimal) |
| `SendScreen` (fees) | Numeric (decimal) |
| `CustomNodesScreen` | Qwerty + URI accessory |
| `CreateAccountScreen` (search) | Qwerty |
| `CreateAccountScreen` (derivation path) | Qwerty |
| `ManageWalletsScreen` (rename) | Qwerty |

---

## 3. Platform-Specific Implementation

### 3.1 Android

**Step 1 — Manifest:**
```xml
<activity
    android:name=".MainActivity"
    android:windowSoftInputMode="stateAlwaysHidden|adjustResize" />
```

**Step 2 — `MainActivity.kt`:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    window.applySecureFlag()

    // Block IME insets — system keyboard can never appear
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.ime())
    }

    setContent { App() }
}

// Belt-and-suspenders: intercept any attempt to show the IME
override fun onRequestPermissionsResult(...) { ... }
```

**Step 3 — All custom text fields use `readOnly = true`** so Compose never creates an `InputConnection` with the IME. The custom keyboard is the sole input path.

**Step 4 — `SystemKeyboardBlocker` (androidMain actual):**
```kotlin
actual fun blockSystemKeyboard(activity: Activity) {
    activity.window.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    )
    WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        .hide(WindowInsetsCompat.Type.ime())
}
```

### 3.2 iOS

**`SystemKeyboardBlocker` (iosMain actual):**
```kotlin
actual fun blockSystemKeyboard(viewController: UIViewController) {
    // Compose Multiplatform on iOS uses a SkikoUIView.
    // Ensure no child view can become first responder:
    // 1. All text fields are readOnly (no native UITextField/UITextView created)
    // 2. If any interop views exist, override canBecomeFirstResponder = false
    // 3. Dismiss any existing keyboard:
    viewController.view?.endEditing(true)
}
```

Since all Compose text fields will be `readOnly = true`, no native text responder is ever created, and the iOS keyboard never appears.

### 3.3 Desktop (JVM)

Desktop has no system soft keyboard to disable. Two strategies:

**Option A (Recommended): Strict mode for sensitive screens only**
- For `SetPassword`, `Passphrase`, `CreateWallet` (mnemonic), `EnterPin`: show the custom keyboard and **block hardware key events** at the window level.
- For non-sensitive fields (wallet name, search, node URLs): allow normal physical keyboard input (better UX).

**Option B: Always custom keyboard**
- Show the custom keyboard for all text inputs, block all hardware key events.
- More secure but worse UX for non-sensitive fields.

```kotlin
// Desktop actual — block hardware keystrokes in strict mode
actual fun blockSystemKeyboard(window: Window, strictMode: Boolean) {
    if (strictMode) {
        window.isFocusable = true
        // Add a key listener that consumes all KeyEvent presses
        // and routes them to the custom keyboard controller instead
    }
}
```

---

## 4. Component API Design

### 4.1 Public API

```kotlin
/**
 * The app-level custom keyboard panel. Place at the bottom of the screen
 * layout. It shows/hides based on KeyboardController state.
 */
@Composable
fun AppKeyboard(
    modifier: Modifier = Modifier,
    actionLabel: String = "Done",
    onAction: (() -> Unit)? = null
) {
    val controller = localKeyboardController()
    // AnimatedVisibility based on controller.isVisible
    // Renders the appropriate layout based on controller.layoutType
}

/**
 * A read-only text field that works with the custom keyboard.
 * Tapping it activates the keyboard and sets this field as the target.
 */
@Composable
fun SecureOutlinedTextField(
    target: KeyboardTarget,
    label: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    enabled: Boolean = true
)

/**
 * Creates a KeyboardController and provides it via LocalKeyboardController.
 * Call this at the App() level or per-screen.
 */
@Composable
fun rememberKeyboardController(): KeyboardController
```

### 4.2 Screen Integration Examples

**`SetPasswordScreen` (sensitive — full custom keyboard):**
```kotlin
@Composable
fun SetPasswordScreen(viewModel: SetPasswordViewModel) {
    val controller = rememberKeyboardController()
    val passwordState = remember { SecureTextFieldState() }
    val target = remember(passwordState) {
        SecureTargetAdapter(passwordState, id = "password") { viewModel.onInput(it) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // ... title, subtitle ...

        SecureOutlinedTextField(
            target = target,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        Button(onClick = { viewModel.onContinue() }, Modifier.fillMaxWidth()) {
            Text("Continue")
        }

        AppKeyboard(actionLabel = "Continue", onAction = { viewModel.onContinue() })
    }
}
```

**`SendScreen` (address field — custom keyboard with hex accessory):**
```kotlin
val addressTarget = remember {
    MutableStateTarget(id = "address") { address = it }
}

SecureOutlinedTextField(
    target = addressTarget,
    label = { Text("Recipient Address") },
    modifier = Modifier.weight(1f)
)

// At the bottom:
AppKeyboard(
    actionLabel = "Send",
    onAction = { /* trigger send */ }
)
```

**`PinScreenEnter` (numeric numpad — replaces existing Numpad):**
```kotlin
// The existing PinDotsInline + Numpad pattern is preserved,
// but the Numpad is now NumericNumpadLayout driven by KeyboardController:
NumericNumpadLayout(
    onDigit = { controller.insertChar(it.toString()[0]) },
    onDelete = { controller.deleteChar() },
    isLocked = state.isLocked || state.isProcessing
)
```

### 4.3 Transition Plan

| Phase | Screens to Convert | Notes |
|-------|-------------------|-------|
| **1 (MVP)** | `PinScreenSetup`, `PinScreenEnter`, `CreateWalletSetupScreen` (mnemonic) | Core controller + numeric layout + SecureOutlinedTextField + IME suppression |
| **2** | `SetPasswordScreen`, `PassphraseScreen`, `ChangePinScreen` | Full QWERTY + shift + symbols + password visual transformation |
| **3** | `SendScreen`, `CustomNodesScreen`, `CreateAccountScreen`, `ManageWalletsScreen` | Hex/URI accessories, auto-scroll, desktop strict mode |

---

## 5. Security Hardening

### 5.1 Memory Management
- All sensitive input goes through `SecureTextFieldState` (wipe-able `CharArray`).
- Keyboard internal buffers are `CharArray`, not `String`.
- `.wipe()` called on: screen dispose, session lock, after cryptographic operations.
- No keystroke logging to Logcat/NSLog/stdout.

### 5.2 Input Sanitization
- Strip control characters (except intended ones) before insertion.
- Reject zero-width spaces, zero-width joiners, and other Unicode injection vectors.
- Enforce character-class whitelists per field type (e.g. mnemonic = BIP-39 words only, address = hex + base58 + bech32 chars).

### 5.3 Clipboard Protection
- For sensitive fields (mnemonic, passphrase, password): **disable paste** or route paste through a validated path that:
  - Trims whitespace
  - Validates against expected character set
  - Wipes clipboard after read (Android: `ClipboardManager` clear; iOS: not possible, but at least don't cache)
- For address fields: allow paste (addresses are not secret).

### 5.4 Anti-Shoulder-Surfing
- **Numpad shuffle:** Randomize the 0-9 key positions on each app launch (seed from CSPRNG). Store the mapping in memory only.
- **Randomized delay:** Add 50-150ms random delay between key visual feedback and actual insertion to defeat timing-based observation.

### 5.5 No IME Involvement
- Since all fields are `readOnly = true` and the custom keyboard is a pure Compose UI (buttons → state mutation), **no text ever passes through the OS input method**. This is the core security guarantee.

---

## 6. Implementation Phases

### Phase 1: MVP (Core Infrastructure + Numeric + Mnemonic)

**Deliverables:**
1. `KeyboardController` + `KeyboardTarget` interface + `LocalKeyboardController`
2. `SecureOutlinedTextField` composable (read-only, custom cursor, tap-to-activate)
3. `NumericNumpadLayout` (refactored from existing `Numpad`)
4. `MnemonicLayout` (basic QWERTY without suggestions — full QWERTY in Phase 2)
5. `SystemKeyboardBlocker` expect/actual (Android: manifest + insets; iOS: readOnly; Desktop: no-op)
6. Convert: `PinScreenSetup`, `PinScreenEnter`, `CreateWalletSetupScreen` (mnemonic field)

**Estimated effort:** 3-5 days

### Phase 2: Full QWERTY + Password Screens

**Deliverables:**
1. `QwertyLayout` with shift/caps state machine
2. `SymbolLayout` (numbers + punctuation + special chars)
3. Layout switching animation (QWERTY ↔ Symbols)
4. Mnemonic word suggestion bar (BIP-39 dictionary)
5. Convert: `SetPasswordScreen`, `PassphraseScreen`, `ChangePinScreen` (both modes)
6. `ExportMnemonicScreen` re-authentication uses custom keyboard

**Estimated effort:** 4-6 days

### Phase 3: Operational Fields + Polish

**Deliverables:**
1. Hex/URI accessory bar (for addresses, URLs, fees)
2. Auto-scrolling caret for long single-line fields
3. Desktop strict mode (block hardware keys on sensitive screens)
4. Numpad shuffle (anti-shoulder-surfing)
5. Accessibility: semantics on all keys, TalkBack/VoiceOver labels
6. Convert: `SendScreen`, `CustomNodesScreen`, `CreateAccountScreen`, `ManageWalletsScreen`
7. Performance optimization (stable composables, targeted recomposition)

**Estimated effort:** 3-5 days

---

## 7. Testing Strategy

### 7.1 Unit Tests (commonTest)
- `KeyboardController`: state transitions (show/hide, layout switch, shift toggle)
- `SecureTargetAdapter`: insert/delete/clear on `SecureTextFieldState`
- `MnemonicLayout`: word suggestion matching, single-space enforcement
- Input sanitization: control char stripping, Unicode injection rejection

### 7.2 Compose UI Tests (desktopTest)
- Simulate tap on virtual keys → verify `SecureTextFieldState` updates
- Verify keyboard shows/hides on field tap / action key
- Test PIN max-length enforcement (stop at 6/8 digits)
- Test shift state: a→A→a cycle
- Test symbol layer toggle

### 7.3 Manual / Integration Testing
- **Android:** Verify system keyboard never appears (test with hardware keyboard connected, test with IME enabled)
- **iOS:** Verify no keyboard slides up on any field tap
- **Desktop:** Verify hardware keys are blocked in strict mode
- **Edge cases:**
  - Empty input + backspace (no crash)
  - Rapid successive taps (no UI jank)
  - Very long input (address 66 chars) — auto-scroll works
  - App backgrounded mid-typing — session lock wipes buffers
  - Rotation (Android) — keyboard state preserved

### 7.4 Security Testing
- Verify no sensitive strings in logcat / system logs
- Verify `SecureTextFieldState.wipe()` zeros memory after use
- Verify clipboard is cleared after sensitive paste
- Verify `FLAG_SECURE` still active (no screenshots)

---

## 8. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Performance:** Full keyboard recompose on every keypress | Frame drops, janky typing | Use stable key composables; only the text display + active key recompose. Use `derivedStateOf` for derived values. Profile with Compose compiler metrics. |
| **Accessibility:** Screen readers can't use custom buttons | Excludes visually impaired users | Add `semantics { contentDescription = "Key A" }` to every key. Support TalkBack/VoiceOver navigation through the keyboard. |
| **UX friction:** On-screen typing is slower than physical keyboard | User frustration, especially for long inputs | Minimize: use custom keyboard only where security demands it (Phase 1-2). For non-sensitive fields on desktop, allow physical keyboard. Large touch targets (48dp+). |
| **iOS edge cases:** Compose Multiplatform may create hidden text responders | System keyboard appears unexpectedly | Test extensively on real devices. Fallback: `endEditing(true)` on any detected keyboard appearance. |
| **Compose Multiplatform API gaps:** `readOnly` + `focusable` behavior may differ across platforms | Inconsistent behavior | Abstract behind `SecureOutlinedTextField`; test on all 3 platforms. File upstream issues if needed. |
| **Desktop key event blocking:** Window focus issues | App becomes unresponsive to mouse | Only block `KeyEvent`, not mouse events. Test on all 3 OSes (Linux, macOS, Windows). |
| **Backward compatibility:** Existing `Numpad` usages break | Compile errors in multiple screens | Keep `Numpad` as a thin wrapper around `NumericNumpadLayout` during transition. Deprecate, don't remove, until all screens are converted. |

---

## Appendix: Sensitive vs Non-Sensitive Input Classification

| Input | Sensitive? | Rationale |
|-------|-----------|-----------|
| PIN (digits) | **Yes** | Directly unlocks wallet |
| Password | **Yes** | Directly unlocks wallet |
| BIP-39 Mnemonic | **Yes** | Master seed — total fund access |
| BIP-39 Passphrase | **Yes** | Additional encryption layer |
| Recipient Address | No | Public ledger info |
| Amount / Fees | No | Public transaction values |
| Wallet Name | No | Local metadata |
| Search Query | No | Local filter |
| Derivation Path | No | Public standard path |
| Custom Node URL | No | Public endpoint |

> **Design decision:** In Phase 1-2, use custom keyboard for ALL sensitive inputs. In Phase 3, extend to non-sensitive inputs on mobile (for consistency + IME suppression), but allow physical keyboard on desktop for non-sensitive fields.
