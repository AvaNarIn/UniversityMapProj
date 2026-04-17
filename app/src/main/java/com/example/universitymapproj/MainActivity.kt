package com.example.universitymapproj

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.universitymapproj.models.*
import com.example.universitymapproj.routing.*
import com.example.universitymapproj.clustering.runKMeans
import com.example.universitymapproj.serialization.*
import com.example.universitymapproj.NeuralNetwork.*
import java.io.File
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.universitymapproj.R
import com.example.universitymapproj.MainScreen
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { false }
        File(filesDir, "model.dat").delete()
        enableEdgeToEdge()
        setContent {
            var appModeString by rememberSaveable { mutableStateOf<String?>(null) }
            val appMode = appModeString?.let { AppMode.valueOf(it) }

            if (appMode == null) {
                ModeSelectionScreen { selected ->
                    appModeString = selected.name
                }
            } else {
                MainScreen(
                    appMode = appMode,
                    onChangeMode = { appModeString = null }
                )
            }
        }
    }
}

fun preprocessBitmap(bitmap: Bitmap): FloatArray {
    val resized = Bitmap.createScaledBitmap(bitmap, 50, 50, true)
    val input = FloatArray(50 * 50)

    for (y in 0 until 50) {
        for (x in 0 until 50) {
            val pixel = resized.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            val gray = (r + g + b) / 3f / 255f
            input[y * 50 + x] = 1f - gray  // Черный = 1, Белый = 0
        }
    }

    Log.d("PREPROCESS", "Preprocessed bitmap: ${input.count { it > 0.5f }} black pixels")
    return input
}
