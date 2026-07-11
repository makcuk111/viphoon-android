package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject

// Remnawave вырезает Hysteria2 из sing-box-подписки, хотя ядро их умеет.
// Дотягиваем Happ/xray-json формат подписки, конвертируем hysteria-ноды в
// нативные sing-box hysteria2-аутбаунды и подмешиваем в конфиг + в группы
// selector/urltest. Порт логики десктоп-клиента (core.rs). Ошибки не
// критичны — при неудаче возвращаем исходный конфиг без изменений.
object SubscriptionEnhancer {
    private const val HAPP_USER_AGENT = "Happ/2.0.0"

    // Скачать sing-box-конфиг и подмешать Hysteria2-ноды из Happ-подписки.
    fun fetchAndEnhance(remoteURL: String): String {
        val content = HTTPClient().use { it.getString(remoteURL) }
        return enhance(content, remoteURL)
    }

    fun enhance(singboxConfig: String, remoteURL: String): String = runCatching {
        val base = baseUrl(remoteURL)
        val happText = HTTPClient().use { it.getString(base, HAPP_USER_AGENT) }
        val nodes = parseHysteria(happText)
        if (nodes.isEmpty()) return singboxConfig
        inject(singboxConfig, nodes)
    }.getOrDefault(singboxConfig)

    // Happ-подписка лежит по базовому URL (без /singbox).
    private fun baseUrl(remoteURL: String): String {
        val trimmed = remoteURL.trim().trimEnd('/')
        return trimmed.removeSuffix("/singbox").trimEnd('/')
    }

    private fun parseHysteria(happText: String): List<Pair<String, JSONObject>> {
        val configs = runCatching { JSONArray(happText) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<String, JSONObject>>()
        val seen = HashSet<String>()

        for (i in 0 until configs.length()) {
            val cfg = configs.optJSONObject(i) ?: continue
            val name = cfg.optString("remarks", "Hysteria2").ifBlank { "Hysteria2" }
            val obs = cfg.optJSONArray("outbounds") ?: continue
            for (j in 0 until obs.length()) {
                val o = obs.optJSONObject(j) ?: continue
                if (o.optString("protocol") != "hysteria") continue

                val settings = o.optJSONObject("settings")
                val stream = o.optJSONObject("streamSettings")
                val address = settings?.optString("address").orEmptyOrNull() ?: continue
                val port = settings?.optInt("port", 0) ?: 0
                if (port == 0) continue
                val auth = stream?.optJSONObject("hysteriaSettings")
                    ?.optString("auth").orEmptyOrNull() ?: continue

                val dedup = "$address:$port"
                if (!seen.add(dedup)) continue

                val tls = stream.optJSONObject("tlsSettings")
                val sni = tls?.optString("serverName").orEmptyOrNull()
                val alpn = tls?.optJSONArray("alpn")

                val tlsObj = JSONObject().put("enabled", true)
                if (sni != null) tlsObj.put("server_name", sni)
                if (alpn != null) tlsObj.put("alpn", alpn)

                val outbound = JSONObject()
                    .put("type", "hysteria2")
                    .put("tag", name)
                    .put("server", address)
                    .put("server_port", port)
                    .put("password", auth)
                    .put("tls", tlsObj)

                out.add(name to outbound)
            }
        }
        return out
    }

    private fun inject(singboxConfig: String, nodes: List<Pair<String, JSONObject>>): String {
        val cfg = JSONObject(singboxConfig)
        val obs = cfg.optJSONArray("outbounds") ?: return singboxConfig
        val tags = nodes.map { it.first }

        // 1) добавить теги в группы выбора/авто
        for (i in 0 until obs.length()) {
            val grp = obs.optJSONObject(i) ?: continue
            val type = grp.optString("type")
            if (type != "selector" && type != "urltest") continue
            val list = grp.optJSONArray("outbounds") ?: JSONArray().also { grp.put("outbounds", it) }
            for (t in tags) list.put(t)
        }

        // 2) добавить сами аутбаунды
        for ((_, ob) in nodes) obs.put(ob)

        return cfg.toString()
    }

    private fun String?.orEmptyOrNull(): String? =
        this?.takeIf { it.isNotBlank() }
}
