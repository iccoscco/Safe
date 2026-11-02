package com.example.appgestin.model

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Reads only explicitly granted health data; never reads GPS or app names. */
class HealthConnectCollector(private val context: Context) {
    companion object {
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun collectLastDay(): JSONObject {
        check(isAvailable()) { "Health Connect no está disponible o necesita actualizarse." }
        val client = HealthConnectClient.getOrCreate(context)
        check(client.permissionController.getGrantedPermissions().containsAll(permissions)) {
            "Autoriza pasos, sueño y frecuencia cardiaca en Health Connect para sincronizar."
        }

        val now = Instant.now()
        val end = Instant.ofEpochSecond(now.epochSecond / 1800 * 1800)
        val start = end.minus(Duration.ofDays(1))
        val observations = JSONArray()
        val buckets = client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, HeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Duration.ofMinutes(30)
            )
        )
        buckets.forEach { bucket ->
            val steps = bucket.result[StepsRecord.COUNT_TOTAL]
            if (steps != null && steps in 0L..8000L) {
                observations.put(observation("steps", bucket.startTime, bucket.endTime, steps))
            }
            val bpm = bucket.result[HeartRateRecord.BPM_AVG]
            if (bpm != null && bpm in 25L..230L) {
                observations.put(observation("heart_rate_mean", bucket.startTime,
                    bucket.endTime, bpm))
            }
        }

        // A fixed overnight interval matches the backend's 1–18 h "night" window.
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nightEndDate = if (ZonedDateTime.now(zone).hour >= 12) today else today.minusDays(1)
        val nightStart = nightEndDate.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val nightEnd = nightEndDate.atTime(12, 0).atZone(zone).toInstant()
        if (!nightEnd.isAfter(now)) {
            val sleep = client.aggregate(AggregateRequest(
                metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(nightStart, nightEnd)
            ))[SleepSessionRecord.SLEEP_DURATION_TOTAL]
            val minutes = sleep?.toMinutes()
            if (minutes != null && minutes in 1L..1080L) {
                observations.put(observation("sleep_minutes", nightStart, nightEnd, minutes))
            }
        }

        return JSONObject().apply {
            put("batch_id", UUID.randomUUID().toString())
            put("sent_at", timestampNow())
            put("observations", observations)
        }
    }

    private fun observation(metric: String, start: Instant, end: Instant, value: Number) =
        JSONObject().apply {
            put("metric", metric)
            put("source", "health_connect")
            put("window_start", start.toString())
            put("window_end", end.toString())
            put("value", value)
        }
}
