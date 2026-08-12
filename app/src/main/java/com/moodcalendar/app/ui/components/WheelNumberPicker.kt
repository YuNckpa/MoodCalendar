package com.moodcalendar.app.ui.components

import android.widget.NumberPicker
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WheelNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() }
) {
    val min = range.first
    val max = range.last
    AndroidView(
        modifier = modifier
            .width(72.dp)
            .height(120.dp),
        factory = { context ->
            NumberPicker(context).apply {
                wrapSelectorWheel = true
                setFormatter { format(it) }
                // Force formatter refresh for already-drawn values
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }
        },
        update = { picker ->
            if (picker.minValue != min || picker.maxValue != max) {
                // Reset safely when range changes
                picker.minValue = 0
                picker.maxValue = 0
                picker.minValue = min
                picker.maxValue = max
            }
            val coerced = value.coerceIn(min, max)
            if (picker.value != coerced) {
                picker.value = coerced
            }
            picker.setOnValueChangedListener { _, _, newVal ->
                onValueChange(newVal)
            }
            picker.setFormatter { format(it) }
            // Invalidate so zero-padded labels redraw
            picker.invalidate()
        }
    )
}
