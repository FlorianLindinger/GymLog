package com.gymlog.app.data

import java.text.Normalizer
import java.util.Locale

internal enum class ExerciseEquipment(val label: String) {
    MACHINE("Machine / Gerät"), CABLE("Cable / Seilzug"), FREE_WEIGHTS("Free weights / Hanteln"),
    BODYWEIGHT("Bodyweight / Eigengewicht"), CARDIO("Cardio / Ausdauer"),
}

internal data class ExerciseTemplate(
    val englishName: String,
    val germanName: String,
    val equipment: ExerciseEquipment,
    val areas: List<AreaAssignment>,
    val aliases: String = "",
) {
    val title: String get() = "$englishName / $germanName"
    val trackingMode: TrackingMode get() = if (equipment == ExerciseEquipment.CARDIO) TrackingMode.STRENGTH else TrackingMode.WEIGHT
}

/** Independently curated starting profiles, not measured muscle-activation percentages. */
internal object ExerciseCatalog {
    val entries: List<ExerciseTemplate> = buildList {
        fun profile(high: String, medium: String = "", low: String = ""): List<AreaAssignment> =
            listOf(high to Strength.FULL, medium to Strength.HALF, low to Strength.LIGHT).flatMap { (names, intensity) ->
                names.split(' ').filter(String::isNotBlank).map { AreaAssignment(BodyArea.valueOf(it), intensity) }
            }
        fun entry(en: String, de: String, equipment: ExerciseEquipment, areas: List<AreaAssignment>, aliases: String = "") {
            add(ExerciseTemplate(en, de, equipment, areas, aliases))
        }
        val machine = ExerciseEquipment.MACHINE
        val cable = ExerciseEquipment.CABLE
        val free = ExerciseEquipment.FREE_WEIGHTS
        val body = ExerciseEquipment.BODYWEIGHT
        val cardio = ExerciseEquipment.CARDIO
        val press = profile("CHEST", "TRICEPS SHOULDERS")
        val inclinePress = profile("CHEST", "SHOULDERS TRICEPS")
        val fly = profile("CHEST", "SHOULDERS")
        val pushup = profile("CHEST", "TRICEPS SHOULDERS", "CORE")
        entry("Chest Press Machine", "Brustpresse", machine, press, "Brustmaschine seated chest press")
        entry("Incline Chest Press Machine", "Schrägbank-Brustpresse", machine, inclinePress, "incline press obere Brust")
        entry("Decline Chest Press Machine", "Negative Brustpresse", machine, press)
        entry("Pec Deck", "Butterfly-Maschine", machine, fly, "butterfly chest fly Brust Butterfly")
        entry("Bench Press", "Bankdrücken", free, press, "Langhantel Flachbankdrücken barbell bench press")
        entry("Dumbbell Bench Press", "Kurzhantel-Bankdrücken", free, press, "KH Bankdrücken dumbbell chest press")
        entry("Incline Bench Press", "Schrägbankdrücken", free, inclinePress, "Langhantel Schrägbank")
        entry("Incline Dumbbell Press", "Kurzhantel-Schrägbankdrücken", free, inclinePress, "KH Schrägbank")
        entry("Decline Bench Press", "Negativbankdrücken", free, press)
        entry("Dumbbell Fly", "Fliegende mit Kurzhanteln", free, fly, "Flys Kurzhantel Butterfly")
        entry("Incline Dumbbell Fly", "Fliegende auf der Schrägbank", free, fly)
        entry("Smith Machine Bench Press", "Bankdrücken an der Multipresse", machine, press, "Smith press")
        entry("Cable Chest Press", "Brustdrücken am Kabelzug", cable, press)
        entry("Cable Crossover", "Fliegende am Kabelzug", cable, fly, "Kabel Butterfly cable fly cross over")
        entry("Low Cable Fly", "Butterfly am tiefen Kabelzug", cable, fly, "low to high fly")
        entry("Push-up", "Liegestütz", body, pushup, "Liegestütze push ups pushups")
        entry("Incline Push-up", "Erhöhter Liegestütz", body, pushup, "Hände erhöht")
        entry("Decline Push-up", "Liegestütz mit erhöhten Füßen", body, pushup)
        entry("Chest Dip", "Dips mit Brustfokus", body, profile("CHEST", "TRICEPS SHOULDERS"), "Brust Dips")
        entry("Assisted Dip", "Unterstützte Dips", machine, profile("TRICEPS CHEST", "SHOULDERS"), "Dipmaschine Dip Assist")

        val pulldown = profile("UPPER_BACK", "BICEPS", "FOREARMS SHOULDERS TRAPS")
        val row = profile("UPPER_BACK", "BICEPS TRAPS", "SHOULDERS FOREARMS")
        val bentRow = profile("UPPER_BACK", "BICEPS TRAPS LOWER_BACK", "CORE FOREARMS SHOULDERS")
        entry("Lat Pulldown", "Latzug", machine, pulldown, "Latziehen Lat Maschine Latissimus Rückenzug")
        entry("Wide-grip Lat Pulldown", "Breiter Latzug", cable, pulldown, "Latzug breit")
        entry("Close-grip Lat Pulldown", "Enger Latzug", cable, pulldown, "Latzug eng neutral grip")
        entry("Underhand Lat Pulldown", "Latzug im Untergriff", cable, pulldown, "reverse grip pulldown")
        entry("Single-arm Lat Pulldown", "Einarmiger Latzug", cable, pulldown)
        entry("Seated Row Machine", "Rudermaschine sitzend", machine, row, "Rücken Rudern Maschine low row horizontal row")
        entry("Chest-supported Row Machine", "Brustgestütztes Rudern an der Maschine", machine, row, "Iso lateral row")
        entry("High Row Machine", "Hohe Rudermaschine", machine, row, "high pull row")
        entry("Seated Cable Row", "Sitzendes Kabelrudern", cable, row, "Rudern am Kabelzug eng breit")
        entry("Single-arm Cable Row", "Einarmiges Kabelrudern", cable, row)
        entry("Standing Cable Row", "Stehendes Kabelrudern", cable, row + profile("", "", "CORE"))
        entry("Barbell Row", "Langhantelrudern", free, bentRow, "vorgebeugtes Rudern bent over row")
        entry("Dumbbell Row", "Kurzhantelrudern", free, row, "einarmiges Rudern single arm row")
        entry("Chest-supported Dumbbell Row", "Brustgestütztes Kurzhantelrudern", free, row, "seal row")
        entry("T-bar Row", "T-Bar-Rudern", free, bentRow, "Landmine Rudern")
        entry("Pendlay Row", "Pendlay-Rudern", free, bentRow)
        entry("Pull-up", "Klimmzug im Obergriff", body, pulldown, "Klimmzüge pull ups pullups")
        entry("Chin-up", "Klimmzug im Untergriff", body, profile("UPPER_BACK BICEPS", "", "CORE FOREARMS"), "chin ups chinups")
        entry("Assisted Pull-up", "Unterstützter Klimmzug", machine, pulldown, "Klimmzugmaschine assisted chin up")
        entry("Inverted Row", "Umgekehrtes Rudern", body, row, "Australian pull up Körpergewicht Rudern")
        entry("Suspension Row", "Rudern am Schlingentrainer", body, row, "TRX row")
        entry("Straight-arm Pulldown", "Latziehen mit gestreckten Armen", cable, profile("UPPER_BACK", "", "TRICEPS CORE"), "Kabel Überzüge cable pullover")
        entry("Pullover Machine", "Überzugmaschine", machine, profile("UPPER_BACK", "CHEST", "TRICEPS"), "Nautilus pullover Überzüge Maschine")
        entry("Dumbbell Pullover", "Kurzhantel-Überzüge", free, profile("UPPER_BACK CHEST", "", "TRICEPS"))
        entry("Back Extension Machine", "Rückenstrecker-Maschine", machine, profile("LOWER_BACK", "GLUTES HAMSTRINGS"), "Rückenextension Rückenmaschine")
        entry("Back Extension", "Hyperextensions", body, profile("LOWER_BACK", "GLUTES HAMSTRINGS"), "Roman chair Rückenstrecken")
        entry("Reverse Hyperextension", "Umgekehrte Hyperextensions", machine, profile("GLUTES", "HAMSTRINGS LOWER_BACK"))
        entry("Superman", "Superman-Rückenheben", body, profile("LOWER_BACK", "GLUTES", "SHOULDERS"))

        val shoulderPress = profile("SHOULDERS", "TRICEPS", "TRAPS")
        val lateral = profile("SHOULDERS", "", "TRAPS")
        val rearDelt = profile("SHOULDERS UPPER_BACK", "TRAPS")
        entry("Shoulder Press Machine", "Schulterpresse", machine, shoulderPress, "Schulterdrücken Maschine")
        entry("Dumbbell Shoulder Press", "Kurzhantel-Schulterdrücken", free, shoulderPress, "KH Schulterdrücken")
        entry("Overhead Press", "Langhantel-Schulterdrücken", free, shoulderPress + profile("", "", "CORE"), "Military press Frontdrücken OHP")
        entry("Arnold Press", "Arnold-Drücken", free, shoulderPress)
        entry("Smith Machine Shoulder Press", "Schulterdrücken an der Multipresse", machine, shoulderPress)
        entry("Landmine Press", "Landmine-Schulterdrücken", free, shoulderPress + profile("", "", "CORE"))
        entry("Lateral Raise Machine", "Seithebemaschine", machine, lateral, "Seitheben Maschine")
        entry("Dumbbell Lateral Raise", "Kurzhantel-Seitheben", free, lateral, "Seitheben KH")
        entry("Cable Lateral Raise", "Seitheben am Kabelzug", cable, lateral)
        entry("Front Raise", "Frontheben", free, lateral, "Kurzhantel Langhantel plate front raise")
        entry("Cable Front Raise", "Frontheben am Kabelzug", cable, lateral)
        entry("Reverse Pec Deck", "Butterfly Reverse", machine, rearDelt, "Reverse Fly Maschine hintere Schulter")
        entry("Bent-over Reverse Fly", "Vorgebeugtes Seitheben", free, rearDelt, "Reverse Fly Kurzhantel rear delt fly")
        entry("Cable Reverse Fly", "Reverse Fly am Kabelzug", cable, rearDelt)
        entry("Face Pull", "Face Pulls am Seilzug", cable, rearDelt, "Gesichtszug Außenrotation")
        entry("Dumbbell Shrug", "Schulterheben mit Kurzhanteln", free, profile("TRAPS", "FOREARMS"), "Nackenziehen Shrugs")
        entry("Barbell Shrug", "Schulterheben mit Langhantel", free, profile("TRAPS", "FOREARMS"))
        entry("Shrug Machine", "Nackenmaschine", machine, profile("TRAPS", "FOREARMS"), "Schulterheben Maschine")
        entry("Upright Row", "Aufrechtes Rudern", free, profile("SHOULDERS TRAPS", "BICEPS", "FOREARMS"))
        entry("Pike Push-up", "Pike-Liegestütz", body, shoulderPress)
        entry("Handstand Push-up", "Handstand-Liegestütz", body, shoulderPress + profile("", "", "CORE"))

        val curl = profile("BICEPS", "FOREARMS")
        val triceps = profile("TRICEPS")
        entry("Biceps Curl Machine", "Bizepsmaschine", machine, curl, "Bizeps Curl Maschine Armbeuger")
        entry("Preacher Curl Machine", "Scottcurl-Maschine", machine, curl, "Scott Curl Maschine")
        entry("Barbell Curl", "Langhantelcurls", free, curl, "LH Curls Bizepscurl")
        entry("EZ-bar Curl", "SZ-Curls", free, curl, "SZ Stange Bizepscurls")
        entry("Dumbbell Curl", "Kurzhantelcurls", free, curl, "KH Curls Bizeps Curls")
        entry("Hammer Curl", "Hammercurls", free, profile("BICEPS FOREARMS"), "Neutralgriff Curls")
        entry("Incline Dumbbell Curl", "Schrägbankcurls", free, curl)
        entry("Concentration Curl", "Konzentrationscurls", free, curl)
        entry("Preacher Curl", "Scottcurls", free, curl)
        entry("Cable Curl", "Bizepscurls am Kabelzug", cable, curl, "Seilzug Curls")
        entry("Reverse Curl", "Curls im Obergriff", free, profile("FOREARMS", "BICEPS"), "Reverse Bizepscurl")
        entry("Triceps Extension Machine", "Trizepsmaschine", machine, triceps, "Armstrecker Trizepsstrecken Maschine")
        entry("Triceps Pushdown", "Trizepsdrücken am Kabelzug", cable, triceps, "Trizeps Pushdown Seil Seilzug rope pushdown")
        entry("Overhead Cable Triceps Extension", "Trizepsstrecken über Kopf am Kabelzug", cable, triceps, "French press Kabel")
        entry("Dumbbell Triceps Extension", "Trizepsstrecken mit Kurzhantel", free, triceps, "über Kopf einarmig")
        entry("Skull Crusher", "French Press mit SZ-Stange", free, triceps, "Stirndrücken lying triceps extension")
        entry("Triceps Kickback", "Trizeps-Kickbacks", free, triceps)
        entry("Close-grip Bench Press", "Enges Bankdrücken", free, profile("TRICEPS", "CHEST SHOULDERS"))
        entry("Bench Dip", "Bankdips", body, profile("TRICEPS", "CHEST SHOULDERS"))
        entry("Diamond Push-up", "Diamant-Liegestütz", body, profile("TRICEPS CHEST", "SHOULDERS", "CORE"), "enge Liegestütze")
        entry("Wrist Curl", "Handgelenkcurls", free, profile("FOREARMS"), "Unterarmcurls wrist flexion")
        entry("Reverse Wrist Curl", "Handgelenkstrecken", free, profile("FOREARMS"), "Unterarmstrecken")
        entry("Farmer Carry", "Farmer's Walk", free, profile("FOREARMS TRAPS", "CORE", "GLUTES QUADS"), "Bauernlauf loaded carry")
        entry("Dead Hang", "Hängen an der Klimmzugstange", body, profile("FOREARMS", "UPPER_BACK", "SHOULDERS"))

        val squat = profile("QUADS GLUTES", "ADDUCTORS", "CORE LOWER_BACK HAMSTRINGS CALVES")
        val legPress = profile("QUADS", "GLUTES ADDUCTORS", "HAMSTRINGS CALVES")
        val lunge = profile("QUADS GLUTES", "HAMSTRINGS ADDUCTORS", "CORE CALVES ABDUCTORS")
        val hinge = profile("HAMSTRINGS GLUTES", "LOWER_BACK", "CORE FOREARMS TRAPS")
        entry("Leg Press", "Beinpresse", machine, legPress, "Beinpressmaschine leg press machine")
        entry("45-degree Leg Press", "45-Grad-Beinpresse", machine, legPress, "Schlitten Beinpresse")
        entry("Horizontal Leg Press", "Horizontale Beinpresse", machine, legPress)
        entry("Single-leg Press", "Einbeinige Beinpresse", machine, legPress)
        entry("Hack Squat", "Hackenschmidt-Kniebeuge", machine, legPress, "Hackenschmidt Maschine")
        entry("Pendulum Squat", "Pendulum-Kniebeuge", machine, legPress)
        entry("Belt Squat", "Gürtel-Kniebeuge", machine, legPress)
        entry("Smith Machine Squat", "Kniebeuge an der Multipresse", machine, squat)
        entry("Barbell Back Squat", "Kniebeugen mit Langhantel", free, squat, "Squat Kniebeuge LH Back Squat")
        entry("Front Squat", "Frontkniebeuge", free, squat)
        entry("Goblet Squat", "Goblet-Kniebeuge", free, squat, "Kettlebell Kniebeuge")
        entry("Bodyweight Squat", "Kniebeugen ohne Gewicht", body, legPress, "Air squat freie Kniebeuge")
        entry("Sumo Squat", "Sumo-Kniebeuge", free, profile("QUADS GLUTES ADDUCTORS", "", "CORE"))
        entry("Split Squat", "Ausfallschritt-Kniebeuge", free, lunge)
        entry("Bulgarian Split Squat", "Bulgarische Kniebeuge", free, lunge, "Bulgarian lunge rear foot elevated")
        entry("Walking Lunge", "Gehende Ausfallschritte", free, lunge, "Lunges Ausfallschritt")
        entry("Reverse Lunge", "Rückwärts-Ausfallschritt", free, lunge)
        entry("Side Lunge", "Seitlicher Ausfallschritt", body, profile("QUADS GLUTES ADDUCTORS", "ABDUCTORS"), "Lateral lunge")
        entry("Step-up", "Aufsteigen auf eine Bank", free, lunge, "Step Ups Box Aufsteiger")
        entry("Pistol Squat", "Einbeinige Kniebeuge", body, lunge)
        entry("Leg Extension", "Beinstrecker", machine, profile("QUADS"), "Beinstreckmaschine Quadrizeps leg extension machine")
        entry("Seated Leg Curl", "Beinbeuger sitzend", machine, profile("HAMSTRINGS", "CALVES"), "Leg Curl Beinbeugermaschine")
        entry("Lying Leg Curl", "Beinbeuger liegend", machine, profile("HAMSTRINGS", "CALVES"))
        entry("Standing Leg Curl", "Beinbeuger stehend", machine, profile("HAMSTRINGS", "CALVES"))
        entry("Nordic Curl", "Nordische Beinbeuger", body, profile("HAMSTRINGS", "", "GLUTES CALVES"))
        entry("Glute Ham Raise", "Glute-Ham-Raise", body, profile("HAMSTRINGS", "GLUTES LOWER_BACK"), "GHR GHD")
        entry("Romanian Deadlift", "Rumänisches Kreuzheben", free, hinge, "RDL gestrecktes Kreuzheben")
        entry("Dumbbell Romanian Deadlift", "Rumänisches Kreuzheben mit Kurzhanteln", free, hinge)
        entry("Single-leg Romanian Deadlift", "Einbeiniges Kreuzheben", free, hinge + profile("", "", "ABDUCTORS"))
        entry("Deadlift", "Kreuzheben", free, profile("GLUTES LOWER_BACK", "QUADS HAMSTRINGS FOREARMS TRAPS", "CORE UPPER_BACK"), "konventionelles Kreuzheben barbell deadlift")
        entry("Sumo Deadlift", "Sumo-Kreuzheben", free, profile("GLUTES QUADS ADDUCTORS", "LOWER_BACK HAMSTRINGS FOREARMS", "CORE TRAPS"))
        entry("Trap-bar Deadlift", "Kreuzheben mit Hex-Bar", free, profile("QUADS GLUTES", "HAMSTRINGS LOWER_BACK FOREARMS", "CORE TRAPS"))
        entry("Good Morning", "Good Mornings", free, profile("HAMSTRINGS GLUTES", "LOWER_BACK", "CORE"))
        entry("Hip Thrust", "Hüftheben mit Langhantel", free, profile("GLUTES", "HAMSTRINGS", "CORE ADDUCTORS"), "Langhantel Glute Bridge Beckenheben")
        entry("Hip Thrust Machine", "Hip-Thrust-Maschine", machine, profile("GLUTES", "HAMSTRINGS", "CORE ADDUCTORS"), "Glute Drive Booty Builder")
        entry("Glute Bridge", "Beckenheben", body, profile("GLUTES", "HAMSTRINGS", "CORE"), "Hüftbrücke")
        entry("Cable Kickback", "Glute-Kickbacks am Kabelzug", cable, profile("GLUTES", "HAMSTRINGS"), "Po Kickbacks Hüftstrecken")
        entry("Glute Kickback Machine", "Gesäßmaschine", machine, profile("GLUTES", "HAMSTRINGS"), "Gluteusmaschine Po Maschine")
        entry("Cable Pull-through", "Pull-through am Kabelzug", cable, profile("GLUTES HAMSTRINGS", "LOWER_BACK", "CORE"))
        entry("Hip Abduction Machine", "Abduktorenmaschine", machine, profile("ABDUCTORS", "GLUTES"), "Beine außen abspreizen Hüftabduktion")
        entry("Hip Adduction Machine", "Adduktorenmaschine", machine, profile("ADDUCTORS"), "Beine innen zusammenführen Hüftadduktion")
        entry("Cable Hip Abduction", "Hüftabduktion am Kabelzug", cable, profile("ABDUCTORS", "GLUTES"))
        entry("Cable Hip Adduction", "Hüftadduktion am Kabelzug", cable, profile("ADDUCTORS"))
        entry("Clamshell", "Muschel-Übung", body, profile("ABDUCTORS", "GLUTES"), "Clam seitliches Hüftöffnen")
        entry("Lateral Band Walk", "Seitlicher Gang mit Miniband", body, profile("ABDUCTORS", "GLUTES"), "Monster Walk Band Side Steps")
        entry("Standing Calf Raise", "Wadenheben stehend", machine, profile("CALVES"), "Wadenmaschine")
        entry("Seated Calf Raise", "Wadenheben sitzend", machine, profile("CALVES"))
        entry("Leg Press Calf Raise", "Wadenheben an der Beinpresse", machine, profile("CALVES"))
        entry("Single-leg Calf Raise", "Einbeiniges Wadenheben", body, profile("CALVES"))

        val abs = profile("CORE")
        entry("Abdominal Crunch Machine", "Bauchmaschine", machine, abs, "Bauchpresse Crunch Maschine Bauchmuskeltrainer")
        entry("Cable Crunch", "Crunches am Kabelzug", cable, abs, "Bauch Seilzug kniende Crunches")
        entry("Crunch", "Bauchpressen", body, abs, "Crunches Bauch Crunch")
        entry("Sit-up", "Rumpfbeugen", body, abs, "Sit Ups Situps")
        entry("Reverse Crunch", "Umgekehrte Crunches", body, abs)
        entry("Hanging Knee Raise", "Knieheben hängend", body, profile("CORE", "", "FOREARMS"), "hängendes Knieheben")
        entry("Hanging Leg Raise", "Beinheben hängend", body, profile("CORE", "", "FOREARMS"))
        entry("Captain's Chair Knee Raise", "Knieheben am Dipständer", machine, abs, "Power Tower leg raise")
        entry("Lying Leg Raise", "Beinheben liegend", body, abs)
        entry("Plank", "Unterarmstütz", body, profile("CORE", "SHOULDERS", "GLUTES"), "Brett")
        entry("Side Plank", "Seitstütz", body, profile("CORE", "SHOULDERS ABDUCTORS"))
        entry("Dead Bug", "Käfer-Übung", body, abs)
        entry("Bird Dog", "Diagonalstrecken im Vierfüßlerstand", body, profile("CORE LOWER_BACK", "GLUTES", "SHOULDERS"))
        entry("Russian Twist", "Russische Drehung", body, abs, "Rumpfdrehen Russian Twists")
        entry("Torso Rotation Machine", "Rumpfrotationsmaschine", machine, abs, "Rumpfdreher Rotary Torso")
        entry("Cable Wood Chop", "Holzhacken am Kabelzug", cable, profile("CORE", "SHOULDERS"), "Woodchopper")
        entry("Pallof Press", "Pallof-Drücken", cable, profile("CORE", "", "SHOULDERS"), "Anti Rotation")
        entry("Ab Wheel Rollout", "Bauchrollen", body, profile("CORE", "SHOULDERS UPPER_BACK"), "Ab Roller Bauchrad")
        entry("Mountain Climber", "Bergsteiger", body, profile("CORE", "QUADS SHOULDERS"))
        entry("Bicycle Crunch", "Fahrrad-Crunches", body, abs)
        entry("Hollow Hold", "Hollow-Body-Halten", body, abs)

        entry("Stationary Bike", "Fahrradergometer", cardio, profile("QUADS", "GLUTES HAMSTRINGS", "CALVES"), "Ergometer Fahrrad bike cycling Radfahren")
        entry("Recumbent Bike", "Liegeergometer", cardio, profile("QUADS", "GLUTES HAMSTRINGS", "CALVES"), "Liegefahrrad")
        entry("Spin Bike", "Spinning-Rad", cardio, profile("QUADS", "GLUTES HAMSTRINGS", "CALVES"), "Indoor Cycling")
        entry("Treadmill Walk", "Gehen auf dem Laufband", cardio, profile("QUADS GLUTES", "HAMSTRINGS CALVES"), "Walking Laufband")
        entry("Treadmill Run", "Laufen auf dem Laufband", cardio, profile("QUADS GLUTES CALVES", "HAMSTRINGS", "CORE"), "Joggen running")
        entry("Incline Treadmill Walk", "Steigungsgehen auf dem Laufband", cardio, profile("GLUTES CALVES", "QUADS HAMSTRINGS"))
        entry("Elliptical Trainer", "Crosstrainer", cardio, profile("QUADS GLUTES", "HAMSTRINGS CALVES", "UPPER_BACK SHOULDERS BICEPS TRICEPS"), "Ellipsentrainer Cross Trainer")
        entry("Rowing Ergometer", "Ruderergometer", cardio, profile("QUADS UPPER_BACK", "GLUTES HAMSTRINGS BICEPS", "CORE FOREARMS"), "Rudergerät rowing cardio Rower")
        entry("Stair Climber", "Treppensteiger", cardio, profile("GLUTES QUADS", "CALVES HAMSTRINGS"), "Stairmaster Stepper Treppenmaschine")
        entry("Ski Ergometer", "Ski-Ergometer", cardio, profile("UPPER_BACK TRICEPS CORE", "SHOULDERS", "QUADS GLUTES"), "SkiErg")
        entry("Air Bike", "Airbike", cardio, profile("QUADS GLUTES", "HAMSTRINGS SHOULDERS UPPER_BACK TRICEPS BICEPS"), "Assault Bike Fan Bike")
        entry("Jump Rope", "Seilspringen", cardio, profile("CALVES", "QUADS", "CORE SHOULDERS FOREARMS"), "Skipping Springseil")
        entry("Burpee", "Burpees", body, profile("QUADS GLUTES CHEST", "CORE TRICEPS SHOULDERS", "CALVES"))
        entry("Kettlebell Swing", "Kettlebell-Schwingen", free, profile("GLUTES HAMSTRINGS", "CORE LOWER_BACK", "FOREARMS SHOULDERS"))
    }

    private val names = entries.associateWith { listOf(it.englishName, it.germanName, it.aliases).map(::normalizeExerciseSearch) }

    fun search(query: String, equipment: ExerciseEquipment? = null, limit: Int = 20): List<ExerciseTemplate> {
        val normalized = normalizeExerciseSearch(query)
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        return entries.asSequence().filter { equipment == null || it.equipment == equipment }
            .mapNotNull { entry ->
                val alternatives = names.getValue(entry)
                val words = alternatives.flatMap { it.split(' ') }.filter(String::isNotBlank)
                val score = when {
                    normalized.isBlank() -> 0
                    normalized in alternatives -> 1_000
                    alternatives.any { it.startsWith(normalized) } -> 800
                    alternatives.any { normalized in it } -> 600
                    tokens.all { token -> words.any { it.startsWith(token) } } -> 400
                    tokens.all { token -> words.any { word -> word.startsWith(token) || (token.length >= 4 && oneEditAway(token, word)) } } -> 100
                    else -> return@mapNotNull null
                }
                entry to score
            }.sortedWith(compareByDescending<Pair<ExerciseTemplate, Int>> { it.second }
                .thenBy { if (normalized.isEmpty()) 0 else it.first.englishName.length }.thenBy { it.first.englishName })
            .take(limit.coerceAtLeast(0)).map { it.first }.toList()
    }
}

internal fun normalizeExerciseSearch(value: String): String = Normalizer.normalize(
    value.lowercase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss"),
    Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "").replace("ae", "a").replace("oe", "o").replace("ue", "u")
    .replace(Regex("[^a-z0-9]+"), " ").trim()

private fun oneEditAway(a: String, b: String): Boolean {
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var edits = 0
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) { i++; j++; continue }
        if (++edits > 1) return false
        if (a.length >= b.length) i++
        if (b.length >= a.length) j++
    }
    return edits + (a.length - i) + (b.length - j) <= 1
}
