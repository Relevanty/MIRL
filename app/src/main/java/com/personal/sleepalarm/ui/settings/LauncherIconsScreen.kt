package com.personal.sleepalarm.ui.settings

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.launcher.LauncherIconFilter
import com.personal.sleepalarm.launcher.LauncherIconSpec
import kotlinx.coroutines.launch

@Composable
fun LauncherIconsScreen(
    onBack: () -> Unit,
    viewModel: LauncherIconsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val autoMatch by viewModel.autoMatch.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LauncherIconFilter.ALL) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val creativeTone = MaterialTheme.appAccents.creative

    val visibleIcons = remember(filter, viewModel.icons) {
        if (filter == LauncherIconFilter.ALL) viewModel.icons
        else viewModel.icons.filter { filter in it.filters }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = creativeTone.color
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = stringResource(R.string.launcher_icons_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = creativeTone.color
                    )
                    Text(
                        text = stringResource(R.string.launcher_icons_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = creativeTone.color
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = creativeTone.container,
                    contentColor = creativeTone.onContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { viewModel.setAutoMatch(!autoMatch) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.launcher_icon_auto_match),
                            style = MaterialTheme.typography.titleSmall,
                            color = creativeTone.onContainer
                        )
                        Text(
                            text = stringResource(R.string.launcher_icon_auto_match_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = creativeTone.onContainer
                        )
                    }
                    Switch(
                        checked = autoMatch,
                        onCheckedChange = viewModel::setAutoMatch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = creativeTone.onColor,
                            checkedTrackColor = creativeTone.color,
                            uncheckedThumbColor = creativeTone.onAction,
                            uncheckedTrackColor = creativeTone.action
                        )
                    )
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LauncherIconFilter.entries, key = LauncherIconFilter::name) { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(stringResource(item.titleRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = creativeTone.action,
                            labelColor = creativeTone.onAction,
                            selectedContainerColor = creativeTone.color,
                            selectedLabelColor = creativeTone.onColor
                        )
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 142.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(visibleIcons, key = LauncherIconSpec::id) { icon ->
                    LauncherIconCard(
                        icon = icon,
                        selected = icon.id == selectedId,
                        onClick = {
                            viewModel.select(icon.id)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.launcher_icon_refresh_notice)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherIconCard(
    icon: LauncherIconSpec,
    selected: Boolean,
    onClick: () -> Unit
) {
    val creativeTone = MaterialTheme.appAccents.creative
    val borderColor by animateColorAsState(
        targetValue = if (selected) creativeTone.color else Color.Transparent,
        label = "launcherIconBorder"
    )
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = creativeTone.container,
            contentColor = creativeTone.onContainer
        ),
        modifier = Modifier.border(2.dp, borderColor, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(10.dp)
        ) {
            Image(
                painter = painterResource(icon.previewRes),
                contentDescription = stringResource(icon.nameRes),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .background(creativeTone.color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.launcher_icon_selected),
                        tint = creativeTone.onColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(icon.nameRes),
            style = MaterialTheme.typography.titleSmall,
            color = creativeTone.onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        )
    }
}
