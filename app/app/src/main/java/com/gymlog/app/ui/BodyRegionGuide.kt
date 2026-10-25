package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.gymlog.app.data.BodyArea
import kotlin.math.min

/** Original vector artwork. Region fills always use the selector's current palette. */
@Composable
internal fun BodyRegionGuide(colors: Map<BodyArea, Long>, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyRegionFigure(false, colors, Modifier.weight(1f))
                BodyRegionFigure(true, colors, Modifier.weight(1f))
            }
            BodyArea.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { area ->
                        val color = Color(colors[area] ?: area.color)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(20.dp).background(color, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .2f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("${area.ordinal + 1}", color = regionInk(color), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(regionGuideName(area), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            Text("Match the numbers and colors to the regions below.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun regionGuideName(area: BodyArea): String = when (area) {
    BodyArea.UPPER_BACK -> "Lats / upper back"
    BodyArea.CORE -> "Core / abs"
    else -> area.label.substringBefore(" /")
}

private fun regionInk(color: Color): Color = if (color.luminance() > .42f) Color(0xFF111820) else Color.White

private data class RegionPatch(val area: BodyArea, val outline: String, val mirrored: Boolean = true)
private data class RegionPin(val area: BodyArea, val x: Float, val y: Float)

private val silhouetteOutlines = listOf(
    "M43 27 L57 27 L58 35 Q69 36 73 43 L66 70 Q61 86 64 102 Q69 113 64 130 L55 136 L50 124 L45 136 L36 130 Q31 113 36 102 Q39 86 34 70 L27 43 Q31 36 42 35 Z",
    "M29 40 Q23 42 22 53 L19 77 L11 105 L9 119 Q8 125 12 128 Q16 128 17 121 L21 108 L29 82 L35 62 Z",
    "M36 113 Q31 133 34 157 L35 174 Q31 189 35 211 L36 229 L33 236 Q33 240 43 239 L46 235 L44 222 L46 204 Q49 189 45 174 L47 157 L49 125 Z",
)

private val sharedPatches = listOf(
    RegionPatch(BodyArea.SHOULDERS, "M29 41 Q25 43 24 51 L23 60 Q30 59 35 51 L37 40 Z"),
    RegionPatch(BodyArea.FOREARMS, "M20 84 L27 85 Q24 100 18 110 L14 113 L13 108 Z"),
)
private val frontPatches = sharedPatches + listOf(
    RegionPatch(BodyArea.CHEST, "M38 41 L49 44 L49 64 Q40 66 33 59 L35 49 Z"),
    RegionPatch(BodyArea.BICEPS, "M26 62 L32 58 Q33 70 28 81 L22 83 Q23 71 26 62 Z"),
    RegionPatch(BodyArea.CORE, "M40 68 L49 68 L49 103 L43 109 L38 96 Z"),
    RegionPatch(BodyArea.ABDUCTORS, "M35 107 L40 111 L37 133 L33 129 Q32 116 35 107 Z"),
    RegionPatch(BodyArea.QUADS, "M38 132 L44 128 L45 146 L42 170 L36 166 Q33 147 38 132 Z"),
    RegionPatch(BodyArea.ADDUCTORS, "M43 114 L49 119 L47 145 L44 156 L43 134 L39 123 Z"),
)
private val backPatches = sharedPatches + listOf(
    RegionPatch(BodyArea.TRAPS, "M43 33 L49 34 L49 65 L41 56 L35 41 Z"),
    RegionPatch(BodyArea.UPPER_BACK, "M35 60 L40 59 L49 68 L49 89 L43 87 L37 76 Z"),
    RegionPatch(BodyArea.LOWER_BACK, "M43 88 L49 91 L49 108 L39 107 Z"),
    RegionPatch(BodyArea.TRICEPS, "M25 62 L32 58 L31 73 L27 83 L22 81 Z"),
    RegionPatch(BodyArea.GLUTES, "M38 110 Q44 107 49 112 L49 128 Q43 135 36 129 L34 121 Z"),
    RegionPatch(BodyArea.HAMSTRINGS, "M36 134 L47 134 L45 157 L42 171 L36 167 Z"),
    RegionPatch(BodyArea.CALVES, "M36 181 Q41 176 44 184 Q48 197 42 213 L38 221 L35 207 Z"),
)
private val frontPins = listOf(
    RegionPin(BodyArea.CHEST, 43f, 53f), RegionPin(BodyArea.SHOULDERS, 29f, 48f),
    RegionPin(BodyArea.BICEPS, 27f, 72f), RegionPin(BodyArea.FOREARMS, 19f, 99f),
    RegionPin(BodyArea.CORE, 50f, 84f), RegionPin(BodyArea.QUADS, 39f, 151f),
    RegionPin(BodyArea.ADDUCTORS, 55f, 128f), RegionPin(BodyArea.ABDUCTORS, 35f, 118f),
)
private val backPins = listOf(
    RegionPin(BodyArea.TRAPS, 50f, 48f), RegionPin(BodyArea.UPPER_BACK, 43f, 74f),
    RegionPin(BodyArea.LOWER_BACK, 50f, 99f), RegionPin(BodyArea.TRICEPS, 27f, 72f),
    RegionPin(BodyArea.GLUTES, 43f, 120f), RegionPin(BodyArea.HAMSTRINGS, 39f, 151f),
    RegionPin(BodyArea.CALVES, 39f, 197f),
)

@Composable
private fun BodyRegionFigure(back: Boolean, colors: Map<BodyArea, Long>, modifier: Modifier) {
    val patches = if (back) backPatches else frontPatches
    val pins = if (back) backPins else frontPins
    val outlines = remember { silhouetteOutlines.map(::guidePath) }
    val paths = remember(back) { patches.map { guidePath(it.outline) } }
    val base = MaterialTheme.colorScheme.surfaceVariant
    val edge = MaterialTheme.colorScheme.onSurface.copy(alpha = .18f)
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        textSize = 7.5f
    } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (back) "BACK" else "FRONT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(240.dp).semantics {
            contentDescription = (if (back) "Back" else "Front") + " body regions: " + pins.joinToString { "${it.area.ordinal + 1} ${regionGuideName(it.area)}" }
        }) {
            val zoom = min(size.width / 100f, size.height / 250f)
            translate((size.width - 100f * zoom) / 2f, (size.height - 250f * zoom) / 2f) {
                scale(zoom, zoom, pivot = Offset.Zero) {
                    drawOval(base, Offset(40f, 4f), Size(20f, 25f))
                    drawOval(edge, Offset(40f, 4f), Size(20f, 25f), style = Stroke(.7f))
                    outlines.forEachIndexed { index, path ->
                        drawPath(path, base)
                        drawPath(path, edge, style = Stroke(.7f))
                        if (index > 0) scale(-1f, 1f, pivot = Offset(50f, 0f)) {
                            drawPath(path, base)
                            drawPath(path, edge, style = Stroke(.7f))
                        }
                    }
                    patches.forEachIndexed { index, patch ->
                        val color = Color(colors[patch.area] ?: patch.area.color)
                        drawPath(paths[index], color)
                        drawPath(paths[index], edge, style = Stroke(.5f))
                        if (patch.mirrored) scale(-1f, 1f, pivot = Offset(50f, 0f)) {
                            drawPath(paths[index], color)
                            drawPath(paths[index], edge, style = Stroke(.5f))
                        }
                    }
                    pins.forEach { pin ->
                        val color = Color(colors[pin.area] ?: pin.area.color)
                        drawCircle(color, 5.8f, Offset(pin.x, pin.y))
                        drawCircle(regionInk(color).copy(alpha = .65f), 5.8f, Offset(pin.x, pin.y), style = Stroke(.65f))
                        paint.color = if (color.luminance() > .42f) android.graphics.Color.rgb(17, 24, 32) else android.graphics.Color.WHITE
                        drawContext.canvas.nativeCanvas.drawText("${pin.area.ordinal + 1}", pin.x, pin.y - (paint.ascent() + paint.descent()) / 2f, paint)
                    }
                }
            }
        }
    }
}

private fun guidePath(outline: String): Path = requireNotNull(PathParser.createPathFromPathData(outline)).asComposePath()
