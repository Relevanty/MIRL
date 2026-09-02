package com.personal.sleepalarm.ui.dday

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.ui.components.TaskDeadlinePlanSummary
import com.personal.sleepalarm.ui.theme.AppAccentTone
import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.util.DeadlineLinks
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/** One deadline treatment shared by the selected day and the calendar's deadline list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeadlineCard(
    event: DDayEntity,
    plan: DDayPlanInfo? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTask: ((Int) -> Unit)? = null,
    dueAtMillis: Long? = null,
    isCompleted: Boolean = false,
    today: LocalDate = LocalDate.now()
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dueAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val due = dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val date = due?.toLocalDate() ?: remember(event.targetDate) { runCatching { LocalDate.parse(event.targetDate) }.getOrNull() }
    val days = date?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    val isOverdue = !isCompleted && (dueAtMillis?.let { it <= nowMillis } ?: (days != null && days < 0))
    val tone = when {
        isCompleted -> MaterialTheme.appAccents.success
        days == null -> MaterialTheme.appAccents.schedule
        isOverdue -> MaterialTheme.appAccents.urgent
        days <= 7 -> MaterialTheme.appAccents.warning
        else -> MaterialTheme.appAccents.schedule
    }
    val linkTone = MaterialTheme.appAccents.info
    val progressTone = MaterialTheme.appAccents.progress
    val errorTone = MaterialTheme.appAccents.urgent
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(date, dueAtMillis, locale) {
        due?.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", locale)) ?: date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)) ?: event.targetDate
    }
    val links = remember(event.linksJson) { DeadlineLinks.decode(event.linksJson) }
    var linkError by remember(event.id, event.linksJson) { mutableStateOf(false) }
    val status = when {
        isCompleted -> stringResource(R.string.deadline_task_completed)
        days == null -> stringResource(R.string.deadline_date_missing)
        isOverdue && dueAtMillis != null -> stringResource(R.string.deadline_task_overdue)
        days < 0 -> pluralStringResource(R.plurals.deadline_days_overdue, -days, -days)
        days == 0 -> stringResource(R.string.deadline_due_today)
        days == 1 -> stringResource(R.string.deadline_due_tomorrow)
        else -> pluralStringResource(R.plurals.deadline_days_remaining, days, days)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = tone.container,
        contentColor = tone.onContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp), tint = tone.onContainer)
                Text(
                    stringResource(R.string.deadline_kind),
                    style = MaterialTheme.typography.labelMedium,
                    color = tone.onContainer,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.deadline_edit),
                    modifier = Modifier.size(18.dp),
                    tint = tone.onContainer
                )
            }
            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tone.onContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = tone.action, contentColor = tone.onAction, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color = tone.onAction,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tone.onContainer,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
            if (event.notes.isNotBlank()) {
                Text(
                    event.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tone.onContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (plan != null) {
                Surface(
                    color = progressTone.container,
                    contentColor = progressTone.onContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (plan.taskPlan == null || plan.linkedTitle != event.title) {
                            Text(
                                plan.linkedTitle,
                                color = progressTone.onContainer,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (plan.taskPlan != null) {
                            TaskDeadlinePlanSummary(plan.taskPlan)
                        } else if (plan.hasWorkBudget) {
                            LinearProgressIndicator(
                                progress = { plan.readinessPercent.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = progressTone.color,
                                trackColor = progressTone.action
                            )
                            Text(
                                stringResource(R.string.deadline_time_accounted, plan.readinessPercent),
                                color = progressTone.onContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (plan.remainingMinutes > 0) {
                                Text(
                                    stringResource(R.string.deadline_work_remaining_overdue, plan.remainingMinutes),
                                    color = progressTone.onContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (event.taskId != null && onOpenTask != null) {
                            TextButton(
                                onClick = { onOpenTask(event.taskId) },
                                colors = ButtonDefaults.textButtonColors(contentColor = progressTone.onContainer)
                            ) {
                                Text(stringResource(R.string.deadline_open_task))
                            }
                        }
                    }
                }
            }
            if (links.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    links.forEachIndexed { index, link ->
                        val openDescription = stringResource(R.string.deadline_open_link_description, link)
                        Surface(
                            onClick = { linkError = !openDeadlineLink(context, link) },
                            color = linkTone.action,
                            contentColor = linkTone.onAction,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.widthIn(max = 280.dp).semantics { contentDescription = openDescription }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp), tint = linkTone.onAction)
                                Text(
                                    if (links.size > 1) "${index + 1}. ${deadlineLinkLabel(link)}" else deadlineLinkLabel(link),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = linkTone.onAction,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }
                }
            }
            if (linkError) {
                Surface(color = errorTone.action, contentColor = errorTone.onAction, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        stringResource(R.string.deadline_browser_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = errorTone.onAction,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

/** Never grant URI permissions or dispatch arbitrary schemes from a stored link. */
internal fun openDeadlineLink(context: Context, value: String): Boolean {
    val link = DeadlineLinks.normalize(value) ?: return false
    return try {
        val uri = Uri.parse(link)
        // A hostless web intent matches generic browsers, not domain-specific App Links.
        // Never probe the user's real URL: a dedicated app might own that domain.
        val browserProbe = Intent(Intent.ACTION_VIEW, Uri.parse("${uri.scheme}://"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val packages = context.packageManager.queryIntentActivities(browserProbe, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo }
            .filter { it.exported && it.enabled }
            .map { it.packageName }
            .toSet()
        if (packages.isEmpty()) return false
        val defaultPackage = context.packageManager.resolveActivity(browserProbe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it in packages }
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (defaultPackage != null) {
            intent.setPackage(defaultPackage)
        } else {
            // Let Android ask which browser to use. Selector controls resolution only;
            // the selected browser receives the original, fully preserved web address.
            intent.selector = browserProbe
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

private fun deadlineLinkLabel(link: String): String {
    val uri = Uri.parse(link)
    val host = uri.host?.removePrefix("www.") ?: return link
    val path = uri.path.orEmpty().trimEnd('/')
    val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
    val fragment = uri.encodedFragment?.let { "#$it" }.orEmpty()
    return host + path + query + fragment
}

/** Use an existing semantic tone for every standard Material control as well. */
@Composable
internal fun DeadlineToneTheme(tone: AppAccentTone, content: @Composable () -> Unit) {
    val urgent = MaterialTheme.appAccents.urgent
    val scheme = MaterialTheme.colorScheme.copy(
        primary = tone.color,
        onPrimary = tone.onColor,
        primaryContainer = tone.container,
        onPrimaryContainer = tone.onContainer,
        secondary = tone.color,
        onSecondary = tone.onColor,
        secondaryContainer = tone.action,
        onSecondaryContainer = tone.onAction,
        tertiary = tone.color,
        onTertiary = tone.onColor,
        tertiaryContainer = tone.action,
        onTertiaryContainer = tone.onAction,
        surface = tone.container,
        onSurface = tone.onContainer,
        surfaceVariant = tone.action,
        onSurfaceVariant = tone.onAction,
        surfaceContainerLowest = tone.container,
        surfaceContainerLow = tone.container,
        surfaceContainer = tone.container,
        surfaceContainerHigh = tone.action,
        surfaceContainerHighest = tone.action,
        surfaceTint = tone.color,
        inverseSurface = tone.onContainer,
        inverseOnSurface = tone.container,
        outline = tone.color,
        outlineVariant = tone.action,
        error = urgent.color,
        onError = urgent.onColor,
        errorContainer = urgent.container,
        onErrorContainer = urgent.onContainer
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
