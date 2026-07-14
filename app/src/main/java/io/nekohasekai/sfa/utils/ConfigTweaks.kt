package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.Settings
import org.json.JSONArray
import org.json.JSONObject

// Порт apply_user_settings из десктоп-клиента (core.rs): фрагментация TLS,
// мультиплексор и маршрутизация по списку сайтов. Применяется к конфигу
// на лету при каждом старте/перезагрузке сервиса — сам профиль не меняем.
// Ошибки не критичны: при неудаче возвращаем конфиг как есть.
object ConfigTweaks {
    private val PROXY_TYPES = setOf("vless", "vmess", "trojan", "shadowsocks", "shadowtls", "hysteria2", "tuic")

    // QUIC-протоколы — TLS-фрагментация не применима
    private val QUIC_TYPES = setOf("hysteria2", "tuic")

    // Мультиплексор поддерживают только эти протоколы
    private val MUX_TYPES = setOf("vless", "vmess", "trojan", "shadowsocks")

    fun apply(content: String): String = runCatching {
        val cfg = JSONObject(content)
        applyTo(cfg)
        cfg.toString()
    }.getOrDefault(content)

    private fun applyTo(cfg: JSONObject) {
        val recordFragment = Settings.viphoonTlsRecordFragment
        val fragment = Settings.viphoonTlsFragment
        val muxEnabled = Settings.viphoonMuxEnabled
        val muxStreams = Settings.viphoonMuxMaxStreams.coerceAtLeast(1)
        val routeMode = Settings.viphoonRouteMode
        val siteList = Settings.viphoonSiteList.filter { it.isNotBlank() }.take(Settings.MAX_SITE_LIST)
        val appList = Settings.viphoonAppList.filter { it.isNotBlank() }.take(Settings.MAX_APP_LIST)

        val outbounds = cfg.optJSONArray("outbounds") ?: return

        for (i in 0 until outbounds.length()) {
            val o = outbounds.optJSONObject(i) ?: continue
            val type = o.optString("type")
            if (type !in PROXY_TYPES) continue

            // TLS-фрагментация (sing-box >= 1.12), только для TCP-протоколов
            if ((fragment || recordFragment) && type !in QUIC_TYPES) {
                val tls = o.optJSONObject("tls")
                if (tls != null && tls.optBoolean("enabled")) {
                    if (recordFragment) tls.put("record_fragment", true)
                    if (fragment) tls.put("fragment", true)
                }
            }

            // Мультиплексор (нужна поддержка на сервере)
            if (muxEnabled && type in MUX_TYPES) {
                o.put(
                    "multiplex",
                    JSONObject()
                        .put("enabled", true)
                        .put("protocol", "h2mux")
                        .put("max_streams", muxStreams),
                )
            }
        }

        // Маршрутизация по спискам сайтов и приложений
        applyRouting(cfg, outbounds, routeMode, siteList, appList)

        // Anti-loop для mihomo-моста (xhttp): трафик самого приложения —
        // в т.ч. дочернего процесса mihomo — идёт мимо TUN самым первым
        // правилом, иначе исходящие соединения моста зациклятся через
        // собственный туннель.
        if (MihomoBridge.hasBridge(cfg)) {
            val directTag = findTagByType(outbounds, "direct")
            val rulesArr = cfg.optJSONObject("route")?.optJSONArray("rules")
            if (directTag != null && rulesArr != null) {
                insertFirst(
                    rulesArr,
                    JSONObject()
                        .put("package_name", JSONArray().put(Application.application.packageName))
                        .put("outbound", directTag),
                )
            }
        }
    }

    private fun applyRouting(
        cfg: JSONObject,
        outbounds: JSONArray,
        routeMode: String,
        siteList: List<String>,
        appList: List<String>,
    ) {
        if (routeMode == Settings.ROUTE_MODE_ALL || (siteList.isEmpty() && appList.isEmpty())) return
        val direct = findTagByType(outbounds, "direct") ?: return
        val selector = findTagByType(outbounds, "selector") ?: return
        val route = cfg.optJSONObject("route") ?: return
        val rules = route.optJSONArray("rules") ?: return

        // Куда направить трафик из списков: exclude → напрямую, only → через VPN
        val target = when (routeMode) {
            Settings.ROUTE_MODE_EXCLUDE -> direct
            Settings.ROUTE_MODE_ONLY -> selector
            else -> return
        }

        // Приложения (package_name) и сайты (domain_suffix) — отдельными правилами
        if (appList.isNotEmpty()) {
            val packages = JSONArray()
            for (p in appList) packages.put(p)
            insertFirst(rules, JSONObject().put("package_name", packages).put("outbound", target))
        }
        if (siteList.isNotEmpty()) {
            val suffixes = JSONArray()
            for (s in siteList) suffixes.put(s)
            insertFirst(rules, JSONObject().put("domain_suffix", suffixes).put("outbound", target))
        }
        // В режиме "only" весь остальной трафик идёт напрямую
        if (routeMode == Settings.ROUTE_MODE_ONLY) {
            route.put("final", direct)
        }
    }

    private fun findTagByType(outbounds: JSONArray, type: String): String? {
        for (i in 0 until outbounds.length()) {
            val o = outbounds.optJSONObject(i) ?: continue
            if (o.optString("type") == type) {
                return o.optString("tag").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun insertFirst(rules: JSONArray, rule: JSONObject) {
        for (i in rules.length() downTo 1) {
            rules.put(i, rules.get(i - 1))
        }
        rules.put(0, rule)
    }

    // Нормализация домена как в десктопе: без схемы, пути, порта и www.
    fun normalizeDomain(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        s.indexOf("://").takeIf { it >= 0 }?.let { s = s.substring(it + 3) }
        s.indexOf('/').takeIf { it >= 0 }?.let { s = s.substring(0, it) }
        s.indexOf(':').takeIf { it >= 0 }?.let { s = s.substring(0, it) }
        s = s.removePrefix("www.").trim()
        if (s.isEmpty() || !s.contains('.')) return null
        return s
    }
}
