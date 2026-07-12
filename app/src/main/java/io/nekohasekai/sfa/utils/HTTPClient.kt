package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.unwrap
import java.io.Closeable
import java.util.Locale

class HTTPClient : Closeable {
    companion object {
        val userAgent by lazy {
            var userAgent = "SFA/"
            userAgent += BuildConfig.VERSION_NAME
            userAgent += " ("
            userAgent += BuildConfig.VERSION_CODE
            userAgent += "; sing-box "
            userAgent += Libbox.version()
            userAgent += "; language "
            userAgent += Locale.getDefault().toLanguageTag().replace("-", "_")
            userAgent += ")"
            userAgent
        }
    }

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String, overrideUserAgent: String? = null): String {
        val effectiveUserAgent = overrideUserAgent ?: userAgent
        // HWID-заголовки для Remnawave (привязка устройства). Безвредны для
        // прочих endpoint'ов — они их просто игнорируют.
        val deviceHeaders = runCatching {
            mapOf(
                "x-hwid" to DeviceInfo.hwid,
                "x-device-os" to DeviceInfo.OS,
                "x-device-model" to DeviceInfo.model,
            )
        }.getOrDefault(emptyMap())

        return try {
            val request = client.newRequest()
            request.setUserAgent(effectiveUserAgent)
            deviceHeaders.forEach { (k, v) -> request.setHeader(k, v) }
            request.setURL(url)
            val response = request.execute()
            response.content.unwrap
        } catch (e: Exception) {
            // Системный DNS у провайдера может не резолвить домен панели
            // («no such host») — пробуем обходные пути: DoH по IP и локальный
            // прокси запущенного VPN.
            try {
                RobustFetch.getString(url, effectiveUserAgent, deviceHeaders)
            } catch (_: Exception) {
                throw e
            }
        }
    }

    override fun close() {
        client.close()
    }
}
