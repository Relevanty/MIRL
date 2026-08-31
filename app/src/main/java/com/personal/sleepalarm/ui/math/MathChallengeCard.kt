package com.personal.sleepalarm.ui.math

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.model.MathAnswerParser
import com.personal.sleepalarm.domain.model.MathAnswerSpec
import com.personal.sleepalarm.domain.model.MathChallenge
import com.personal.sleepalarm.domain.model.MathChallengeKind
import com.personal.sleepalarm.ui.alarm.ChallengeVisualRenderer
import com.personal.sleepalarm.ui.theme.AppAccentTone

/** Shared challenge body used by both the wake-up alarm and free maths practice. */
@Composable
fun MathChallengeCard(
    challenge: MathChallenge,
    userInput: String,
    errorMessage: String?,
    showHint: Boolean,
    answerAccepted: Boolean,
    enabled: Boolean,
    onInputChanged: (String) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val studyTone = MaterialTheme.appAccents.study
    val infoTone = MaterialTheme.appAccents.info
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = studyTone.action,
            contentColor = studyTone.onAction,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = stringResource(challenge.kind.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = challenge.question,
            style = when {
                challenge.question.length > 100 -> MaterialTheme.typography.bodyLarge
                challenge.question.length > 60 -> MaterialTheme.typography.titleLarge
                challenge.question.length > 36 -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.headlineLarge
            },
            color = studyTone.color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        challenge.visual?.let { visual ->
            Spacer(modifier = Modifier.height(12.dp))
            ChallengeVisualRenderer(
                visual = visual,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = userInput,
            onValueChange = onInputChanged,
            label = { Text(text = stringResource(R.string.alarm_answer_label)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = challenge.answerSpec.keyboardType()
            ),
            singleLine = challenge.answerSpec is MathAnswerSpec.Integer,
            maxLines = 3,
            enabled = enabled && !answerAccepted,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = studyTone.action,
                unfocusedContainerColor = studyTone.action,
                disabledContainerColor = studyTone.action.copy(alpha = 0.62f),
                focusedTextColor = studyTone.onAction,
                unfocusedTextColor = studyTone.onAction,
                disabledTextColor = studyTone.onAction.copy(alpha = 0.68f),
                focusedLabelColor = studyTone.onAction,
                unfocusedLabelColor = studyTone.onAction.copy(alpha = 0.74f),
                disabledLabelColor = studyTone.onAction.copy(alpha = 0.58f),
                cursorColor = studyTone.color,
                focusedIndicatorColor = studyTone.color,
                unfocusedIndicatorColor = studyTone.onAction.copy(alpha = 0.38f),
                disabledIndicatorColor = studyTone.onAction.copy(alpha = 0.24f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(challenge.answerSpec.formatHintRes()),
            color = infoTone.color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )

        if (challenge.answerSpec is MathAnswerSpec.IntervalSet && enabled && !answerAccepted) {
            IntervalAnswerTokens(studyTone) { token -> onInputChanged(userInput + token) }
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            StatusMessage(
                text = error,
                tone = MaterialTheme.appAccents.urgent
            )
        }

        if (showHint) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusMessage(
                text = stringResource(
                    R.string.alarm_hint_answer,
                    MathAnswerParser.canonical(challenge.answerSpec)
                ),
                tone = MaterialTheme.appAccents.warning
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!answerAccepted) {
            Button(
                onClick = onCheck,
                enabled = enabled && userInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = studyTone.action,
                    contentColor = studyTone.onAction,
                    disabledContainerColor = studyTone.action.copy(alpha = 0.46f),
                    disabledContentColor = studyTone.onAction.copy(alpha = 0.54f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(text = stringResource(R.string.alarm_action_check))
            }
        } else {
            StatusMessage(
                text = stringResource(R.string.alarm_answer_correct),
                tone = MaterialTheme.appAccents.success
            )
        }
    }
}

@StringRes
private fun MathChallengeKind.labelRes(): Int = when (this) {
    MathChallengeKind.ARITHMETIC -> R.string.alarm_kind_arithmetic
    MathChallengeKind.LINEAR -> R.string.alarm_kind_linear
    MathChallengeKind.FRACTION -> R.string.alarm_kind_fraction
    MathChallengeKind.PROPORTION -> R.string.alarm_kind_proportion
    MathChallengeKind.SYSTEM -> R.string.alarm_kind_system
    MathChallengeKind.ABSOLUTE -> R.string.alarm_kind_absolute
    MathChallengeKind.QUADRATIC -> R.string.alarm_kind_quadratic
    MathChallengeKind.RADICAL -> R.string.alarm_kind_radical
    MathChallengeKind.POWER -> R.string.alarm_kind_power
    MathChallengeKind.LOGARITHM -> R.string.alarm_kind_logarithm
    MathChallengeKind.RATIONAL -> R.string.alarm_kind_rational
    MathChallengeKind.TRIGONOMETRY -> R.string.alarm_kind_trigonometry
    MathChallengeKind.FACTORIAL -> R.string.alarm_kind_factorial
    MathChallengeKind.BIQUADRATIC -> R.string.alarm_kind_biquadratic
    MathChallengeKind.EXPONENTIAL -> R.string.alarm_kind_exponential
    MathChallengeKind.POLYNOMIAL -> R.string.alarm_kind_polynomial
    MathChallengeKind.INEQUALITY -> R.string.alarm_kind_inequality
    MathChallengeKind.PARAMETER -> R.string.alarm_kind_parameter
    MathChallengeKind.NUMBER_SET -> R.string.alarm_kind_number_set
    MathChallengeKind.FUNCTION -> R.string.alarm_kind_function
    MathChallengeKind.COORDINATE -> R.string.alarm_kind_coordinate
    MathChallengeKind.GEOMETRY -> R.string.alarm_kind_geometry
    MathChallengeKind.NUMBER_THEORY -> R.string.alarm_kind_number_theory
    MathChallengeKind.DIVISORS -> R.string.alarm_kind_divisors
    MathChallengeKind.COMBINATORICS -> R.string.alarm_kind_combinatorics
    MathChallengeKind.SEQUENCE -> R.string.alarm_kind_sequence
    MathChallengeKind.ANALYSIS -> R.string.alarm_kind_analysis
    MathChallengeKind.DIGIT -> R.string.alarm_kind_digit
}

@StringRes
private fun MathAnswerSpec.formatHintRes(): Int = when (this) {
    is MathAnswerSpec.Integer -> R.string.alarm_answer_format_integer
    is MathAnswerSpec.IntegerSet -> R.string.alarm_answer_format_set
    is MathAnswerSpec.IntervalSet -> R.string.alarm_answer_format_intervals
    is MathAnswerSpec.OrderedPair -> R.string.alarm_answer_format_pair
}

private fun MathAnswerSpec.keyboardType(): KeyboardType = when (this) {
    is MathAnswerSpec.Integer -> if (expected >= 0) KeyboardType.Number else KeyboardType.Text
    is MathAnswerSpec.IntegerSet,
    is MathAnswerSpec.IntervalSet,
    is MathAnswerSpec.OrderedPair -> KeyboardType.Text
}

@Composable
private fun IntervalAnswerTokens(tone: AppAccentTone, onToken: (String) -> Unit) {
    val tokens = remember { listOf("[", "]", "(", ")", "; ", " ∪ ", "-∞", "+∞") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tokens.forEach { token ->
            AssistChip(
                onClick = { onToken(token) },
                label = { Text(token.trim()) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = tone.action,
                    labelColor = tone.onAction
                )
            )
        }
    }
}

@Composable
private fun StatusMessage(text: String, tone: AppAccentTone) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tone.container,
        contentColor = tone.onContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = tone.onContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}
