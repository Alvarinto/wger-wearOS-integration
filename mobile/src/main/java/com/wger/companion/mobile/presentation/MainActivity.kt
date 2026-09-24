package com.wger.companion.mobile.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wger.companion.mobile.BuildConfig
import com.wger.companion.mobile.data.model.WgerExerciseSlot
import com.wger.companion.mobile.data.model.WgerRoutine
import com.wger.companion.mobile.datalayer.PhoneSyncManager
import com.wger.companion.mobile.network.WgerApiClient
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MobileRelayScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileRelayScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { WgerApiClient() }
    val syncManager = remember { PhoneSyncManager(context) }

    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isLoadingRoutines by remember { mutableStateOf(false) }
    var routines by remember { mutableStateOf<List<WgerRoutine>>(emptyList()) }
    var selectedRoutine by remember { mutableStateOf<WgerRoutine?>(null) }
    var slotsForSelectedRoutine by remember { mutableStateOf<List<WgerExerciseSlot>>(emptyList()) }
    var syncToWatchStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("wger Relay Móvil") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de Estado del Servidor
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Configuración del Servidor wger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "URL: ${BuildConfig.WGER_SERVER_URL}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val tokenDisplay = if (BuildConfig.WGER_API_TOKEN.isNotBlank()) "Configurado (oculto)" else "NO CONFIGURADO"
                        Text(
                            text = "Token API: $tokenDisplay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (BuildConfig.WGER_API_TOKEN.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    connectionStatus = "Comprobando..."
                                    val result = apiClient.testConnection()
                                    connectionStatus = if (result.isSuccess) {
                                        "✅ Conexión correcta con wger"
                                    } else {
                                        "❌ Error: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            }
                        ) {
                            Text("Probar conexión HTTP")
                        }

                        connectionStatus?.let { status ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = status, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Tarjeta de Gestión de Rutinas
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sincronización con Pixel Watch 3",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Descarga tu rutina actual de wger y envíala al reloj para entrenar offline.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isLoadingRoutines = true
                                        val result = apiClient.getActiveRoutines()
                                        if (result.isSuccess) {
                                            routines = result.getOrNull() ?: emptyList()
                                            val first = routines.firstOrNull { it.is_active } ?: routines.firstOrNull()
                                            selectedRoutine = first
                                            if (first != null) {
                                                val slotsRes = apiClient.getRoutineDateSequenceGym(first.id)
                                                slotsForSelectedRoutine = slotsRes.getOrNull() ?: emptyList()
                                            }
                                        } else {
                                            connectionStatus = "Error cargando rutinas: ${result.exceptionOrNull()?.message}"
                                        }
                                        isLoadingRoutines = false
                                    }
                                },
                                enabled = !isLoadingRoutines
                            ) {
                                Text("Obtener Rutinas de wger")
                            }

                            if (isLoadingRoutines) {
                                CircularProgressIndicator(modifier = Modifier.height(24.dp))
                            }
                        }

                        selectedRoutine?.let { routine ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Rutina activa seleccionada: ${routine.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${slotsForSelectedRoutine.size} series programadas",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        syncToWatchStatus = "Enviando vía Data Layer..."
                                        val sent = syncManager.sendRoutineToWatch(
                                            routineId = routine.id,
                                            routineName = routine.name,
                                            routineDescription = routine.description,
                                            slots = slotsForSelectedRoutine
                                        )
                                        syncToWatchStatus = if (sent) {
                                            "✅ ¡Rutina enviada al reloj con setUrgent()!"
                                        } else {
                                            "❌ Error enviando rutina al reloj (verifica conexión Bluetooth/WiFi del reloj)"
                                        }
                                    }
                                }
                            ) {
                                Text("Enviar Rutina al Reloj")
                            }

                            syncToWatchStatus?.let { statusText ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Información de Topología
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ℹ️ Estado de Conectividad",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Asegúrate de tener Tailscale conectado en este móvil si tu servidor wger está en tu VPN privada.\n" +
                                    "• El servicio 'MobileDataLayerListenerService' se ejecuta en background y escuchará automáticamente cuando el reloj termine un entrenamiento para subirlo a wger.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
