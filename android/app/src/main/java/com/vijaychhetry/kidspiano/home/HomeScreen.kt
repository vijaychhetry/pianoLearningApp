package com.vijaychhetry.kidspiano.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vijaychhetry.kidspiano.core.learning.Copy
import com.vijaychhetry.kidspiano.ui.HoldButton

@Composable
fun HomeScreen(
    pianoReady: Boolean,
    setupSummary: String?,
    onPractice: () -> Unit,
    onGrownUps: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Kids Piano", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Play the real piano. The phone listens.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onPractice,
            enabled = pianoReady,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("Practice", fontSize = 22.sp)
        }
        Text(
            if (pianoReady) setupSummary ?: Copy.READY else Copy.NOT_CALIBRATED,
            style = MaterialTheme.typography.bodyMedium,
        )
        HoldButton(
            label = "Grown-ups",
            holdLabel = "Keep holding…",
            textButton = true,
            onHeld = onGrownUps,
        )
    }
}
