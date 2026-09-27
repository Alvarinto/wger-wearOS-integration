package com.wger.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wger.wear.data.local.LoggedWorkoutSessionEntity

@Composable
fun SummaryScreen(
    session: LoggedWorkoutSessionEntity,
    totalSets: Int,
    onDone: () -> Unit
) {
    val durationSeconds = ((session.endTimestampMs - session.startTimestampMs) / 1000).coerceAtLeast(0)
    val mins = durationSeconds / 60
    val secs = durationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", mins, secs)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "¡ENTRENAMIENTO COMPLETADO!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Duración:", style = MaterialTheme.typography.bodySmall)
                        Text(durationFormatted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Series completadas:", style = MaterialTheme.typography.bodySmall)
                        Text("$totalSets", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pulso medio:", style = MaterialTheme.typography.bodySmall)
                        val hrText = if (session.avgHeartRateBpm > 0) "${session.avgHeartRateBpm} bpm" else "-- bpm"
                        Text(hrText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            val isSynced = session.syncStatus == "SYNCED"
            Text(
                text = if (isSynced) "✅ Sincronizado con wger" else "⏳ Pendiente de sincronizar",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text("Listo")
            }
        }
    }
}
