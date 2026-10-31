package com.gymlog.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalContentColor
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

/** Estimate from actual line width, then correct for fixed letter spacing and pixel rounding. */
internal fun fittedSingleLineFontSize(preferredSize: Float, availableWidth: Int, measureWidth: (Float) -> Int): Float {
    var candidate = preferredSize.takeIf { it.isFinite() && it > 0 }?.coerceAtLeast(1f) ?: 14f
    val width = availableWidth.coerceAtLeast(1)
    repeat(6) {
        val measured = measureWidth(candidate)
        if (measured <= width || candidate <= 1f) return candidate
        candidate = (candidate * width / measured - .1f).coerceAtLeast(1f)
    }
    return 1f
}

/** Keep the full line visible, shrinking only when the available width requires it. */
@Composable
internal fun AutoFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) = AutoFitText(AnnotatedString(text), style, modifier, color)

@Composable
internal fun AutoFitText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val fontSize = remember(text, style, width, density, measurer) {
            fittedSingleLineFontSize(style.fontSize.value, width) { size -> measurer.measure(
                text = text,
                style = style.copy(fontSize = size.sp),
                softWrap = false,
                maxLines = 1,
            ).size.width }
        }
        Text(text, style = style.copy(fontSize = fontSize.sp), color = color, maxLines = 1, softWrap = false)
    }
}
