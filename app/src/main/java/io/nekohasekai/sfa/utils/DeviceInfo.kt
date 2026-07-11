package io.nekohasekai.sfa.utils

import android.os.Build
import android.provider.Settings
import io.nekohasekai.sfa.Application

// Идентификация устройства для Remnawave (привязка/лимит устройств).
// x-hwid — стабильный id (ANDROID_ID), x-device-os — "android",
// x-device-model — производитель + модель.
object DeviceInfo {
    const val OS = "android"

    val hwid: String by lazy {
        runCatching {
            Settings.Secure.getString(
                Application.application.contentResolver,
                Settings.Secure.ANDROID_ID,
            )
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "viphoon-unknown-device"
    }

    val model: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { "Android" }
}
