package com.vijaychhetry.kidspiano

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijaychhetry.kidspiano.calibration.CalibrationScreen
import com.vijaychhetry.kidspiano.calibration.CalibrationViewModel
import com.vijaychhetry.kidspiano.lab.AudioLabScreen
import com.vijaychhetry.kidspiano.lab.AudioLabViewModel
import com.vijaychhetry.kidspiano.ui.KidsColors
import com.vijaychhetry.kidspiano.ui.KidsTheme

private enum class Screen { HOME, CALIBRATE, LAB }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent { KidsPianoApp() }
    }
}

@Composable
private fun KidsPianoApp() {
    var screen by remember { mutableStateOf(Screen.CALIBRATE) }
    val labModel: AudioLabViewModel = viewModel()
    val calibrationModel: CalibrationViewModel = viewModel()
    val activity = LocalContext.current as MainActivity

    DisposableEffect(screen) {
        activity.requestedOrientation = when (screen) {
            Screen.CALIBRATE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            onCalibrate = { screen = Screen.CALIBRATE },
            onLab = {
                calibrationModel.stop()
                screen = Screen.LAB
            },
        )
        Screen.CALIBRATE -> CalibrationScreen(
            model = calibrationModel,
            onHome = {
                calibrationModel.stop()
                screen = Screen.HOME
            },
        )
        Screen.LAB -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TextButton(
                onClick = {
                    labModel.stop()
                    screen = Screen.HOME
                },
                modifier = Modifier.statusBarsPadding().padding(start = 8.dp),
            ) { Text("Home") }
            AudioLabScreen(labModel)
        }
    }
}

@Composable
private fun HomeScreen(onCalibrate: () -> Unit, onLab: () -> Unit) {
    KidsTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(KidsColors.cream)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Kids Piano",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = KidsColors.purple,
            )
            Text(
                "The screen asks for one key at a time. Play it on the piano.",
                color = KidsColors.ink,
            )
            Button(onClick = onCalibrate) { Text("Calibrate") }
            OutlinedButton(onClick = onLab) { Text("Audio Lab") }
        }
    }
}
