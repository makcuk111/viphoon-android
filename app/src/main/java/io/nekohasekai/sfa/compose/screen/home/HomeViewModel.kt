package io.nekohasekai.sfa.compose.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sfa.utils.SubscriptionInfo
import io.nekohasekai.sfa.utils.SubscriptionInfoFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
}
