package com.vijaychhetry.kidspiano.grownups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vijaychhetry.kidspiano.core.learning.ParentGate

@Composable
fun GrownUpsGateScreen(onUnlocked: () -> Unit, onBack: () -> Unit) {
    val gate = remember { ParentGate() }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Grown-ups", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Calibrate and Audio Lab stay here so a child cannot change the piano setup by accident.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(gate.prompt, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = answer,
            onValueChange = {
                answer = it
                wrong = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Answer") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        if (wrong) {
            Text("That is not the number.", color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = {
            if (gate.accepts(answer)) onUnlocked() else wrong = true
        }) { Text("Open") }
    }
}
