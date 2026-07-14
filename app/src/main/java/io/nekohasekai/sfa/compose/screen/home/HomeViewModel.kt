package io.nekohasekai.sfa.compose.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.NodeCatalog
import io.nekohasekai.sfa.utils.SubscriptionInfo
import io.nekohasekai.sfa.utils.SubscriptionInfoFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class HomeSubscriptionState(
    val loading: Boolean = false,
    val info: SubscriptionInfo? = null,
    val loadedForUrl: String? = null,
)

// Держит данные подписки (subscription-userinfo) для главного экрана.
// Обновляется лениво при появлении remoteURL и по кнопке «Обновить».
class HomeViewModel : ViewModel() {
    private val _subscription = MutableStateFlow(HomeSubscriptionState())
    val subscription = _subscription.asStateFlow()

    fun ensureLoaded(remoteURL: String?) {
        if (remoteURL.isNullOrBlank()) {
            if (_subscription.value.info != null || _subscription.value.loadedForUrl != null) {
                _subscription.value = HomeSubscriptionState()
            }
            return
        }
        val state = _subscription.value
        if (state.loading) return
        if (state.loadedForUrl == remoteURL && state.info != null) return
        refresh(remoteURL)
    }

    fun refresh(remoteURL: String?) {
        if (remoteURL.isNullOrBlank()) return
        if (_subscription.value.loading) return
        _subscription.value = _subscription.value.copy(loading = true)
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { SubscriptionInfoFetcher.fetch(remoteURL) }
            _subscription.value = HomeSubscriptionState(
                loading = false,
                info = info ?: _subscription.value.info,
                loadedForUrl = remoteURL,
            )
        }
    }

    // Офлайн-каталог нод из файла конфига: список локаций доступен
    // и при выключенном VPN (как в Happ и десктоп-клиенте).
    private val _catalog = MutableStateFlow<NodeCatalog.Catalog?>(null)
    val catalog = _catalog.asStateFlow()

    private var catalogPath: String? = null

    fun loadCatalog(configPath: String?) {
        if (configPath.isNullOrBlank()) {
            catalogPath = null
            _catalog.value = null
            return
        }
        if (catalogPath == configPath && _catalog.value != null) return
        catalogPath = configPath
        viewModelScope.launch {
            _catalog.value = withContext(Dispatchers.IO) { NodeCatalog.fromConfigFile(configPath) }
        }
    }

    // Выбранная нода, сохранённая в настройках (актуальна при выключенном VPN).
    private val _storedNode = MutableStateFlow("")
    val storedNode = _storedNode.asStateFlow()

    // Пользовательский порядок нод (drag-and-drop в списке локаций).
    private val _nodeOrder = MutableStateFlow<List<String>>(emptyList())
    val nodeOrder = _nodeOrder.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _storedNode.value = Settings.viphoonSelectedNode
            _nodeOrder.value = runCatching {
                val arr = JSONArray(Settings.viphoonNodeOrder)
                (0 until arr.length()).map { arr.getString(it) }
            }.getOrDefault(emptyList())
        }
    }

    fun rememberNodeSelection(tag: String) {
        _storedNode.value = tag
        viewModelScope.launch(Dispatchers.IO) {
            Settings.viphoonSelectedNode = tag
        }
    }

    fun saveNodeOrder(tags: List<String>) {
        _nodeOrder.value = tags
        viewModelScope.launch(Dispatchers.IO) {
            Settings.viphoonNodeOrder = JSONArray(tags).toString()
        }
    }
}
