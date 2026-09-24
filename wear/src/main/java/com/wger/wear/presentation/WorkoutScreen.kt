package com.wger.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wger.wear.data.local.RoutineExerciseSlotEntity

@Composable
fun WorkoutScreen(
    currentSlot: RoutineExerciseSlotEntity,
    slotIndex: Int,
    totalSlots: Int,
    heartRateBpm: Int,
    elapsedSeconds: Long,
    onCompleteSet: (reps: Int, weightKg: Float) -> Unit,
    onFinishWorkout: () -> Unit
) {
    var reps by remember { mutableIntStateOf(currentSlot.targetReps) }
    var weightKg by remember { mutableFloatStateOf(currentSlot.defaultWeightKg) }

    LaunchedEffect(currentSlot.slotEntryId) {
        reps = currentSlot.targetReps
        weightKg = currentSlot.defaultWeightKg
    }

    val elapsedMinutes = elapsedSeconds / 60
    val elapsedSecs = elapsedSeconds % 60
    val formattedTime = String.format("%02d:%02d", elapsedMinutes, elapsedSecs)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicadores biométricos y de tiempo
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏱ $formattedTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (heartRateBpm > 0) "❤️ $heartRateBpm bpm" else "❤️ -- bpm",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (heartRateBpm > 140) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        // Título del ejercicio
        item {
            Text(
                text = currentSlot.exerciseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // Etiqueta de progresión del entrenamiento
        item {
            Text(
                text = "Serie ${currentSlot.setNumber} de ${currentSlot.totalSetsForExercise}  •  Total ${slotIndex + 1}/$totalSlots",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }

        // Selector de Repeticiones
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { if (reps > 1) reps-- },
                    modifier = Modifier.width(42.dp)
                ) {
                    Text("-", style = MaterialTheme.typography.titleMedium)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$reps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("reps", style = MaterialTheme.typography.labelSmall)
                }

                FilledTonalButton(
                    onClick = { reps++ },
                    modifier = Modifier.width(42.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Selector de Peso en Kg
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { if (weightKg >= 1.25f) weightKg -= 1.25f },
                    modifier = Modifier.width(42.dp)
                ) {
                    Text("-", style = MaterialTheme.typography.titleMedium)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val weightFormatted = if (weightKg % 1f == 0f) {
                        weightKg.toInt().toString()
                    } else {
                        String.format("%.1f", weightKg)
                    }
                    Text(
                        text = weightFormatted,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("kg", style = MaterialTheme.typography.labelSmall)
                }

                FilledTonalButton(
                    onClick = { weightKg += 1.25f },
                    modifier = Modifier.width(42.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Botón Registrar Serie
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onCompleteSet(reps, weightKg) },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text("Registrar serie")
            }
        }

        // Botón Finalizar Sesión
        item {
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(
                onClick = onFinishWorkout,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text("Finalizar sesión")
            }
        }
    }
}
