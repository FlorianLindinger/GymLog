package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    @Test
    fun catalogueHasLargeBilingualCoverageAndValidProfiles() {
        val entries = ExerciseCatalog.entries
        assertTrue(entries.size >= 150)
        assertEquals(entries.size, entries.map { it.englishName }.distinct().size)
        assertEquals(ExerciseEquipment.entries.toSet(), entries.map { it.equipment }.toSet())
        entries.forEach { entry ->
            assertTrue(entry.englishName.isNotBlank() && entry.germanName.isNotBlank())
            assertTrue("No primary region: ${entry.title}", entry.areas.any { it.strength == Strength.FULL })
            assertEquals("Duplicate regions: ${entry.title}", entry.areas.size, entry.areas.map { it.area }.distinct().size)
        }
    }

    @Test
    fun germanEnglishAliasesAndUmlautSpellingsFindTheSameExercise() {
        listOf("Bankdrücken", "bankdruecken", "Bankdrucken", "bench press", "Langhantel Flachbankdrücken").forEach { query ->
            assertEquals(query, "Bench Press", ExerciseCatalog.search(query).first().englishName)
        }
        assertEquals("Chest Press Machine", ExerciseCatalog.search("Brustmaschine").first().englishName)
        assertEquals("Seated Leg Curl", ExerciseCatalog.search("Beinbeuger sitzend").first().englishName)
    }

    @Test
    fun searchSupportsPrefixesWordOrderAndSmallSpellingMistakes() {
        assertTrue(ExerciseCatalog.search("rudern kabel").any { it.englishName == "Seated Cable Row" })
        assertEquals("Lat Pulldown", ExerciseCatalog.search("Lattzug").first().englishName)
        assertTrue(ExerciseCatalog.search("Bulgar").any { it.englishName == "Bulgarian Split Squat" })
    }

    @Test
    fun equipmentFilterAndEmptySearchCanBrowseWholeCatalogue() {
        val bodyweight = ExerciseCatalog.search("", ExerciseEquipment.BODYWEIGHT, Int.MAX_VALUE)
        assertTrue(bodyweight.size >= 20)
        assertTrue(bodyweight.all { it.equipment == ExerciseEquipment.BODYWEIGHT })
        assertEquals(ExerciseCatalog.entries.size, ExerciseCatalog.search("", limit = Int.MAX_VALUE).size)
        assertTrue(ExerciseCatalog.search("xyzunknownexercise").isEmpty())
        assertTrue(ExerciseCatalog.search("", limit = 0).isEmpty())
    }

    @Test
    fun primarySupportingAndLightRegionsAreKeptDistinct() {
        val press = ExerciseCatalog.entries.first { it.englishName == "Bench Press" }
        assertEquals(Strength.FULL, press.areas.first { it.area == BodyArea.CHEST }.strength)
        assertEquals(Strength.HALF, press.areas.first { it.area == BodyArea.TRICEPS }.strength)
        val pull = ExerciseCatalog.entries.first { it.englishName == "Lat Pulldown" }
        assertEquals(Strength.LIGHT, pull.areas.first { it.area == BodyArea.FOREARMS }.strength)
        assertFalse(press.areas.any { it.area == BodyArea.QUADS })
    }

    @Test
    fun cardioUsesLevelAndMinutesWhileResistanceUsesWeight() {
        ExerciseCatalog.entries.forEach { entry ->
            assertEquals(if (entry.equipment == ExerciseEquipment.CARDIO) TrackingMode.STRENGTH else TrackingMode.WEIGHT, entry.trackingMode)
        }
    }

    @Test
    fun allIntensityValuesRoundTripIncludingExistingProfiles() {
        Strength.entries.forEach { assertEquals(it, Strength.fromStorage(it.value)) }
        assertEquals(Strength.HALF, Strength.fromStorage(0.5))
        assertEquals(Strength.FULL, Strength.fromStorage(1.0))
        assertEquals(Strength.LIGHT, Strength.fromStorage(0.25))
    }
}
