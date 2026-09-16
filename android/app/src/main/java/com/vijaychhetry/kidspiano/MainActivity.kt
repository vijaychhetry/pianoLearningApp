package com.vijaychhetry.kidspiano

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijaychhetry.kidspiano.calibration.CalibrationScreen
import com.vijaychhetry.kidspiano.calibration.CalibrationViewModel
import com.vijaychhetry.kidspiano.lab.AudioLabScreen
import com.vijaychhetry.kidspiano.lab.AudioLabViewModel

private enum class Tab { CALIBRATE, LAB }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { KidsPianoApp() }
    }
}

@Composable
private fun KidsPianoApp() {
    var tab by remember { mutableStateOf(Tab.CALIBRATE) }
    val labModel: AudioLabViewModel = viewModel()
    val calibrationModel: CalibrationViewModel = viewModel()

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(
                    "Kids Piano Lab",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabButton("Calibrate", tab == Tab.CALIBRATE) {
                        labModel.stop()
                        tab = Tab.CALIBRATE
                    }
                    TabButton("Audio Lab", tab == Tab.LAB) {
                        calibrationModel.stop()
                        tab = Tab.LAB
                    }
                }
            }
            when (tab) {
                Tab.CALIBRATE -> CalibrationScreen(calibrationModel)
                Tab.LAB -> AudioLabScreen(labModel)
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
