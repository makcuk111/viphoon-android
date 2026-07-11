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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
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
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.NodeCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BYPASS_REGEX = Regex("обход|bypass|white|бел[ыо]|lte", RegexOption.IGNORE_CASE)

// Строка списка локаций (плоский список, как в Happ и десктоп-клиенте).
private data class NodeRow(
    val tag: String,
    val name: String,
    val flag: String?,
    val subtitle: String?,
    val delay: Int,
    val isSelected: Boolean,
    val isAuto: Boolean,
)

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
    val catalog by homeViewModel.catalog.collectAsState()
    val storedNode by homeViewModel.storedNode.collectAsState()

    val hasProfile = dashboardUiState.selectedProfileId != -1L
    val selectedProfile = remember(dashboardUiState.profiles, dashboardUiState.selectedProfileId) {
        dashboardUiState.profiles.firstOrNull { it.id == dashboardUiState.selectedProfileId }
    }
    val remoteURL = selectedProfile?.typed?.remoteURL?.takeIf { it.isNotBlank() }

    LaunchedEffect(remoteURL) {
        homeViewModel.ensureLoaded(remoteURL)
    }
    LaunchedEffect(selectedProfile?.typed?.path) {
        homeViewModel.loadCatalog(selectedProfile?.typed?.path)
    }

    // Главная группа — селектор из конфига (обычно «🎛 ViPhooN»)
    val mainGroup = remember(groupsUiState.groups) {
        groupsUiState.groups.firstOrNull { it.type.equals("selector", ignoreCase = true) }
            ?: groupsUiState.groups.firstOrNull { it.selectable }
    }
    val online = mainGroup != null

    // Синхронизация выбранной ноды: выбор, сделанный при выключенном VPN,
    // применяется после старта; актуальный выбор запоминается для офлайна.
    LaunchedEffect(mainGroup?.tag, mainGroup?.selected, storedNode) {
        val group = mainGroup ?: return@LaunchedEffect
        if (storedNode.isNotEmpty() &&
            storedNode != group.selected &&
            group.items.any { it.tag == storedNode }
        ) {
            groupsViewModel.selectGroupItem(group.tag, storedNode)
        } else if (group.selected.isNotEmpty() && group.selected != storedNode) {
            homeViewModel.rememberNodeSelection(group.selected)
        }
    }

    var showNodeSheet by remember { mutableStateOf(false) }

    val currentNode = remember(groupsUiState.groups, catalog, storedNode) {
        pickCurrentNode(groupsUiState.groups, catalog, storedNode)
    }

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

        if (hasProfile) {
            SubscriptionRow(
                profileName = dashboardUiState.selectedProfileName,
                loading = subscription.loading,
                onRefresh = { homeViewModel.refresh(remoteURL) },
            )
        } else {
            AddSubscriptionBanner(onClick = onOpenAddSubscription)
        }

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
            node = currentNode,
            enabled = online || catalog != null,
            onClick = { showNodeSheet = true },
        )

        Spacer(Modifier.height(20.dp))
    }

    if (showNodeSheet) {
        val rows = remember(mainGroup, catalog, storedNode) {
            buildNodeRows(mainGroup, catalog, storedNode)
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNodeSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Локации",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (online) {
                        IconButton(onClick = { mainGroup?.let { groupsViewModel.urlTest(it.tag) } }) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Проверить пинг",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (!online) {
                    Text(
                        text = "VPN выключен — пинг появится после подключения",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rows, key = { it.tag }) { row ->
                        NodeRowItem(
                            row = row,
                            onClick = {
                                mainGroup?.let { groupsViewModel.selectGroupItem(it.tag, row.tag) }
                                homeViewModel.rememberNodeSelection(row.tag)
                                showNodeSheet = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// Плоский список: автовыбор первым, дальше все локации в порядке подписки.
private fun buildNodeRows(mainGroup: Group?, catalog: NodeCatalog.Catalog?, storedNode: String): List<NodeRow> {
    data class RawNode(val tag: String, val type: String, val delay: Int)

    val raw: List<RawNode>
    val selectedTag: String
    if (mainGroup != null) {
        raw = mainGroup.items
            .filter { it.type != "direct" && it.type != "block" }
            .map { RawNode(it.tag, it.type, it.urlTestDelay) }
        selectedTag = mainGroup.selected
    } else if (catalog != null) {
        raw = catalog.nodes.map { RawNode(it.tag, it.type, 0) }
        selectedTag = storedNode
    } else {
        return emptyList()
    }

    val rows = raw.map { node ->
        val isAuto = NodeCatalog.isAuto(node.type)
        val subtitle = when {
            isAuto -> "лучший сервер по пингу"
            BYPASS_REGEX.containsMatchIn(node.tag) -> "обход белых списков · трафик из лимита"
            NodeCatalog.isHysteria(node.type) -> "Hysteria2 · устойчив к блокировкам"
            else -> null
        }
        NodeRow(
            tag = node.tag,
            name = if (isAuto) "Автовыбор" else NodeCatalog.cleanName(node.tag).ifBlank { node.tag },
            flag = if (isAuto) null else NodeCatalog.flagFor(node.tag),
            subtitle = subtitle,
            delay = node.delay,
            isSelected = if (selectedTag.isNotEmpty()) node.tag == selectedTag else isAuto,
            isAuto = isAuto,
        )
    }
    return rows.sortedByDescending { it.isAuto }
}

@Composable
private fun NodeRowItem(row: NodeRow, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (row.isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = if (row.isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.isAuto) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Text(text = row.flag ?: "🌐", fontSize = 22.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (row.delay > 0) {
                Text(
                    text = "${row.delay} ms",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = latencyColorFor(row.delay),
                )
                Spacer(Modifier.size(10.dp))
            }
            if (row.isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private data class CurrentNode(val title: String, val flag: String?, val isAuto: Boolean, val delay: Int)

// Текущая локация: при работающем VPN — из групп (автовыбор раскрывается до
// реальной ноды), при выключенном — из сохранённого выбора и офлайн-каталога.
private fun pickCurrentNode(groups: List<Group>, catalog: NodeCatalog.Catalog?, storedNode: String): CurrentNode? {
    val group = groups.firstOrNull { it.type.equals("selector", ignoreCase = true) }
        ?: groups.firstOrNull { it.selectable }
    if (group != null && group.selected.isNotBlank()) {
        var tag = group.selected
        var delay = group.items.firstOrNull { it.tag == tag }?.urlTestDelay ?: 0
        // выбран «Автовыбор» (urltest) — показываем ноду, которую он выбрал
        val inner = groups.firstOrNull { it.tag == tag && it.tag != group.tag }
        val isAuto = inner != null ||
            group.items.firstOrNull { it.tag == tag }?.type?.let(NodeCatalog::isAuto) == true
        if (inner != null && inner.selected.isNotBlank()) {
            tag = inner.selected
            delay = inner.items.firstOrNull { it.tag == tag }?.urlTestDelay ?: delay
        }
        return if (isAuto && inner?.selected.isNullOrBlank()) {
            CurrentNode("Автовыбор", null, true, delay)
        } else {
            CurrentNode(NodeCatalog.cleanName(tag).ifBlank { tag }, NodeCatalog.flagFor(tag), isAuto, delay)
        }
    }

    // VPN выключен: показываем сохранённый выбор
    val nodes = catalog?.nodes ?: return null
    val stored = nodes.firstOrNull { it.tag == storedNode }
    return when {
        stored == null || NodeCatalog.isAuto(stored.type) -> CurrentNode("Автовыбор", null, true, 0)
        else -> CurrentNode(
            NodeCatalog.cleanName(stored.tag).ifBlank { stored.tag },
            NodeCatalog.flagFor(stored.tag),
            false,
            0,
        )
    }
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
                imageVector = Icons.Outlined.Add,
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
private fun AddSubscriptionBanner(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Добавить подписку",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SubscriptionRow(profileName: String?, loading: Boolean, onRefresh: () -> Unit) {
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
                    text = profileName ?: "Подписка",
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
private fun CurrentNodeBar(node: CurrentNode?, enabled: Boolean, onClick: () -> Unit) {
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
            if (node?.flag != null) {
                Text(text = node.flag, fontSize = 22.sp)
            } else {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (node?.isAuto == true) "Локация · автовыбор" else "Текущая локация",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = node?.title ?: (if (enabled) "Выбрать локацию" else "Нет подписки"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (node != null && node.delay > 0) {
                Text(
                    text = "${node.delay} ms",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = latencyColorFor(node.delay),
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
