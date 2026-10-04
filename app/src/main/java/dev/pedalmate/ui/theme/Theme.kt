package dev.pedalmate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** App-wide Compose theme (dark only; the Bike+ is used in a dim room). */
@Composable
fun PedalMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        content()
    }
}
