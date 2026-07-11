package io.nekohasekai.sfa.compose.screen.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compose.base.SelectableMessageDialog
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.SubscriptionEnhancer
import io.nekohasekai.sfa.utils.ViphoonLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Date

// Экран добавления подписки в стиле примера: «Вставьте свой ключ», поле ввода,
// чекбокс «Заменить текущую», кнопка «Добавить». Ссылку нормализуем через
// ViphoonLinks (…/singbox) и создаём remote-профиль с авто-обновлением.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    initialUrl: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf(initialUrl ?: "") }
    var replace by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    OverrideTopBar {
        TopAppBar(
            title = { Text("Подписка") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )
    }

    if (error != null) {
        SelectableMessageDialog(
            title = "Ошибка",
            message = error ?: "",
            onDismiss = { error = null },
        )
    }

    fun submit() {
        if (saving) return
        val raw = key.trim()
        if (raw.isEmpty()) {
            error = "Вставьте ключ подписки"
            return
        }
        saving = true
        scope.launch {
            try {
                val url = ViphoonLinks.normalize(raw)
                val host = android.net.Uri.parse(url).host ?: "ViPhooN"
                withContext(Dispatchers.IO) {
                    if (replace) {
                        ProfileManager.list().forEach { runCatching { ProfileManager.delete(it) } }
                    }
                    val typed = TypedProfile().apply {
                        type = TypedProfile.Type.Remote
                        remoteURL = url
                        autoUpdate = true
                        autoUpdateInterval = 60
                        lastUpdated = Date()
                    }
                    val profile = Profile(name = host, typed = typed).apply {
                        userOrder = ProfileManager.nextOrder()
                    }
                    val fileID = ProfileManager.nextFileID()
                    val dir = File((context.applicationContext as Application).filesDir, "configs").also { it.mkdirs() }
                    val configFile = File(dir, "$fileID.json")
                    typed.path = configFile.path

                    val content = SubscriptionEnhancer.fetchAndEnhance(url)
                    Libbox.checkConfig(content)
                    configFile.writeText(content)

                    ProfileManager.create(profile, andSelect = true)
                }
                onAdded()
            } catch (e: Exception) {
                error = e.message ?: "Не удалось добавить подписку"
            } finally {
                saving = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "ViPhooN",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Вставьте свой ключ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://sub.viphoon.app/…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = replace, onCheckedChange = { replace = it })
                Text(
                    text = "Заменить текущую",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { submit() },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Добавить")
                }
            }
        }
    }
}
