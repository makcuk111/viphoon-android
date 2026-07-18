package io.nekohasekai.sfa.update

import android.util.Log

// Пользователю нельзя показывать сырое сообщение исключения: у сетевых ошибок
// оно содержит полный подписанный URL github-ассета на пару тысяч символов
// («Get "https://release-assets.githubusercontent.com/…": read»).
fun friendlyUpdateError(e: Throwable): String {
    Log.e("Update", "update error", e)
    val raw = (e.message ?: "").lowercase()
    val reason = when {
        raw.contains("timeout") || raw.contains("deadline") ->
            "сервер не ответил вовремя"
        raw.contains("no address") || raw.contains("unknown host") || raw.contains("lookup") ->
            "не удалось разрешить адрес сервера"
        raw.contains("connection refused") || raw.contains("connect:") ->
            "не удалось подключиться к серверу"
        raw.contains("read") || raw.contains("reset") || raw.contains("broken pipe") || raw.contains("eof") ->
            "соединение оборвалось во время загрузки"
        raw.contains("space") || raw.contains("enospc") ->
            "недостаточно места на устройстве"
        raw.contains("404") || raw.contains("not found") ->
            "файл обновления не найден"
        raw.contains("empty file") ->
            "файл скачался пустым"
        else -> null
    }
    return buildString {
        append("Не удалось скачать обновление")
        if (reason != null) {
            append(": ")
            append(reason)
        }
        append(".\n\nПроверьте подключение к интернету и попробуйте ещё раз. ")
        append("Если включён VPN — попробуйте с ним и без него.")
    }
}
