package dev.pedalmate.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.pedalmate.ui.theme.PedalMateTheme

/** Launcher activity. Stub for now; the real setup screen arrives in A14. */
class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PedalMateTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Text("PedalMate")
                }
            }
        }
    }
}
