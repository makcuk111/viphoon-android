package io.nekohasekai.sfa.utils

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

// Устойчивое скачивание подписки. DNS у российских провайдеров нередко
// не резолвит/блокирует домен панели («no such host»), поэтому пробуем
// несколько путей по очереди:
//   1) обычный HTTPS через системный резолвер Android;
//   2) резолв через DoH (8.8.8.8 / 1.1.1.1 доступны по IP) + TLS с нужным SNI;
//   3) через локальный прокси запущенного VPN (127.0.0.1:2412).
object RobustFetch {
    private const val TIMEOUT_MS = 15_000
    private const val LOCAL_PROXY_PORT = 2412
    private const val MAX_REDIRECTS = 4

    data class Response(val body: String, val headers: Map<String, String>)

    fun getString(url: String, userAgent: String, headers: Map<String, String> = emptyMap()): String =
        get(url, userAgent, headers).body

    fun get(url: String, userAgent: String, headers: Map<String, String> = emptyMap()): Response {
        val errors = ArrayList<Throwable>()

        runCatching { direct(url, userAgent, headers, proxy = null) }
            .onSuccess { return it }
            .onFailure { errors.add(it) }

        runCatching { viaDoh(url, userAgent, headers, 0) }
            .onSuccess { return it }
            .onFailure { errors.add(it) }

        runCatching {
            direct(url, userAgent, headers, Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", LOCAL_PROXY_PORT)))
        }
            .onSuccess { return it }
            .onFailure { errors.add(it) }

        throw errors.first()
    }

    private fun direct(url: String, userAgent: String, headers: Map<String, String>, proxy: Proxy?): Response {
        val conn = (if (proxy != null) URL(url).openConnection(proxy) else URL(url).openConnection()) as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", userAgent)
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code for $url")
            val body = conn.inputStream.use { it.readBytesCompat().toString(Charsets.UTF_8) }
            val responseHeaders = HashMap<String, String>()
            conn.headerFields.forEach { (k, v) ->
                if (k != null && v != null && v.isNotEmpty()) responseHeaders[k.lowercase()] = v.first()
            }
            return Response(body, responseHeaders)
        } finally {
            conn.disconnect()
        }
    }

    // ---- DoH: резолвим A-запись через 8.8.8.8/1.1.1.1 (их сертификаты валидны по IP) ----

    private fun dohResolve(host: String): String {
        val endpoints = listOf(
            "https://8.8.8.8/resolve?name=$host&type=A" to null,
            "https://1.1.1.1/dns-query?name=$host&type=A" to "application/dns-json",
        )
        for ((endpoint, accept) in endpoints) {
            val ip = runCatching {
                val conn = URL(endpoint).openConnection() as HttpsURLConnection
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                if (accept != null) conn.setRequestProperty("Accept", accept)
                try {
                    val body = conn.inputStream.use { it.readBytesCompat().toString(Charsets.UTF_8) }
                    val answers = JSONObject(body).optJSONArray("Answer") ?: return@runCatching null
                    (0 until answers.length())
                        .mapNotNull { answers.optJSONObject(it) }
                        .firstOrNull { it.optInt("type") == 1 }
                        ?.optString("data")
                        ?.takeIf { it.isNotBlank() }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            if (ip != null) return ip
        }
        throw java.io.IOException("DoH resolve failed for $host")
    }

    // HTTPS-запрос к явному IP с корректным SNI (обходит системный DNS).
    private fun viaDoh(url: String, userAgent: String, headers: Map<String, String>, redirects: Int): Response {
        if (redirects > MAX_REDIRECTS) throw java.io.IOException("Too many redirects")
        val u = URL(url)
        if (u.protocol != "https") return direct(url, userAgent, headers, proxy = null)
        val host = u.host
        val port = if (u.port == -1) 443 else u.port
        val ip = dohResolve(host)

        val raw = Socket()
        raw.connect(InetSocketAddress(ip, port), TIMEOUT_MS)
        raw.soTimeout = TIMEOUT_MS
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        // createSocket(socket, host, ...) выставляет SNI = host на Android
        val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
        ssl.use { socket ->
            socket.startHandshake()
            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (!verifier.verify(host, socket.session)) {
                throw javax.net.ssl.SSLPeerUnverifiedException("Certificate does not match $host")
            }

            val path = u.file.ifBlank { "/" }
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(host).append("\r\n")
                append("User-Agent: ").append(userAgent).append("\r\n")
                headers.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.outputStream.write(request.toByteArray(Charsets.ISO_8859_1))
            socket.outputStream.flush()

            val response = socket.inputStream.readBytesCompat()
            return parseHttpResponse(response) { location ->
                val next = if (location.startsWith("http")) location else "https://$host$location"
                viaDoh(next, userAgent, headers, redirects + 1)
            }
        }
    }

    private fun parseHttpResponse(response: ByteArray, onRedirect: (String) -> Response): Response {
        val headerEnd = indexOfDoubleCrlf(response)
        if (headerEnd < 0) throw java.io.IOException("Malformed HTTP response")
        val head = String(response, 0, headerEnd, Charsets.ISO_8859_1)
        val lines = head.split("\r\n")
        val status = lines.first().split(" ").getOrNull(1)?.toIntOrNull()
            ?: throw java.io.IOException("Malformed HTTP status")
        val headerMap = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val idx = line.indexOf(':')
            if (idx > 0) headerMap[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        if (status in 300..399) {
            val location = headerMap["location"] ?: throw java.io.IOException("Redirect without Location")
            return onRedirect(location)
        }
        if (status !in 200..299) throw java.io.IOException("HTTP $status")

        var body = response.copyOfRange(headerEnd + 4, response.size)
        if (headerMap["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
            body = decodeChunked(body)
        }
        return Response(body.toString(Charsets.UTF_8), headerMap)
    }

    private fun indexOfDoubleCrlf(data: ByteArray): Int {
        for (i in 0..data.size - 4) {
            if (data[i] == '\r'.code.toByte() && data[i + 1] == '\n'.code.toByte() &&
                data[i + 2] == '\r'.code.toByte() && data[i + 3] == '\n'.code.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    private fun decodeChunked(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            var lineEnd = pos
            while (lineEnd < data.size - 1 &&
                !(data[lineEnd] == '\r'.code.toByte() && data[lineEnd + 1] == '\n'.code.toByte())
            ) {
                lineEnd++
            }
            if (lineEnd >= data.size - 1) break
            val sizeLine = String(data, pos, lineEnd - pos, Charsets.ISO_8859_1).trim()
            val size = sizeLine.split(';').first().toIntOrNull(16) ?: break
            if (size == 0) break
            val chunkStart = lineEnd + 2
            val chunkEnd = (chunkStart + size).coerceAtMost(data.size)
            out.write(data, chunkStart, chunkEnd - chunkStart)
            pos = chunkEnd + 2
        }
        return out.toByteArray()
    }

    private fun InputStream.readBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
