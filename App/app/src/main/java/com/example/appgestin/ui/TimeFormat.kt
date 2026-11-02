package com.example.appgestin.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun displayTime(value: String): String = runCatching {
    val normalized = value.replace(Regex("\\.\\d+"), "").replace("Z", "+00:00")
    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(normalized)
    SimpleDateFormat("EEE d MMM · h:mm a", Locale.Builder().setLanguage("es")
        .setRegion("PE").build()).format(date ?: Date())
}.getOrElse { value.take(16).replace('T', ' ') }
