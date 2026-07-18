package io.nekohasekai.sfa.utils

import android.util.Log
import io.nekohasekai.sfa.Application
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// XHTTP-мост через mihomo (Clash.Meta): sing-box не умеет транспорт
// xhttp/splithttp, поэтому такие ноды поднимает отдельное ядро mihomo
// с локальными SOCKS-инбаундами, а sing-box ходит в них socks-аутбаундами.
// Аналог xray-моста в десктоп-клиенте (core.rs).
//
// Бинарь mihomo упаковывается как «нативная библиотека» libmihomo.so
// (jniLibs, см. .github/workflows/android.yml) — только из nativeLibraryDir
// разрешён запуск исполняемых файлов на Android 10+.
object MihomoBridge {
    private const val TAG = "MihomoBridge"

    // База локальных SOCKS-портов (по порту на xhttp-ноду).
    const val SOCKS_BASE = 24100

    private var process: Process? = null

    private val workDir: File
        get() = File(Application.application.filesDir, "mihomo").also { it.mkdirs() }

    private val configFile: File
        get() = File(workDir, "config.json")

    private fun binary(): File = File(Application.application.applicationInfo.nativeLibraryDir, "libmihomo.so")

    fun isAvailable(): Boolean = binary().exists()

    // Есть ли в sing-box-конфиге socks-аутбаунды на локальные порты моста.
    fun hasBridge(config: String): Boolean =
        runCatching { hasBridge(JSONObject(config)) }.getOrDefault(false)

    fun hasBridge(cfg: JSONObject): Boolean {
        val obs = cfg.optJSONArray("outbounds") ?: return false
        for (i in 0 until obs.length()) {
            val o = obs.optJSONObject(i) ?: continue
            if (o.optString("type") == "socks" &&
                o.optString("server") == "127.0.0.1" &&
                o.optInt("server_port") >= SOCKS_BASE
            ) {
                return true
            }
        }
        return false
    }

    // mihomo читает YAML, а JSON — валидное подмножество YAML,
    // поэтому конфиг можно писать обычным JSONObject.
    fun writeConfig(config: JSONObject) {
        runCatching { configFile.writeText(config.toString(2)) }
            .onFailure { Log.w(TAG, "write config: ${it.message}") }
    }

    fun clearConfig() {
        runCatching { configFile.delete() }
    }

    // Реальные адреса xhttp-нод из конфига моста: в sing-box-конфиге такие
    // ноды выглядят как socks на 127.0.0.1, что бесполезно для проверки
    // пинга. Имя mihomo-прокси совпадает с тегом sing-box-аутбаунда.
    fun proxyEndpoints(): Map<String, Pair<String, Int>> = runCatching {
        val cfg = JSONObject(configFile.readText())
        val proxies = cfg.optJSONArray("proxies") ?: return@runCatching emptyMap<String, Pair<String, Int>>()
        val map = HashMap<String, Pair<String, Int>>()
        for (i in 0 until proxies.length()) {
            val p = proxies.optJSONObject(i) ?: continue
            val name = p.optString("name")
            val server = p.optString("server")
            val port = p.optInt("port", 0)
            if (name.isNotEmpty() && server.isNotEmpty() && port > 0) {
                map[name] = server to port
            }
        }
        map
    }.getOrDefault(emptyMap())

    @Synchronized
    fun start() {
        stop()
        if (!configFile.exists()) {
            Log.w(TAG, "config.json отсутствует — мост не запущен")
            return
        }
        val bin = binary()
        if (!bin.exists()) {
            Log.w(TAG, "libmihomo.so не найден (${bin.path}) — xhttp-ноды работать не будут")
            return
        }
        runCatching {
            val p = ProcessBuilder(
                bin.absolutePath,
                "-d", workDir.absolutePath,
                "-f", configFile.absolutePath,
            ).redirectErrorStream(true).start()
            process = p
            // Дренаж вывода, иначе процесс может встать на переполненном пайпе.
            Thread {
                runCatching {
                    p.inputStream.bufferedReader().forEachLine { Log.d(TAG, it) }
                }
            }.apply { isDaemon = true }.start()
            Log.i(TAG, "mihomo запущен (pid-процесс жив: ${p.isAliveCompat()})")
        }.onFailure {
            process = null
            Log.w(TAG, "запуск mihomo: ${it.message}")
        }
    }

    @Synchronized
    fun stop() {
        process?.let {
            runCatching { it.destroy() }
            Log.i(TAG, "mihomo остановлен")
        }
        process = null
    }

    private fun Process.isAliveCompat(): Boolean = runCatching {
        exitValue()
        false
    }.getOrDefault(true)

    // Строит конфиг mihomo: SOCKS-инбаунд на ноду + vless/xhttp-прокси,
    // маршрутизация IN-NAME → прокси. nodes: (имя, mihomo-прокси).
    fun buildConfig(proxies: List<JSONObject>): JSONObject {
        val listeners = JSONArray()
        val proxiesArr = JSONArray()
        val rules = JSONArray()
        for ((i, proxy) in proxies.withIndex()) {
            val inName = "in-$i"
            listeners.put(
                JSONObject()
                    .put("name", inName)
                    .put("type", "socks")
                    .put("listen", "127.0.0.1")
                    .put("port", SOCKS_BASE + i)
                    .put("udp", true)
                    .put("users", JSONArray()),
            )
            proxiesArr.put(proxy)
            rules.put("IN-NAME,$inName,${proxy.optString("name")}")
        }
        rules.put("MATCH,DIRECT")
        return JSONObject()
            .put("log-level", "warning")
            .put("mode", "rule")
            .put("ipv6", true)
            .put("listeners", listeners)
            .put("proxies", proxiesArr)
            .put("rules", rules)
    }
}
