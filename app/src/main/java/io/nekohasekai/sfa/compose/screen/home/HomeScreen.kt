package io.nekohasekai.sfa.compose.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsScreen
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.constant.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    groupsViewModel: GroupsViewModel,
    onOpenSettings: () -> Unit,
    onOpenAddSubscription: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()
    val groupsUiState by groupsViewModel.uiState.collectAsState()
    val subscription by homeViewModel.subscription.collectAsState()

    val hasProfile = dashboardUiState.selectedProfileId != -1L
    val remoteURL = remember(dashboardUiState.profiles, dashboardUiState.selectedProfileId) {
        dashboardUiState.profiles
            .firstOrNull { it.id == dashboardUiState.selectedProfileId }
            ?.typed?.remoteURL
            ?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(remoteURL) {
        homeViewModel.ensureLoaded(remoteURL)
    }

    var showNodeSheet by remember { mutableStateOf(false) }

    val currentNode = remember(groupsUiState.groups) { pickCurrentNode(groupsUiState.groups) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        HomeHeader(
            onImport = onOpenAddSubscription,
            onSettings = onOpenSettings,
        )

        Spacer(Modifier.height(12.dp))

        SubscriptionRow(
            hasProfile = hasProfile,
            profileName = dashboardUiState.selectedProfileName,
            loading = subscription.loading,
            onRefresh = {
                if (remoteURL != null) homeViewModel.refresh(remoteURL) else onOpenAddSubscription()
            },
        )

        Spacer(Modifier.weight(1f))

        ConnectButton(
            serviceStatus = serviceStatus,
            enabled = hasProfile,
            onClick = {
                if (hasProfile) {
                    dashboardViewModel.toggleService()
                } else {
                    onOpenAddSubscription()
                }
            },
        )

        Spacer(Modifier.weight(1f))

        subscription.info?.let { info ->
            SubscriptionInfoCard(info)
            Spacer(Modifier.height(12.dp))
        }

        CurrentNodeBar(
            nodeName = currentNode?.name,
            delay = currentNode?.delay ?: 0,
            enabled = groupsUiState.groups.isNotEmpty(),
            onClick = { showNodeSheet = true },
        )

        Spacer(Modifier.height(20.dp))
    }

    if (showNodeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNodeSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Локации",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
                )
                GroupsScreen(
                    serviceStatus = serviceStatus,
                    viewModel = groupsViewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp),
                )
            }
        }
    }
}

private data class CurrentNode(val name: String, val delay: Int)

// Текущая нода = выбранный аутбаунд основной группы-селектора (или первой группы).
private fun pickCurrentNode(groups: List<Group>): CurrentNode? {
    val group = groups.firstOrNull { it.selectable } ?: groups.firstOrNull() ?: return null
    val selected = group.selected.ifBlank { return null }
    val delay = group.items.firstOrNull { it.tag == selected }?.urlTestDelay ?: 0
    return CurrentNode(selected, delay)
}

@Composable
private fun HomeHeader(onImport: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onImport) {
            Icon(
                imageVector = Icons.Outlined.AddLink,
                contentDescription = "Добавить подписку",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "ViPhooN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Настройки",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SubscriptionRow(hasProfile: Boolean, profileName: String?, loading: Boolean, onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasProfile) (profileName ?: "Подписка") else "Подписка не указана",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Row(
                    modifier = Modifier.clickable(onClick = onRefresh),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Обновить",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(serviceStatus: Status, enabled: Boolean, onClick: () -> Unit) {
    val connected = serviceStatus == Status.Started
    val transitioning = serviceStatus == Status.Starting || serviceStatus == Status.Stopping

    val label = when {
        !enabled -> "НЕТ ПОДПИСКИ"
        serviceStatus == Status.Started -> "ПОДКЛЮЧЕНО"
        serviceStatus == Status.Starting -> "ПОДКЛЮЧЕНИЕ…"
        serviceStatus == Status.Stopping -> "ОТКЛЮЧЕНИЕ…"
        else -> "ОТКЛЮЧЕНО"
    }

    val accent = MaterialTheme.colorScheme.primary
    val ringColor by animateColorAsState(
        targetValue = if (connected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        animationSpec = tween(400),
        label = "ring",
    )

    // Пульсация свечения при активном подключении.
    val infinite = rememberInfiniteTransition(label = "glow")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val glowScale = if (connected) pulse else 1f

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (connected) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(glowScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0f)),
                            ),
                            shape = CircleShape,
                        ),
                )
            }
            Surface(
                onClick = onClick,
                enabled = enabled && !transitioning,
                shape = CircleShape,
                color = if (connected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(2.dp, ringColor),
                modifier = Modifier.size(200.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (transitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp,
                            color = accent,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Power,
                            contentDescription = null,
                            tint = if (connected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        color = if (connected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentNodeBar(nodeName: String?, delay: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Текущая локация",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = nodeName ?: "Не выбрана",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (delay > 0) {
                Text(
                    text = "$delay ms",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = latencyColorFor(delay),
                )
                Spacer(Modifier.size(8.dp))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun latencyColorFor(delay: Int) = when {
    delay < 100 -> MaterialTheme.colorScheme.tertiary
    delay < 300 -> MaterialTheme.colorScheme.primary
    delay < 500 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun SubscriptionInfoCard(info: io.nekohasekai.sfa.utils.SubscriptionInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoCell(
                    modifier = Modifier.weight(1f),
                    title = "Осталось",
                    value = info.remainingDays?.let { "$it дн." } ?: "—",
                )
                InfoCell(
                    modifier = Modifier.weight(1f),
                    title = "Трафик",
                    value = if (info.hasTraffic) {
                        "${Libbox.formatBytes(info.usedBytes)} / ${Libbox.formatBytes(info.totalBytes)}"
                    } else {
                        Libbox.formatBytes(info.usedBytes)
                    },
                )
            }
            if (info.hasExpire) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Действует до ${formatDate(info.expireEpochSeconds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatDate(epochSeconds: Long): String =
    runCatching {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(epochSeconds * 1000))
    }.getOrDefault("—")
