package com.personal.sleepalarm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.diary.MarkdownVisualTransformation
import com.personal.sleepalarm.ui.diary.ThemedMarkdownText
import com.personal.sleepalarm.ui.theme.ThemedAlertDialog
import com.personal.sleepalarm.ui.theme.appAccents

@Composable
internal fun QuickNotesDialog(
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialText) { mutableStateOf(TextFieldValue(initialText)) }
    var preview by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val accents = MaterialTheme.appAccents
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val noteAccent = accents.calm.color
    val noteSurface = lerp(colors.surface, noteAccent, 0.13f)
    val noteButtonColors = ButtonDefaults.textButtonColors(
        contentColor = noteAccent,
        disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.48f)
    )
    val markdownTransformation = remember(colors, accents) {
        MarkdownVisualTransformation(
            headingColor = accents.study.color,
            quoteColor = colors.onSurfaceVariant,
            listColor = accents.other.color,
            codeColor = accents.urgent.color,
            codeBackground = colors.surfaceVariant.copy(alpha = 0.55f),
            boldColor = colors.onSurface,
            italicColor = colors.onSurfaceVariant,
            mathColor = accents.focus.color
        )
    }

    fun saveAndDismiss() {
        onSave(value.text)
        onDismiss()
    }

    ThemedAlertDialog(
        onDismissRequest = ::saveAndDismiss,
        title = {
            Text(
                text = stringResource(R.string.quick_notes_title),
                color = noteAccent
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { preview = false },
                        enabled = preview,
                        colors = noteButtonColors
                    ) {
                        Text(stringResource(R.string.diary_edit))
                    }
                    TextButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            preview = true
                        },
                        enabled = !preview,
                        colors = noteButtonColors
                    ) {
                        Text(stringResource(R.string.diary_preview))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(
                            color = noteSurface,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(16.dp)
                ) {
                    if (preview) {
                        if (value.text.isBlank()) {
                            Text(
                                text = stringResource(R.string.quick_notes_empty),
                                color = colors.onSurfaceVariant
                            )
                        } else {
                            ThemedMarkdownText(
                                markdown = value.text,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    } else {
                        BasicTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.onSurface
                            ),
                            cursorBrush = SolidColor(accents.calm.color),
                            visualTransformation = markdownTransformation,
                            decorationBox = { field ->
                                if (value.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.quick_notes_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.62f)
                                    )
                                }
                                field()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::saveAndDismiss, colors = noteButtonColors) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
