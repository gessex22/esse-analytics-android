package com.esseanalytics.android.core.model

// Coincide con los 3 nombres que usa la central (backend/) — "facebook" existe
// como destino de crossposting pero no participa del ciclo normal de publicar.
enum class Platform(val apiValue: String) {
    YOUTUBE("youtube"),
    INSTAGRAM("instagram"),
    TIKTOK("tiktok"),
    FACEBOOK("facebook");

    companion object {
        val publishable = listOf(YOUTUBE, INSTAGRAM, TIKTOK)

        fun fromApiValue(value: String): Platform? = entries.find { it.apiValue == value }
    }
}
