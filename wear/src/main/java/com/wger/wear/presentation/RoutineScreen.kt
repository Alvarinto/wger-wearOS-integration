package com.wger.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wger.wear.data.local.RoutineCacheEntity
import com.wger.wear.data.local.RoutineExerciseSlotEntity

@Composable
fun RoutineScreen(
    routine: RoutineCacheEntity?,
    slots: List<RoutineExerciseSlotEntity>,
    onStartWorkout: () -> Unit,
    onRefreshRoutine: () -> Unit
) {
    if (routine == null || slots.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sin rutina activa",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Abre la app en el móvil para sincronizar tu rutina de hoy.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onRefreshRoutine,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text("Pedir rutina al móvil")
            }
        }
    } else {
        val uniqueExercises = slots.map { it.exerciseName }.distinct()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                Text(
                    text = "${uniqueExercises.size} ejercicios • ${slots.size} series totales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text("Iniciar entrenamiento")
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onRefreshRoutine,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text("Actualizar rutina")
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ejercicios previstos:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(uniqueExercises) { exerciseName ->
                val count = slots.count { it.exerciseName == exerciseName }
                Card(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = exerciseName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "$count series programadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
