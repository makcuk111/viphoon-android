package io.nekohasekai.sfa.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

// Remnawave вырезает Hysteria2 и XHTTP из sing-box-подписки, хотя ядро
// (или mihomo-мост) их умеет. Дотягиваем Happ/xray-json формат подписки:
//  - hysteria-ноды конвертируем в нативные sing-box hysteria2-аутбаунды;
//  - xhttp-ноды поднимаем через mihomo-мост (sing-box такой транспорт не
//    умеет): пишем конфиг mihomo с локальными SOCKS, а в sing-box
//    подмешиваем socks-аутбаунды на эти порты.
// Порт логики десктоп-клиента (core.rs). Ошибки не критичны — при
// неудаче возвращаем исходный конфиг без изменений.
object SubscriptionEnhancer {
    private const val HAPP_USER_AGENT = "Happ/2.0.0"

    // Сколько ждём Happ-подписку СВЕРХ времени основного запроса. Hysteria2 —
    // необязательное дополнение, из-за него добавление подписки тормозить
    // не должно.
    private const val HAPP_EXTRA_WAIT_MS = 8_000L

    // Скачать sing-box-конфиг и подмешать Hysteria2-ноды из Happ-подписки.
    // Оба запроса идут параллельно — раньше они шли последовательно и
    // добавление подписки занимало двойное время.
    fun fetchAndEnhance(remoteURL: String): String = runBlocking {
        val happDeferred = async(Dispatchers.IO) {
            runCatching {
                HTTPClient().use { it.getString(baseUrl(remoteURL), HAPP_USER_AGENT) }
            }.getOrNull()
        }
        val content = HTTPClient().use { it.getString(remoteURL) }
        val happText = withTimeoutOrNull(HAPP_EXTRA_WAIT_MS) { happDeferred.await() }
        if (happText == null) content else enhanceWith(content, happText)
    }

    private fun enhanceWith(singboxConfig: String, happText: String): String = runCatching {
        var result = singboxConfig
        val hysteria = parseHysteria(happText)
        if (hysteria.isNotEmpty()) {
            result = inject(result, hysteria)
        }
        result = applyXhttp(result, parseXhttp(happText))
        result
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

    // ───────────────────────── XHTTP через mihomo ─────────────────────────

    // Ищет в Happ/xray-json ноды с транспортом xhttp/splithttp и конвертирует
    // их в mihomo-прокси (mihomo поддерживает xhttp только для VLESS).
    private fun parseXhttp(happText: String): List<Pair<String, JSONObject>> {
        val configs = runCatching { JSONArray(happText) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<String, JSONObject>>()
        val seenNames = HashSet<String>()
        val seenNodes = HashSet<String>()

        for (i in 0 until configs.length()) {
            val cfg = configs.optJSONObject(i) ?: continue
            val name = cfg.optString("remarks", "XHTTP").ifBlank { "XHTTP" }
            val obs = cfg.optJSONArray("outbounds") ?: continue
            for (j in 0 until obs.length()) {
                val o = obs.optJSONObject(j) ?: continue
                val stream = o.optJSONObject("streamSettings") ?: continue
                val net = stream.optString("network")
                if (net != "xhttp" && net != "splithttp") continue
                if (o.optString("protocol") != "vless") continue

                val vnext = o.optJSONObject("settings")?.optJSONArray("vnext")?.optJSONObject(0) ?: continue
                val address = vnext.optString("address").orEmptyOrNull() ?: continue
                val port = vnext.optInt("port", 0)
                if (port == 0) continue
                val user = vnext.optJSONArray("users")?.optJSONObject(0) ?: continue
                val uuid = user.optString("id").orEmptyOrNull() ?: continue

                val xs = stream.optJSONObject("xhttpSettings") ?: stream.optJSONObject("splithttpSettings")
                val dedup = "$address:$port:${xs?.optString("path").orEmpty()}"
                if (!seenNodes.add(dedup)) continue
                var unique = name
                var k = 2
                while (!seenNames.add(unique)) unique = "$name #${k++}"

                val proxy = JSONObject()
                    .put("name", unique)
                    .put("type", "vless")
                    .put("server", address)
                    .put("port", port)
                    .put("uuid", uuid)
                    .put("udp", true)
                    .put("network", "xhttp")
                user.optString("flow").orEmptyOrNull()?.let { proxy.put("flow", it) }

                when (stream.optString("security")) {
                    "tls" -> {
                        proxy.put("tls", true)
                        val tls = stream.optJSONObject("tlsSettings")
                        tls?.optString("serverName").orEmptyOrNull()?.let { proxy.put("servername", it) }
                        tls?.optJSONArray("alpn")?.let { proxy.put("alpn", it) }
                        tls?.optString("fingerprint").orEmptyOrNull()?.let { proxy.put("client-fingerprint", it) }
                        if (tls?.optBoolean("allowInsecure") == true) proxy.put("skip-cert-verify", true)
                    }
                    "reality" -> {
                        proxy.put("tls", true)
                        val rs = stream.optJSONObject("realitySettings")
                        rs?.optString("serverName").orEmptyOrNull()?.let { proxy.put("servername", it) }
                        rs?.optString("fingerprint").orEmptyOrNull()?.let { proxy.put("client-fingerprint", it) }
                        val ro = JSONObject()
                        rs?.optString("publicKey").orEmptyOrNull()?.let { ro.put("public-key", it) }
                        rs?.optString("shortId")?.let { ro.put("short-id", it) }
                        proxy.put("reality-opts", ro)
                    }
                }

                val xOpts = JSONObject()
                xs?.optString("path").orEmptyOrNull()?.let { xOpts.put("path", it) }
                xs?.optString("host").orEmptyOrNull()?.let { xOpts.put("host", it) }
                xs?.optString("mode").orEmptyOrNull()?.takeIf { it != "auto" }?.let { xOpts.put("mode", it) }
                proxy.put("xhttp-opts", xOpts)

                out.add(unique to proxy)
            }
        }
        return out
    }

    // Пишет конфиг mihomo-моста и подмешивает в sing-box socks-аутбаунды на
    // его локальные порты (одна нода = один порт), плюс теги в selector/urltest.
    private fun applyXhttp(singboxConfig: String, nodes: List<Pair<String, JSONObject>>): String {
        if (nodes.isEmpty()) {
            MihomoBridge.clearConfig()
            return singboxConfig
        }
        if (!MihomoBridge.isAvailable()) return singboxConfig
        return runCatching {
            val cfg = JSONObject(singboxConfig)
            val obs = cfg.optJSONArray("outbounds") ?: return singboxConfig
            val tags = nodes.map { it.first }

            for (i in 0 until obs.length()) {
                val grp = obs.optJSONObject(i) ?: continue
                val type = grp.optString("type")
                if (type != "selector" && type != "urltest") continue
                val list = grp.optJSONArray("outbounds") ?: JSONArray().also { grp.put("outbounds", it) }
                for (t in tags) list.put(t)
            }
            nodes.forEachIndexed { i, (tag, _) ->
                obs.put(
                    JSONObject()
                        .put("type", "socks")
                        .put("tag", tag)
                        .put("server", "127.0.0.1")
                        .put("server_port", MihomoBridge.SOCKS_BASE + i)
                        .put("version", "5"),
                )
            }

            MihomoBridge.writeConfig(MihomoBridge.buildConfig(nodes.map { it.second }))
            cfg.toString()
        }.getOrDefault(singboxConfig)
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
