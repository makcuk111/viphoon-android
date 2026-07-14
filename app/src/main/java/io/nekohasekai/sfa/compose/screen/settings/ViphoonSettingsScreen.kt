package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.navigation.safeNavigateUp
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.ConfigTweaks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Экран настроек ViPhooN: обход блокировок (фрагментация TLS, мультиплексор)
// и маршрутизация по сайтам — те же опции, что в десктоп-клиенте.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViphoonSettingsScreen(navController: NavController, serviceStatus: Status = Status.Stopped) {
    OverrideTopBar {
        TopAppBar(
            title = { Text("Настройки ViPhooN") },
            navigationIcon = {
                IconButton(onClick = { navController.safeNavigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                    )
                }
            },
        )
    }

    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)
    val onChanged = { notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Restart) }

    var tlsRecordFragment by remember { mutableStateOf(Settings.viphoonTlsRecordFragment) }
    var tlsFragment by remember { mutableStateOf(Settings.viphoonTlsFragment) }
    var muxEnabled by remember { mutableStateOf(Settings.viphoonMuxEnabled) }
    var muxStreams by remember { mutableStateOf(Settings.viphoonMuxMaxStreams) }
    var routeMode by remember { mutableStateOf(Settings.viphoonRouteMode) }
    var siteList by remember { mutableStateOf(Settings.viphoonSiteList.toList().sorted()) }
    var newSite by remember { mutableStateOf("") }
    var appList by remember { mutableStateOf(Settings.viphoonAppList.toList().sorted()) }
    var showAppPicker by remember { mutableStateOf(false) }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SectionTitle("Обход блокировок")

        SettingsCard {
            ToggleItem(
                title = "Фрагментация TLS-записей",
                subtitle = "record_fragment: быстрый способ обхода DPI",
                checked = tlsRecordFragment,
                onCheckedChange = {
                    tlsRecordFragment = it
                    Settings.viphoonTlsRecordFragment = it
                    onChanged()
                },
            )
            ToggleItem(
                title = "Фрагментация TCP-пакетов",
                subtitle = "fragment: медленнее, но устойчивее к DPI",
                checked = tlsFragment,
                onCheckedChange = {
                    tlsFragment = it
                    Settings.viphoonTlsFragment = it
                    onChanged()
                },
            )
            ToggleItem(
                title = "Мультиплексор",
                subtitle = "Несколько потоков в одном соединении (нужна поддержка сервера)",
                checked = muxEnabled,
                onCheckedChange = {
                    muxEnabled = it
                    Settings.viphoonMuxEnabled = it
                    onChanged()
                },
            )
            if (muxEnabled) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = "Максимум потоков: $muxStreams",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = muxStreams.toFloat(),
                        onValueChange = { muxStreams = it.toInt().coerceIn(1, 16) },
                        onValueChangeFinished = {
                            Settings.viphoonMuxMaxStreams = muxStreams
                            onChanged()
                        },
                        valueRange = 1f..16f,
                        steps = 14,
                    )
                }
            }
        }

        SectionTitle("Маршрутизация по сайтам")

        SettingsCard {
            RouteModeItem(
                title = "Весь трафик через VPN",
                selected = routeMode == Settings.ROUTE_MODE_ALL,
                onClick = {
                    routeMode = Settings.ROUTE_MODE_ALL
                    Settings.viphoonRouteMode = routeMode
                    onChanged()
                },
            )
            RouteModeItem(
                title = "Сайты из списка — напрямую",
                subtitle = "Остальной трафик идёт через VPN",
                selected = routeMode == Settings.ROUTE_MODE_EXCLUDE,
                onClick = {
                    routeMode = Settings.ROUTE_MODE_EXCLUDE
                    Settings.viphoonRouteMode = routeMode
                    onChanged()
                },
            )
            RouteModeItem(
                title = "Через VPN — только список",
                subtitle = "Остальной трафик идёт напрямую",
                selected = routeMode == Settings.ROUTE_MODE_ONLY,
                onClick = {
                    routeMode = Settings.ROUTE_MODE_ONLY
                    Settings.viphoonRouteMode = routeMode
                    onChanged()
                },
            )
        }

        if (routeMode != Settings.ROUTE_MODE_ALL) {
            SectionTitle("Список сайтов (${siteList.size}/${Settings.MAX_SITE_LIST})")

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newSite,
                        onValueChange = { newSite = it },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val domain = ConfigTweaks.normalizeDomain(newSite)
                            if (domain != null && siteList.size < Settings.MAX_SITE_LIST) {
                                val updated = (siteList + domain).distinct().sorted()
                                siteList = updated
                                Settings.viphoonSiteList = updated.toSet()
                                newSite = ""
                                onChanged()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Добавить",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                siteList.forEach { site ->
                    ListItem(
                        headlineContent = {
                            Text(site, style = MaterialTheme.typography.bodyLarge)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    val updated = siteList - site
                                    siteList = updated
                                    Settings.viphoonSiteList = updated.toSet()
                                    onChanged()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            SectionTitle("Приложения (${appList.size}/${Settings.MAX_APP_LIST})")

            SettingsCard {
                ListItem(
                    headlineContent = { Text("Выбрать приложения", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text(
                            "Маршрутизация по установленным приложениям",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { showAppPicker = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Выбрать",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier.clickable { showAppPicker = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                appList.forEach { pkg ->
                    ListItem(
                        headlineContent = {
                            Text(pkg, style = MaterialTheme.typography.bodyMedium)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    val updated = appList - pkg
                                    appList = updated
                                    Settings.viphoonAppList = updated.toSet()
                                    onChanged()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showAppPicker) {
        AppPickerDialog(
            selected = appList.toSet(),
            maxApps = Settings.MAX_APP_LIST,
            onDismiss = { showAppPicker = false },
            onConfirm = { picked ->
                appList = picked.toList().sorted()
                Settings.viphoonAppList = picked
                showAppPicker = false
                onChanged()
            },
        )
    }
}

private data class AppEntry(val packageName: String, val label: String)

@Composable
private fun AppPickerDialog(
    selected: Set<String>,
    maxApps: Int,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var picked by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            val q = query.lowercase()
            apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Приложения (${picked.size}/$maxApps)") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val on = picked.contains(app.packageName)
                        ListItem(
                            headlineContent = {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { checked ->
                                        picked = if (checked) {
                                            if (picked.size >= maxApps) picked else picked + app.packageName
                                        } else {
                                            picked - app.packageName
                                        }
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                picked = if (on) {
                                    picked - app.packageName
                                } else {
                                    if (picked.size >= maxApps) picked else picked + app.packageName
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun ToggleItem(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun RouteModeItem(title: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
