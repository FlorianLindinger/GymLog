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
            if (entry.areas.isEmpty()) {
                assertTrue("Manual profiles need a sourced model: ${entry.title}", ManufacturerEquipment.machines.any { it.sourceUrl == entry.sourceUrl && it.manual })
            } else assertTrue("No primary region: ${entry.title}", entry.areas.any { it.strength == Strength.FULL })
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
    fun numberedGymEquipmentAndManufacturerLabelsFindBaseExercises() {
        listOf("bauchmaschine 2", "Bauchmaschine2", "FitX Bauchmaschine #2", "Technogym Selection 900 Bauchmaschine 2", "Technogym Selection900 Bauchmaschine2").forEach { query ->
            val match = ExerciseCatalog.search(query).first()
            assertEquals(query, "Abdominal Crunch Machine", match.baseExerciseName ?: match.englishName)
        }
        mapOf(
            "Technogym Selection 900 Total Abdominal" to "Total Abdominal Machine",
            "Technogym Vertical Traction 2" to "Vertical Traction Machine",
            "Technogym Arm Curl" to "Biceps Curl Machine",
            "Technogym Arm Extension" to "Triceps Extension Machine",
            "Technogym Pectoral" to "Pec Deck",
            "Technogym Delts Machine" to "Lateral Raise Machine",
            "Technogym Lower Back" to "Back Extension Machine",
            "Technogym Pulley" to "Seated Cable Row",
            "Hammer Strength Iso Lateral Row 2" to "Chest-supported Row Machine",
            "Bauchrotation 2" to "Torso Rotation Machine",
        ).forEach { (query, expected) ->
            val match = ExerciseCatalog.search(query).first()
            assertEquals(query, expected, match.baseExerciseName ?: match.englishName)
        }
    }

    @Test
    fun spellingSpacingAndTranspositionsPreserveUsefulMatches() {
        listOf("Bauchmaschne 2", "Bauchmaschien 2", "Bauhmaschne 2", "Bauch Maschine 2", "Bauch-Maschine 2").forEach { query ->
            assertEquals(query, "Abdominal Crunch Machine", ExerciseCatalog.search(query).first().englishName)
        }
        assertEquals("Bench Press", ExerciseCatalog.search("Benchpress").first().englishName)
        assertEquals("Lat Pulldown", ExerciseCatalog.search("Latzgu").first().englishName)
        assertEquals("Reverse Pec Deck", ExerciseCatalog.search("Butterfly reverse 2").first().englishName)
        assertTrue(ExerciseCatalog.search("Bauchmaschine 2", ExerciseEquipment.CARDIO).isEmpty())
        listOf("2", "FitX", "no such equipment 2").forEach { query ->
            assertTrue(query, ExerciseCatalog.search(query).isEmpty())
        }
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
            assertEquals(entry.trackingModeOverride ?: if (entry.equipment == ExerciseEquipment.CARDIO) TrackingMode.STRENGTH else TrackingMode.WEIGHT, entry.trackingMode)
        }
    }

    @Test
    fun allIntensityValuesRoundTripIncludingExistingProfiles() {
        Strength.entries.forEach { assertEquals(it, Strength.fromStorage(it.value)) }
        assertEquals(Strength.HALF, Strength.fromStorage(0.5))
        assertEquals(Strength.FULL, Strength.fromStorage(1.0))
        assertEquals(Strength.LIGHT, Strength.fromStorage(0.25))
    }

    @Test
    fun manufacturerModelsHaveSourcesProfilesAndSeparateMultiUseChoices() {
        val entries = ExerciseCatalog.entries
        assertTrue(ManufacturerEquipment.machines.count { it.manufacturer == "Technogym" } >= 170)
        assertTrue(ManufacturerEquipment.machines.count { it.manufacturer == "Hammer Strength" } >= 100)
        ManufacturerEquipment.machines.forEach { model ->
            val variants = entries.filter { it.sourceUrl == model.sourceUrl }
            assertEquals(model.name, if (model.manual) 1 else model.movements.size, variants.size)
            assertTrue(variants.all { it.manufacturer == model.manufacturer })
            assertTrue(model.sourceUrl.startsWith("https://www.technogym.com/") || model.sourceUrl.startsWith("https://www.lifefitness.com/"))
            if (!model.manual) assertEquals(model.movements.toSet(), variants.map { it.baseExerciseName }.toSet())
        }
        val pulley = ExerciseCatalog.search("Hammer Strength Dual Adjustable Pulley", limit = Int.MAX_VALUE)
            .filter { it.sourceUrl?.endsWith("/hammer-strength-dual-adjustable-pulley") == true }
        assertTrue(pulley.any { it.baseExerciseName == "Cable Chest Press" })
        assertTrue(pulley.any { it.baseExerciseName == "Seated Cable Row" })
        assertFalse(pulley.first { it.baseExerciseName == "Cable Chest Press" }.areas == pulley.first { it.baseExerciseName == "Seated Cable Row" }.areas)
    }

    @Test
    fun manufacturerAndProductLineSearchesBrowseOnlyMatchingModels() {
        listOf("Technogym", "Hammer Strength", "Technogym Selection 900").forEach { query ->
            val results = ExerciseCatalog.search(query, limit = Int.MAX_VALUE)
            assertTrue(query, results.isNotEmpty())
            assertTrue(results.all { it.manufacturer == if (query.startsWith("Hammer")) "Hammer Strength" else "Technogym" })
            if (query.endsWith("900")) assertTrue(results.all { it.englishName.contains("Selection 900") })
        }
        mapOf("Technogym Artis Arm Curl" to "Biceps Curl Machine",
            "Technogym Pure Strength Standing Abductor" to "Hip Abduction Machine",
            "Technogym Skillup" to "Ski Ergometer", "Hammer Strength HD SPARC" to "Lower-body Arc Trainer",
            "Hammer Strength MTS Iso Lateral Row" to "Chest-supported Row Machine").forEach { (query, expected) ->
            assertEquals(query, expected, ExerciseCatalog.search(query).first().baseExerciseName)
        }
    }

    @Test
    fun stepmasterVariantsFindCardioAndManualMachinesDoNotInventRegions() {
        listOf("Stepmaster", "Step Master", "Stairmaster", "Stepmaster 2", "Technogym Excite Climb").forEach { query ->
            val result = ExerciseCatalog.search(query).first()
            assertEquals(query, "Stair Climber", result.baseExerciseName ?: result.englishName)
            assertEquals(TrackingMode.STRENGTH, result.trackingMode)
            assertTrue(result.areas.any { it.area == BodyArea.GLUTES && it.strength == Strength.FULL })
        }
        val stretch = ExerciseCatalog.search("Technogym Flexability Anterior").first()
        assertTrue(stretch.areas.isEmpty())
        assertEquals(TrackingMode.DURATION, stretch.trackingMode)
        assertTrue(ExerciseCatalog.search("Technogym Excite Climb 2").first().sourceUrl!!.contains("excite-climb_"))
        assertTrue(ExerciseCatalog.search("Technogym Artis Climb").first().sourceUrl!!.contains("artis-climb_"))
    }
}
