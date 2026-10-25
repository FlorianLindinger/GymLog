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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

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
            fun fits(size: Float): Boolean = !measurer.measure(
                text = text,
                style = style.copy(fontSize = size.sp),
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = width),
            ).didOverflowWidth

            val preferred = style.fontSize.value
            if (fits(preferred)) preferred else {
                var low = 1f
                var high = preferred
                repeat(12) {
                    val middle = (low + high) / 2f
                    if (fits(middle)) low = middle else high = middle
                }
                low
            }
        }
        Text(text, style = style.copy(fontSize = fontSize.sp), color = color, maxLines = 1, softWrap = false)
    }
}
