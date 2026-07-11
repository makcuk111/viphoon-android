package io.nekohasekai.sfa.utils

import android.net.Uri

// Нормализация ссылки подписки ViPhooN/Remnawave к https-URL вида …/singbox.
// Принимает: viphoon://add/<url>, viphoon://import/<url>, viphoon://<host>/<path>,
// голый host, http(s)-ссылку. Payload может быть percent-encoded.
object ViphoonLinks {
    fun normalize(raw: String): String {
        var rest = raw.trim().removePrefix("viphoon://")
        rest = rest.removePrefix("add/").removePrefix("import/")
        val decoded = Uri.decode(rest)
        var url = if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
            decoded
        } else {
            "https://$decoded"
        }
        url = url.trimEnd('/')
        if (!url.endsWith("/singbox")) {
            url += "/singbox"
        }
        return url
    }
}
