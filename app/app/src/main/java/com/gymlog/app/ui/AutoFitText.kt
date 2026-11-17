package com.gymlog.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/** Estimate from actual line width, then correct for fixed letter spacing and pixel rounding. */
internal fun fittedSingleLineFontSize(preferredSize: Float, availableWidth: Int, minimumSize: Float = 1f, measureWidth: (Float) -> Int): Float {
    var candidate = preferredSize.takeIf { it.isFinite() && it > 0 }?.coerceAtLeast(minimumSize) ?: 14f.coerceAtLeast(minimumSize)
    val width = availableWidth.coerceAtLeast(1)
    repeat(6) {
        val measured = measureWidth(candidate)
        if (measured <= width || candidate <= minimumSize) return candidate
        candidate = (candidate * width / measured - .1f).coerceAtLeast(minimumSize)
    }
    return minimumSize
}

/** Fit one line; an explicit readable minimum allows wrapping instead of shrinking further. */
@Composable
internal fun AutoFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    minimumFontSize: TextUnit = 1.sp,
) = AutoFitText(AnnotatedString(text), style, modifier, color, minimumFontSize)

@Composable
internal fun AutoFitText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    minimumFontSize: TextUnit = 1.sp,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val fontSize = remember(text, style, width, density, measurer, minimumFontSize) {
            fittedSingleLineFontSize(style.fontSize.value, width, minimumFontSize.value) { size -> measurer.measure(
                text = text,
                style = style.copy(fontSize = size.sp),
                softWrap = false,
                maxLines = 1,
            ).size.width }
        }
        Text(text, style = style.copy(fontSize = fontSize.sp), color = color,
            maxLines = if (minimumFontSize.value > 1f) Int.MAX_VALUE else 1, softWrap = minimumFontSize.value > 1f)
    }
}

/** Choose whole recent entries, retaining the newest entry even when it needs to wrap. */
internal fun overviewHistoryPrefixLength(text: String, fits: (String) -> Boolean): Int {
    val ends = Regex(" \\| ").findAll(text).map { it.range.first }.toList() + text.length
    var low = 0
    var probe = 1
    // Bracket from the newest entries: measuring half of a years-long history is expensive.
    while (probe <= ends.lastIndex && fits(text.substring(0, ends[probe]))) {
        low = probe
        if (probe == ends.lastIndex) return ends[probe]
        probe = (probe * 2).coerceAtMost(ends.lastIndex)
    }
    var high = (probe - 1).coerceAtMost(ends.lastIndex)
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(text.substring(0, ends[middle]))) low = middle else high = middle - 1
    }
    return ends[low]
}

/** Fill the available row with complete entries at a readable size, without a fixed entry cap. */
@Composable
internal fun OverviewHistoryText(text: AnnotatedString, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyMedium
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val fitted = remember(text, width, density, style, measurer) {
            val end = overviewHistoryPrefixLength(text.text) {
                measurer.measure(text.subSequence(0, it.length), style.copy(fontSize = 12.sp),
                    softWrap = false, maxLines = 1).size.width <= width
            }
            val visible = text.subSequence(0, end)
            val size = fittedSingleLineFontSize(style.fontSize.value, width, 12f) { size ->
                measurer.measure(visible, style.copy(fontSize = size.sp), softWrap = false, maxLines = 1).size.width
            }
            visible to size
        }
        // Reuse this constraint scope and measurer instead of subcomposing another AutoFitText.
        Text(fitted.first, style = style.copy(fontSize = fitted.second.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = Int.MAX_VALUE, softWrap = true)
    }
}
