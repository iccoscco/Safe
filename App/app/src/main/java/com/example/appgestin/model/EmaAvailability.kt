package com.example.appgestin.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun parseApiTime(value: String): Date? = runCatching {
    val normalized = value.replace(Regex("\\.\\d+"), "").replace("Z", "+00:00")
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(normalized)
}.getOrNull()

fun EmaPrompt.isAvailable(at: Date = Date()): Boolean {
    if (instrument != "EMA_DAILY") return false
    val starts = parseApiTime(scheduledAt) ?: return false
    val ends = parseApiTime(expiresAt) ?: return false
    return !at.before(starts) && at.before(ends)
}
