package com.personal.sleepalarm.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.theme.appAccents
import java.util.Locale
import kotlin.math.abs

@Composable
fun EnergyStatsContent(
    analytics: EnergyAnalytics,
    modifier: Modifier = Modifier
) {
    val energyTone = MaterialTheme.appAccents.energy
    val infoTone = MaterialTheme.appAccents.info
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = energyTone.container,
            contentColor = energyTone.onContainer
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.energy_stats_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.energy_stats_period),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        R.string.energy_stats_sample_summary,
                        analytics.energySampleCount,
                        analytics.episodeSampleCount
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (!analytics.hasAnyData) {
            InfoCard(
                title = stringResource(R.string.energy_stats_empty_title),
                body = stringResource(R.string.energy_stats_empty_body)
            )
            return@Column
        }

        if (!analytics.hasEnoughEnergyData || !analytics.hasEnoughEpisodeData) {
            InfoCard(
                title = stringResource(R.string.energy_stats_low_data_title),
                body = stringResource(
                    R.string.energy_stats_low_data_body,
                    EnergyAnalytics.MIN_ENERGY_SAMPLES,
                    EnergyAnalytics.MIN_EPISODE_SAMPLES
                )
            )
        }

        if (analytics.averageByTimeOfDay.isNotEmpty()) {
            EnergyChartCard(
                title = stringResource(R.string.energy_stats_time_title),
                subtitle = stringResource(R.string.energy_stats_time_subtitle)
            ) {
                analytics.averageByTimeOfDay.forEach { bucket ->
                    AverageEnergyRow(
                        label = timeOfDayLabel(bucket.key),
                        bucket = bucket
                    )
                }
            }
        }

        if (analytics.deltaByWorkMode.isNotEmpty()) {
            EnergyChartCard(
                title = stringResource(R.string.energy_stats_work_mode_title),
                subtitle = stringResource(R.string.energy_stats_delta_subtitle)
            ) {
                analytics.deltaByWorkMode.forEach { bucket ->
                    DeltaRow(workModeLabel(bucket.key), bucket)
                }
            }
        }

        if (analytics.deltaByDomain.isNotEmpty()) {
            EnergyChartCard(
                title = stringResource(R.string.energy_stats_domain_title),
                subtitle = stringResource(R.string.energy_stats_delta_subtitle)
            ) {
                analytics.deltaByDomain.forEach { bucket ->
                    DeltaRow(domainLabel(bucket.key), bucket)
                }
            }
        }

        if (analytics.averageByHoursAwake.isNotEmpty()) {
            EnergyChartCard(
                title = stringResource(R.string.energy_stats_awake_title),
                subtitle = stringResource(R.string.energy_stats_awake_subtitle)
            ) {
                analytics.averageByHoursAwake.forEach { bucket ->
                    AverageEnergyRow(
                        label = hoursAwakeLabel(bucket.key),
                        bucket = bucket
                    )
                }
            }
        } else {
            InfoCard(
                title = stringResource(R.string.energy_stats_awake_unavailable_title),
                body = stringResource(R.string.energy_stats_awake_unavailable_body)
            )
        }

        Text(
            text = stringResource(R.string.energy_stats_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = infoTone.color,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun EnergyChartCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val tone = MaterialTheme.appAccents.energy
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = tone.container,
        contentColor = tone.onContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tone.onContainer.copy(alpha = 0.72f)
                )
            }
            content()
        }
    }
}

@Composable
private fun AverageEnergyRow(label: String, bucket: EnergyAverageBucket) {
    val tone = MaterialTheme.appAccents.energy
    val accent = tone.color
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    R.string.energy_stats_average_value,
                    formatOneDecimal(bucket.average),
                    bucket.sampleCount
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tone.onContainer.copy(alpha = 0.14f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((bucket.average / 10f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
        }
        if (bucket.sampleCount < EnergyAnalytics.MIN_BUCKET_SAMPLES) {
            LowSampleLabel()
        }
    }
}

@Composable
private fun DeltaRow(label: String, bucket: EnergyDeltaBucket) {
    val energyTone = MaterialTheme.appAccents.energy
    val deltaColor = when {
        bucket.averageDelta > 0.15f -> MaterialTheme.appAccents.success.color
        bucket.averageDelta < -0.15f -> MaterialTheme.appAccents.urgent.color
        else -> energyTone.onContainer.copy(alpha = 0.72f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.energy_stats_samples_short, bucket.sampleCount),
                style = MaterialTheme.typography.labelSmall,
                color = energyTone.onContainer.copy(alpha = 0.72f)
            )
            if (bucket.sampleCount < EnergyAnalytics.MIN_BUCKET_SAMPLES) LowSampleLabel()
        }
        Text(
            text = formatSigned(bucket.averageDelta),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = deltaColor
        )
    }
}

@Composable
private fun LowSampleLabel() {
    val tone = MaterialTheme.appAccents.energy
    Text(
        text = stringResource(R.string.energy_stats_few_samples),
        style = MaterialTheme.typography.labelSmall,
        color = tone.onContainer.copy(alpha = 0.64f)
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    val tone = MaterialTheme.appAccents.info
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tone.container,
        contentColor = tone.onContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun timeOfDayLabel(key: String): String = stringResource(
    when (key) {
        "MORNING" -> R.string.energy_stats_time_morning
        "DAY" -> R.string.energy_stats_time_day
        "EVENING" -> R.string.energy_stats_time_evening
        else -> R.string.energy_stats_time_night
    }
)

@Composable
private fun hoursAwakeLabel(key: String): String = stringResource(
    when (key) {
        "H0_3" -> R.string.energy_stats_awake_0_3
        "H3_6" -> R.string.energy_stats_awake_3_6
        "H6_9" -> R.string.energy_stats_awake_6_9
        "H9_12" -> R.string.energy_stats_awake_9_12
        "H12_16" -> R.string.energy_stats_awake_12_16
        else -> R.string.energy_stats_awake_16_plus
    }
)

@Composable
private fun workModeLabel(key: String): String = when (key.uppercase(Locale.ROOT)) {
    "DEEP", "DEEP_WORK" -> stringResource(R.string.energy_stats_mode_deep)
    "ADMIN" -> stringResource(R.string.energy_stats_mode_admin)
    "COMMUNICATION" -> stringResource(R.string.energy_stats_mode_communication)
    "LEARNING", "STUDY" -> stringResource(R.string.energy_stats_mode_learning)
    "CREATIVE" -> stringResource(R.string.energy_stats_mode_creative)
    "PHYSICAL" -> stringResource(R.string.energy_stats_mode_physical)
    "ROUTINE" -> stringResource(R.string.energy_stats_mode_routine)
    "RECOVERY" -> stringResource(R.string.energy_stats_mode_recovery)
    "WORK" -> stringResource(R.string.energy_stats_domain_work)
    else -> stringResource(R.string.energy_stats_mode_other)
}

@Composable
private fun domainLabel(key: String): String = when (key.uppercase(Locale.ROOT)) {
    "WORK" -> stringResource(R.string.energy_stats_domain_work)
    "STUDY", "LEARNING" -> stringResource(R.string.energy_stats_domain_study)
    "HEALTH", "PHYSICAL" -> stringResource(R.string.energy_stats_domain_health)
    "HOME" -> stringResource(R.string.energy_stats_domain_home)
    "SOCIAL", "COMMUNICATION" -> stringResource(R.string.energy_stats_domain_social)
    "CREATIVE" -> stringResource(R.string.energy_stats_mode_creative)
    else -> stringResource(R.string.energy_stats_mode_other)
}

private fun formatOneDecimal(value: Float): String =
    String.format(Locale.getDefault(), "%.1f", value)

private fun formatSigned(value: Float): String {
    val sign = when {
        value > 0.05f -> "+"
        value < -0.05f -> "−"
        else -> ""
    }
    return "$sign${formatOneDecimal(abs(value))}"
}
