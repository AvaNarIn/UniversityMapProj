package com.example.universitymapproj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.universitymapproj.LocalLunchDecisionTreeState
import com.example.universitymapproj.LunchDecisionTreeState
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { false }

        File(filesDir, "model.dat").delete()

        enableEdgeToEdge()

        setContent {
            val lunchState = remember { LunchDecisionTreeState() }

            CompositionLocalProvider(
                LocalLunchDecisionTreeState provides lunchState
            ) {
                var appModeString by rememberSaveable { mutableStateOf<String?>(null) }
                val appMode: AppMode? = appModeString?.let { AppMode.valueOf(it) }

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
}