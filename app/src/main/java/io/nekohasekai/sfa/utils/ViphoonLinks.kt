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

    // Похож ли текст из буфера на ссылку/конфиг подписки (для «умного импорта»).
    fun looksLikeLink(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty() || s.length > 4096 || s.contains(' ') || s.contains('\n')) return false
        if (s.startsWith("viphoon://")) return true
        if (s.startsWith("http://") || s.startsWith("https://")) return true
        return Regex("^(vless|vmess|trojan|ss|tuic|hysteria2?|hy2)://", RegexOption.IGNORE_CASE)
            .containsMatchIn(s)
    }

    // Своя подписка ViPhooN — её можно импортировать автоматически.
    fun isViphoonSub(raw: String): Boolean {
        val s = raw.trim()
        if (s.startsWith("viphoon://")) return true
        return Regex("^https?://([^/]*\\.)?viphoon\\.(su|app)(/|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(s)
    }
}
