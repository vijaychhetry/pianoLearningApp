package com.vijaychhetry.kidspiano

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijaychhetry.kidspiano.calibration.CalibrationScreen
import com.vijaychhetry.kidspiano.calibration.CalibrationStore
import com.vijaychhetry.kidspiano.calibration.CalibrationViewModel
import com.vijaychhetry.kidspiano.core.learning.lessonMayStart
import com.vijaychhetry.kidspiano.grownups.FilesScreen
import com.vijaychhetry.kidspiano.grownups.GrownUpsGateScreen
import com.vijaychhetry.kidspiano.grownups.SetupScreen
import com.vijaychhetry.kidspiano.home.HomeScreen
import com.vijaychhetry.kidspiano.lab.AudioLabScreen
import com.vijaychhetry.kidspiano.lab.AudioLabViewModel
import com.vijaychhetry.kidspiano.lesson.LessonScreen
import com.vijaychhetry.kidspiano.lesson.LessonViewModel
import com.vijaychhetry.kidspiano.ui.LockScreenOrientation

private enum class Dest { HOME, PRACTICE, GATE, GROWNUPS }
private enum class GrownUpsTab { SETUP, CALIBRATE, FILES, LAB }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }
        setContent {
            MaterialTheme(colorScheme = kidsScheme) {
                KidsPianoApp()
            }
        }
    }
}

@Composable
private fun KidsPianoApp() {
    var dest by remember { mutableStateOf(Dest.HOME) }
    var grownUpsTab by remember { mutableStateOf(GrownUpsTab.SETUP) }
    val labModel: AudioLabViewModel = viewModel()
    val calibrationModel: CalibrationViewModel = viewModel()
    val lessonModel: LessonViewModel = viewModel()
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    var homeTick by remember { mutableStateOf(0) }
    val profile = remember(homeTick) { store.load() }
    val pianoReady = lessonMayStart(profile)
    val playSession = dest == Dest.PRACTICE
    LockScreenOrientation(landscape = playSession)

    BackHandler(enabled = dest != Dest.HOME) {
        when (dest) {
            Dest.PRACTICE -> {
                lessonModel.stop()
                homeTick++
                dest = Dest.HOME
            }
            Dest.GATE -> dest = Dest.HOME
            Dest.GROWNUPS -> {
                labModel.stop()
                calibrationModel.stop()
                homeTick++
                dest = Dest.HOME
            }
            Dest.HOME -> Unit
        }
    }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (dest) {
                Dest.HOME -> HomeScreen(
                    pianoReady = pianoReady,
                    setupSummary = store.summary(),
                    courseLabel = store.lessonSet().label,
                    onPractice = { dest = Dest.PRACTICE },
                    onGrownUps = { dest = Dest.GATE },
                )
                Dest.PRACTICE -> LessonScreen(lessonModel) {
                    lessonModel.stop()
                    homeTick++
                    dest = Dest.HOME
                }
                Dest.GATE -> GrownUpsGateScreen(
                    onUnlocked = { dest = Dest.GROWNUPS },
                    onBack = { dest = Dest.HOME },
                )
                Dest.GROWNUPS -> GrownUpsTools(
                    tab = grownUpsTab,
                    onTab = { tab ->
                        if (tab != grownUpsTab) {
                            labModel.stop()
                            calibrationModel.stop()
                            grownUpsTab = tab
                        }
                    },
                    onHome = {
                        labModel.stop()
                        calibrationModel.stop()
                        homeTick++
                        dest = Dest.HOME
                    },
                    labModel = labModel,
                    calibrationModel = calibrationModel,
                )
            }
        }
    }
}

@Composable
private fun GrownUpsTools(
    tab: GrownUpsTab,
    onTab: (GrownUpsTab) -> Unit,
    onHome: () -> Unit,
    labModel: AudioLabViewModel,
    calibrationModel: CalibrationViewModel,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Grown-ups",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onHome) { Text("Kids Home") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabButton("Setup", tab == GrownUpsTab.SETUP) {
                    onTab(GrownUpsTab.SETUP)
                }
                TabButton("Calibrate", tab == GrownUpsTab.CALIBRATE) {
                    onTab(GrownUpsTab.CALIBRATE)
                }
                TabButton("Files", tab == GrownUpsTab.FILES) {
                    onTab(GrownUpsTab.FILES)
                }
                TabButton("Lab", tab == GrownUpsTab.LAB) {
                    onTab(GrownUpsTab.LAB)
                }
            }
        }
        when (tab) {
            GrownUpsTab.SETUP -> SetupScreen()
            GrownUpsTab.CALIBRATE -> CalibrationScreen(calibrationModel)
            GrownUpsTab.FILES -> FilesScreen()
            GrownUpsTab.LAB -> AudioLabScreen(labModel)
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

private val kidsScheme = lightColorScheme(
    primary = Color(0xFF5B3CC4),
    secondary = Color(0xFF1F8A5B),
    tertiary = Color(0xFFD46A2E),
    background = Color(0xFFFFF6F0),
    surface = Color(0xFFFFFBFA),
)
