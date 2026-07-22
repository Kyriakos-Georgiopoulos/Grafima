package io.grafima.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.grafima.sample.ui.theme.GrafimaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrafimaApp()
        }
    }
}

@Composable
fun GrafimaApp() {
    GrafimaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChartsDemoScreen()
        }
    }
}
