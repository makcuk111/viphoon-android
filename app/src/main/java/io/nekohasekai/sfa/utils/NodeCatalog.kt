package io.nekohasekai.sfa.utils

import org.json.JSONObject
import java.io.File

// Работа с нодами как в десктоп-клиенте ViPhooN: определение страны по имени,
// эмодзи-флаг, чистое имя и офлайн-каталог нод из конфига подписки
// (чтобы выбор локации работал и при выключенном VPN).
object NodeCatalog {
    // server/serverPort — адрес сервера для офлайн-проверки пинга (TCP/ICMP
    // без поднятого VPN). Для xhttp-нод (socks-мост на 127.0.0.1) реальный
    // адрес подтягивается из конфига mihomo.
    data class Node(val tag: String, val type: String, val server: String? = null, val serverPort: Int = 0)

    data class Catalog(val selectorTag: String, val nodes: List<Node>)

    // Название страны (рус/англ) -> ISO-код. Перенесено из десктоп-клиента.
    private val NAME_TO_CC = mapOf(
        "эстони" to "ee", "нидерланд" to "nl", "голланд" to "nl", "австри" to "at",
        "испани" to "es", "польш" to "pl", "герман" to "de", "швеци" to "se",
        "финлянд" to "fi", "франци" to "fr", "великобритани" to "gb", "англи" to "gb",
        "сша" to "us", "америк" to "us", "канад" to "ca", "япони" to "jp",
        "сингапур" to "sg", "турци" to "tr", "латви" to "lv", "литв" to "lt",
        "украин" to "ua", "казахст" to "kz", "россий" to "ru", "росси" to "ru",
        "швейцар" to "ch", "норвеги" to "no", "дани" to "dk", "ирланди" to "ie",
        "итали" to "it", "португал" to "pt", "чехи" to "cz", "румыни" to "ro",
        "болгари" to "bg", "венгри" to "hu", "грузи" to "ge", "армени" to "am",
        "гонконг" to "hk", "корея" to "kr", "инди" to "in", "бразили" to "br",
        "австрали" to "au", "оаэ" to "ae", "эмират" to "ae", "израил" to "il",
        "молдов" to "md", "беларус" to "by", "серби" to "rs", "хорвати" to "hr",
        "germany" to "de", "sweden" to "se", "netherlands" to "nl", "finland" to "fi",
        "france" to "fr", "usa" to "us", "united states" to "us", "poland" to "pl",
        "turkey" to "tr", "latvia" to "lv", "estonia" to "ee", "austria" to "at",
        "spain" to "es", "kazakhstan" to "kz", "switzerland" to "ch", "norway" to "no",
    )

    // ISO-код страны из тега ноды: сначала по эмодзи-флагу, затем по названию.
    fun countryCode(tag: String): String? {
        val cps = ArrayList<Int>()
        var i = 0
        while (i < tag.length) {
            val cp = tag.codePointAt(i)
            cps.add(cp)
            i += Character.charCount(cp)
        }
        for (j in 0 until cps.size - 1) {
            if (cps[j] in 0x1F1E6..0x1F1FF && cps[j + 1] in 0x1F1E6..0x1F1FF) {
                return "" + ('a' + (cps[j] - 0x1F1E6)) + ('a' + (cps[j + 1] - 0x1F1E6))
            }
        }
        val low = tag.lowercase()
        for ((k, v) in NAME_TO_CC) {
            if (low.contains(k)) return v
        }
        return null
    }

    fun countryFlag(code: String?): String {
        if (code == null || code.length != 2) return "🌐"
        val cc = code.uppercase()
        return String(Character.toChars(0x1F1E6 + (cc[0] - 'A'))) +
            String(Character.toChars(0x1F1E6 + (cc[1] - 'A')))
    }

    fun flagFor(tag: String): String = countryFlag(countryCode(tag))

    // Чистит имя ноды от эмодзи, разделителей и дублирующего «(HY2)».
    fun cleanName(tag: String): String = tag
        .replace(Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{20E3}]"), "")
        .replace(Regex("\\((?:hy2|hysteria2?)\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[|·]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun isAuto(type: String): Boolean = type.equals("urltest", ignoreCase = true)

    fun isHysteria(type: String): Boolean = type.startsWith("hysteria", ignoreCase = true)

    // Офлайн-каталог: парсит конфиг подписки и возвращает главный селектор
    // и его ноды в исходном порядке (direct/block скрываются).
    fun fromConfigFile(path: String): Catalog? = runCatching {
        val json = JSONObject(File(path).readText())
        val outbounds = json.getJSONArray("outbounds")
        val typeByTag = HashMap<String, String>()
        val endpointByTag = HashMap<String, Pair<String, Int>>()
        var selectorTag: String? = null
        var selectorList: List<String> = emptyList()
        // Реальные адреса xhttp-нод (в sing-box они socks на 127.0.0.1).
        val bridgeEndpoints by lazy { MihomoBridge.proxyEndpoints() }
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.getJSONObject(i)
            val tag = ob.optString("tag")
            val type = ob.optString("type")
            if (tag.isNotEmpty()) {
                typeByTag[tag] = type
                val server = ob.optString("server")
                val port = ob.optInt("server_port", 0)
                if (server == "127.0.0.1" && port >= MihomoBridge.SOCKS_BASE) {
                    bridgeEndpoints[tag]?.let { endpointByTag[tag] = it }
                } else if (server.isNotEmpty() && port > 0) {
                    endpointByTag[tag] = server to port
                }
            }
            if (selectorTag == null && type == "selector") {
                selectorTag = tag
                val arr = ob.optJSONArray("outbounds")
                if (arr != null) {
                    selectorList = (0 until arr.length()).map { arr.getString(it) }
                }
            }
        }
        val mainTag = selectorTag ?: return null
        // Раскрываем urltest внутри селектора, чтобы получить и авто-пункт, и все ноды.
        val seen = LinkedHashSet<String>()
        fun addAll(tags: List<String>) {
            for (t in tags) {
                val type = typeByTag[t] ?: continue
                if (type == "direct" || type == "block" || type == "dns") continue
                seen.add(t)
            }
        }
        addAll(selectorList)
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.getJSONObject(i)
            if (ob.optString("type") == "urltest") {
                val arr = ob.optJSONArray("outbounds") ?: continue
                addAll((0 until arr.length()).map { arr.getString(it) })
            }
        }
        val nodes = seen.map {
            val endpoint = endpointByTag[it]
            Node(it, typeByTag[it] ?: "", endpoint?.first, endpoint?.second ?: 0)
        }
        if (nodes.isEmpty()) return null
        Catalog(mainTag, nodes)
    }.getOrNull()
}
