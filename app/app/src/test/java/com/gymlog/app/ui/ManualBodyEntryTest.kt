package com.gymlog.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.health.connect.client.records.*
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.stubs.stub
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ManualBodyEntryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun weightAndFatRoundTripThroughHealthConnectWithoutExportingMuscleAsLeanMass() = runBlocking {
        val fake = FakeHealthConnectClient()
        val gateway = HealthConnectGateway(context, fake)
        val entry = ManualBodyEntry(80.0, 20.0, 40.0, 1700000000000, "test-entry")
        val records = manualBodyHealthRecords(entry)
        assertEquals(2, records.size); assertTrue(records.none { it is LeanBodyMassRecord })
        assertEquals(setOf("android.permission.health.WRITE_WEIGHT", "android.permission.health.WRITE_BODY_FAT"), gateway.writePermissions(entry))
        gateway.writeBodyData(entry); gateway.writeBodyData(entry)
        val data = gateway.readBodyData()
        assertEquals(1, data.measurements.count { it.metric == BodyMetric.WEIGHT })
        assertEquals(80.0, data.measurements.single { it.metric == BodyMetric.WEIGHT }.value, 0.0)
        assertEquals(20.0, data.measurements.single { it.metric == BodyMetric.FAT_PERCENTAGE }.value, 0.0)
        assertEquals(16.0, data.measurements.single { it.metric == BodyMetric.FAT_MASS }.value, 0.0)
    }
    @Test fun deniedWritesAreNotReportedAsSuccess() = runBlocking {
        val fake = FakeHealthConnectClient()
        fake.overrides.insertRecords = stub { throw SecurityException("Permission revoked") }
        try { HealthConnectGateway(context, fake).writeBodyData(ManualBodyEntry(80.0)); fail("Write appeared to succeed") }
        catch (_: SecurityException) { }
    }
    @Test fun localPercentagesPersistAndOwnHealthConnectRecordsDoNotDuplicate() {
        val entry = ManualBodyEntry(80.0, 20.0, 40.0, 1700000000000, "local")
        ManualBodyStore(context).save(entry); ManualBodyStore(context).save(entry)
        val local = ManualBodyStore(context).merge(BodyDataSnapshot())
        assertEquals(5, local.measurements.size)
        assertEquals(32.0, local.measurements.single { it.metric == BodyMetric.MUSCLE_MASS }.value, 0.0)
        assertEquals(5, ManualBodyStore(context).merge(local).measurements.size)
    }
    @Test fun manualMeasurementsSurviveFullBackupWithoutWritingHealthConnect() {
        val entry = ManualBodyEntry(80.0, 20.0, 40.0, 1700000000000, "backup", false)
        ManualBodyStore(context).save(entry)
        val repo = com.gymlog.app.data.GymRepository(context)
        val file = repo.exportJson()
        context.getSharedPreferences("manual_body_entries", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(ManualBodyStore(context).merge(BodyDataSnapshot()).measurements.isEmpty())
        repo.importJson(file)
        val restored = ManualBodyStore(context).merge(BodyDataSnapshot())
        assertEquals(5, restored.measurements.size)
        assertEquals(40.0, restored.measurements.single { it.metric == BodyMetric.MUSCLE_PERCENTAGE }.value, 0.0)
    }
    @Test fun invalidNumbersAndImpossiblePercentageTotalsAreRejected() {
        listOf<() -> ManualBodyEntry>({ ManualBodyEntry(0.0) }, { ManualBodyEntry(Double.NaN) },
            { ManualBodyEntry(80.0, 101.0) }, { ManualBodyEntry(80.0, 70.0, 40.0) }).forEach { make ->
            try { make(); fail("Invalid measurement accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
