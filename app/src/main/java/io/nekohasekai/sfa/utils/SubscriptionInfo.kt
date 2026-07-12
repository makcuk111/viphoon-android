package io.nekohasekai.sfa.utils

// Данные подписки Remnawave из заголовка ответа `subscription-userinfo`
// (де-факто стандарт Clash/sing-box: upload/download/total/expire в байтах и
// unix-времени). Тянем best-effort — при ошибке возвращаем null, карточка
// просто не показывает цифры.
data class SubscriptionInfo(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expireEpochSeconds: Long,
) {
    val usedBytes: Long get() = uploadBytes + downloadBytes
    val hasTraffic: Boolean get() = totalBytes > 0
    val hasExpire: Boolean get() = expireEpochSeconds > 0

    val remainingBytes: Long
        get() = (totalBytes - usedBytes).coerceAtLeast(0)

    // Осталось дней до окончания (>= 0), либо null если срок не задан/истёк.
    val remainingDays: Long?
        get() {
            if (!hasExpire) return null
            val nowSec = System.currentTimeMillis() / 1000
            val left = expireEpochSeconds - nowSec
            if (left <= 0) return 0
            return left / 86_400
        }
}

object SubscriptionInfoFetcher {
    private const val HAPP_USER_AGENT = "Happ/2.0.0"

    fun fetch(remoteURL: String): SubscriptionInfo? = runCatching {
        val base = remoteURL.trim().trimEnd('/').removeSuffix("/singbox").trimEnd('/')
        val deviceHeaders = runCatching {
            mapOf(
                "x-hwid" to DeviceInfo.hwid,
                "x-device-os" to DeviceInfo.OS,
                "x-device-model" to DeviceInfo.model,
            )
        }.getOrDefault(emptyMap())
        // RobustFetch сам обходит проблемы с DNS (DoH по IP, локальный прокси VPN)
        val response = RobustFetch.get(base, HAPP_USER_AGENT, deviceHeaders)
        val header = response.headers["subscription-userinfo"] ?: return null
        parse(header)
    }.getOrNull()

    // Формат: "upload=0; download=100; total=1000; expire=1700000000"
    private fun parse(header: String): SubscriptionInfo? {
        val map = HashMap<String, Long>()
        header.split(';').forEach { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim().lowercase()
                val value = kv[1].trim().toLongOrNull()
                if (value != null) map[key] = value
            }
        }
        if (map.isEmpty()) return null
        return SubscriptionInfo(
            uploadBytes = map["upload"] ?: 0,
            downloadBytes = map["download"] ?: 0,
            totalBytes = map["total"] ?: 0,
            expireEpochSeconds = map["expire"] ?: 0,
        )
    }
}
