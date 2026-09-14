package com.ultrabytecoder.kryptakeep.ui.keyboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyboardLayoutType
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.KeyboardTarget
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.LocalKeyboardController
import compose.icons.FeatherIcons
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import kotlinx.coroutines.delay

@Composable
fun SecureOutlinedTextField(
    target: KeyboardTarget,
    label: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    layoutType: KeyboardLayoutType = KeyboardLayoutType.Qwerty,
    supportsDecimal: Boolean = false,
    masked: Boolean = true,
    revealable: Boolean = false,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    singleLine: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
    enabled: Boolean = true
) {
    val controller = LocalKeyboardController.current
    val isActive = controller?.isVisible == true && controller.activeTarget == target

    // H9: wipe this field's buffer when the app is backgrounded (ON_STOP). Bound to
    // the FIELD (not the keyboard), so it fires even when the on-screen keyboard has
    // been dismissed and the controller no longer holds an active target. Per-screen
    // dispose wipes cover navigation; this covers app-level backgrounding. Lifecycle
    // callbacks fire on the main thread, matching the target's thread confinement.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, target) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) target.clear()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val borderWidth = if (isActive) 2.dp else 1.dp

    // Sensitive fields are masked by default. The mask is derived from the
    // character count only (whitespace positions preserved so multi-line
    // mnemonics keep their shape), so no secret character is ever rendered or
    // boxed into an un-wipeable display object beyond the platform's own
    // `target.text`.
    // H13: a sensitive field can optionally reveal its content briefly (to let
    // the user verify an unrecoverable secret); auto-hides after 5s.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(5_000)
            revealed = false
        }
    }
    val showMask = masked && !revealed
    val displayText = remember(target.text, showMask) {
        if (showMask) target.text.map { if (it.isWhitespace()) it else '•' }.joinToString("")
        else target.text
    }

    // Per-Text layouts, used to map a tap on the (split) content to a character
    // index for tap-to-position cursor placement (single-line fields only).
    var beforeLayout: TextLayoutResult? by remember { mutableStateOf<TextLayoutResult?>(null) }
    var afterLayout: TextLayoutResult? by remember { mutableStateOf<TextLayoutResult?>(null) }
    val enabledState = rememberUpdatedState(enabled)
    // singleLine forces a single line (caps maxLines); minLines grows the min
    // height so multi-line fields (mnemonic) reserve room before scrolling.
    val effectiveMaxLines = if (singleLine) 1 else maxLines

    Column(modifier = modifier) {
        label?.let {
            Box(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) { it() }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp + 28.dp * (minLines - 1))
                // Deliberately out of the IME focus chain: input only flows through the
                // on-screen keyboard, so no system input method can observe the secret.
                .focusable(false)
                .semantics { password() }
                .clip(shape)
                .border(BorderStroke(borderWidth, borderColor), shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controller?.let { c ->
                        // Tap toggles: bring up this field's keyboard, or dismiss if it's up.
                        if (c.target == target && c.isVisible) c.hide() else c.show(target, layoutType, supportsDecimal)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (effectiveMaxLines > 1) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (target.text.isEmpty() && placeholder != null) {
                        Box(modifier = Modifier.alpha(0.5f)) {
                            placeholder()
                        }
                    } else {
                        // Split the display text at the cursor and render the caret
                        // between the two runs so the caret sits exactly on the index.
                        // Single-line fields support tap-to-position; multi-line keeps
                        // the caret at the end (insert/delete still occur at the cursor).
                        val singleLineField = effectiveMaxLines == 1
                        val text = displayText
                        val cursor = target.cursorIndex.coerceIn(0, text.length)
                        val before = text.substring(0, cursor)
                        val after = text.substring(cursor)
                        val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = before,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                                maxLines = effectiveMaxLines,
                                onTextLayout = { beforeLayout = it },
                                modifier = if (singleLineField && before.isNotEmpty()) Modifier.pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        if (!enabledState.value) return@detectTapGestures
                                        controller?.show(target, layoutType, supportsDecimal)
                                        val r = beforeLayout
                                        target.setCursor(if (r != null) r.getOffsetForPosition(offset) else before.length)
                                    }
                                } else Modifier
                            )
                            if (isActive) {
                                KeyboardCursor()
                            }
                            Text(
                                text = after,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                                maxLines = effectiveMaxLines,
                                onTextLayout = { afterLayout = it },
                                modifier = if (singleLineField && after.isNotEmpty()) Modifier.pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        if (!enabledState.value) return@detectTapGestures
                                        controller?.show(target, layoutType, supportsDecimal)
                                        val r = afterLayout
                                        target.setCursor(target.cursorIndex + (if (r != null) r.getOffsetForPosition(offset) else 0))
                                    }
                                } else Modifier
                            )
                        }
                    }
                }

                if (trailingIcon != null) {
                    trailingIcon()
                }
                if (revealable) {
                    IconButton(
                        onClick = { revealed = !revealed },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (revealed) FeatherIcons.EyeOff else FeatherIcons.Eye,
                            contentDescription = if (revealed) "Hide" else "Show",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (supportingText != null || isError) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                supportingText?.invoke()
            }
        }
    }
}
