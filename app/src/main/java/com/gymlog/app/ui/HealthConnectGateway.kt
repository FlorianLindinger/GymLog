package com.gymlog.app.ui

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.math.abs

class HealthConnectGateway(private val context: Context) {
    companion object {
        val permissions = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
        )
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readBodyData(): BodyDataSnapshot {
        val health = client()
        val start = Instant.now().minusSeconds(10L * 365L * 24L * 60L * 60L)
        val end = Instant.now().plusSeconds(24L * 60L * 60L)
        val filter = TimeRangeFilter.between(start, end)
        val weights = health.readRecords(
            ReadRecordsRequest(WeightRecord::class, filter, ascendingOrder = true, pageSize = 1000),
        ).records
        val fatPercentages = health.readRecords(
            ReadRecordsRequest(BodyFatRecord::class, filter, ascendingOrder = true, pageSize = 1000),
        ).records
        val leanMasses = health.readRecords(
            ReadRecordsRequest(LeanBodyMassRecord::class, filter, ascendingOrder = true, pageSize = 1000),
        ).records

        fun source(record: Record): String = record.metadata.dataOrigin.packageName
        fun nearestWeight(time: Instant): WeightRecord? = weights.minByOrNull {
            abs(it.time.toEpochMilli() - time.toEpochMilli())
        }?.takeIf { abs(it.time.toEpochMilli() - time.toEpochMilli()) <= 7L * 24L * 60L * 60L * 1000L }

        val result = mutableListOf<BodyMeasurement>()
        weights.forEach {
            result += BodyMeasurement(BodyMetric.WEIGHT, it.weight.inKilograms, it.time.toEpochMilli(), source(it))
        }
        fatPercentages.forEach {
            val percentage = it.percentage.value
            result += BodyMeasurement(BodyMetric.FAT_PERCENTAGE, percentage, it.time.toEpochMilli(), source(it))
            nearestWeight(it.time)?.let { weight ->
                result += BodyMeasurement(
                    BodyMetric.FAT_MASS,
                    weight.weight.inKilograms * percentage / 100.0,
                    it.time.toEpochMilli(),
                    source(it),
                )
            }
        }
        leanMasses.forEach {
            val kilograms = it.mass.inKilograms
            result += BodyMeasurement(BodyMetric.MUSCLE_MASS, kilograms, it.time.toEpochMilli(), source(it))
            nearestWeight(it.time)?.takeIf { it.weight.inKilograms > 0.0 }?.let { weight ->
                result += BodyMeasurement(
                    BodyMetric.MUSCLE_PERCENTAGE,
                    kilograms / weight.weight.inKilograms * 100.0,
                    it.time.toEpochMilli(),
                    source(it),
                )
            }
        }
        return BodyDataSnapshot(result.sortedBy { it.measuredAt }, System.currentTimeMillis())
    }
}
