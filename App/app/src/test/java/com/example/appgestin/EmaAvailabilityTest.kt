package com.example.appgestin

import com.example.appgestin.model.EmaPrompt
import com.example.appgestin.model.isAvailable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Date

class EmaAvailabilityTest {
    private val prompt = EmaPrompt(
        id = "test", instrument = "EMA_DAILY",
        scheduledAt = "2026-10-18T10:00:00-05:00",
        expiresAt = "2026-10-18T11:00:00-05:00", status = "scheduled"
    )

    @Test
    fun onlyAvailableWithinItsWindow() {
        assertFalse(prompt.isAvailable(at("2026-10-18T14:59:59Z")))
        assertTrue(prompt.isAvailable(at("2026-10-18T15:30:00Z")))
        assertFalse(prompt.isAvailable(at("2026-10-18T16:00:00Z")))
    }

    private fun at(value: String): Date = Date.from(Instant.parse(value))
}
