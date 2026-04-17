package com.example.universitymapproj

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.universitymapproj.NeuralNetwork.NeuralNetwork
import com.google.android.gms.location.*
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        splashScreen.setKeepOnScreenCondition { false }
        enableEdgeToEdge()

        setContent {

            val lunchState = remember { LunchDecisionTreeState() }
            var appModeString by rememberSaveable { mutableStateOf<String?>(null) }
            val appMode = appModeString?.let { AppMode.valueOf(it) }


            var gpsUserCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }


            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                if (fineGranted || coarseGranted) {
                    startLocationUpdates { lat: Double, lon: Double ->

                        gpsUserCell = MapConfig.gpsToGrid(lat, lon)
                    }
                }
            }


            LaunchedEffect(Unit) {
                launcher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }

            CompositionLocalProvider(LocalLunchDecisionTreeState provides lunchState) {
                if (appMode == null) {
                    ModeSelectionScreen { selected ->
                        appModeString = selected.name
                    }
                } else {
                    MainScreen(
                        appMode = appMode,
                        onChangeMode = { appModeString = null },
                        externalUserCell = gpsUserCell
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(onLocationReceived: (Double, Double) -> Unit) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        onLocationReceived(it.latitude, it.longitude)
                    }
                }
            },
            Looper.getMainLooper()
        )
    }
}