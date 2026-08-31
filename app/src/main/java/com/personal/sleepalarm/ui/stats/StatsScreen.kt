package com.personal.sleepalarm.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sleepalarm.R
import com.personal.sleepalarm.util.CsvExporter
import com.personal.sleepalarm.ui.activity.ManualActivitySheet
import com.personal.sleepalarm.ui.theme.appAccents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accents = MaterialTheme.appAccents
    var statsMode by remember { mutableStateOf("sleep") }
    var showManualActivity by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val exportScope = rememberCoroutineScope()
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            exportScope.launch {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        withContext(Dispatchers.IO) {
                            CsvExporter.writeSessionsCsv(context, state.allSessions, output)
                        }
                    } ?: error("Output stream is unavailable")
                    true
                }.getOrDefault(false)
                snackbarHostState.showSnackbar(
                    context.getString(if (ok) R.string.stats_export_done else R.string.stats_export_empty)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statsMode == "sleep",
                    onClick = { statsMode = "sleep" },
                    label = { Text(stringResource(R.string.tab_home)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accents.sleep.container,
                        selectedLabelColor = accents.sleep.onContainer
                    )
                )
                FilterChip(
                    selected = statsMode == "activity",
                    onClick = { statsMode = "activity" },
                    label = { Text(stringResource(R.string.stats_tab_study)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accents.progress.container,
                        selectedLabelColor = accents.progress.onContainer
                    )
                )
                FilterChip(
                    selected = statsMode == "productivity",
                    onClick = { statsMode = "productivity" },
                    label = { Text(stringResource(R.string.stats_tab_tasks)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accents.work.container,
                        selectedLabelColor = accents.work.onContainer
                    )
                )
                FilterChip(
                    selected = statsMode == "energy",
                    onClick = { statsMode = "energy" },
                    label = { Text(stringResource(R.string.stats_tab_energy)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accents.energy.container,
                        selectedLabelColor = accents.energy.onContainer
                    )
                )
            }

            when (statsMode) {
                "activity" -> ActivityStatsContent(onAddActivity = { showManualActivity = true })
                "productivity" -> ProductivityStatsContent()
                "energy" -> EnergyStatsContent(state.energyAnalytics)
                else -> SleepStatsContent(
                        state = state,
                        onExport = { exportCsvLauncher.launch("sleep_sessions.csv") },
                        onCorrectDuration = viewModel::correctSleepDuration
                    )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showManualActivity) {
        ManualActivitySheet(onDismiss = { showManualActivity = false })
    }
}
