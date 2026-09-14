package com.ultrabytecoder.kryptakeep.ui.keyboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ultrabytecoder.kryptakeep.ui.keyboard.model.KeyboardLayoutType
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.KeyboardTarget
import com.ultrabytecoder.kryptakeep.ui.keyboard.state.LocalKeyboardController

@Composable
fun SecureOutlinedTextField(
    target: KeyboardTarget,
    label: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    layoutType: KeyboardLayoutType = KeyboardLayoutType.Qwerty,
    masked: Boolean = true,
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
    val displayText = remember(target.text, masked) {
        if (masked) target.text.map { if (it.isWhitespace()) it else '•' }.joinToString("")
        else target.text
    }

    Column(modifier = modifier) {
        label?.let {
            Box(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) { it() }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .focusable(false)
                .clip(shape)
                .border(BorderStroke(borderWidth, borderColor), shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controller?.show(target, layoutType)
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
                        .then(if (maxLines > 1) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (target.text.isEmpty() && placeholder != null) {
                        Box(modifier = Modifier.alpha(0.5f)) {
                            placeholder()
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                maxLines = maxLines
                            )
                            if (isActive) {
                                KeyboardCursor()
                            }
                        }
                    }
                }

                if (trailingIcon != null) {
                    trailingIcon()
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
