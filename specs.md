# ESPECIFICACIÓN TÉCNICA: CLIENTE WGER PARA PIXEL WATCH 3 (WEAR OS 5) Y RELAY MÓVIL

## 1. RESUMEN DEL SISTEMA Y REQUISITOS

* **Dispositivo objetivo:** Google Pixel Watch 3 (Wear OS 5 / API 34 / Android 14).
* **Entorno del desarrollador:** Linux (Arch/CachyOS) con CLI de Android y ADB inalámbrico.
* **Topología de red:** Móvil con Tailscale activo actúa como relay HTTP hacia la API REST v2 de wger. El reloj opera **100% offline** durante el entrenamiento.
* **Replicación:** Google Play Services Wearable Data Layer mediante `DataClient` persistente con `.setUrgent()`. No usar `MessageClient` para registros críticos.
* **Biometría:** Lectura continua de frecuencia cardíaca mediante `ExerciseClient` respaldado por un `ForegroundService` de tipo `health` y `OngoingActivity`.
* **Credenciales:** Configuración privada en `local.properties` inyectada en `BuildConfig` en el módulo móvil. Cero teclado en el reloj.

---

## 2. ARQUITECTURA GENERAL Y FLUJO DE DATOS

```
[ Servidor wger (Docker en LAN / Tailscale) ]
                    ▲
         HTTP REST v2 (JSON con Token)
                    │
            [ Android Mobile ]  <-- Posee VPN/Tailscale y credenciales
                    ▲
       Google Play Services: DataClient (PutDataMapRequest + .setUrgent())
                    │
            [ Pixel Watch 3 ]   <-- 100% autónomo durante el entrenamiento
              ├── UI: Wear Compose Material 3 (1.5.0)
              ├── Persistencia: Room Database local
              ├── Biometría: ExerciseClient (Health Services)
              └── Background: ForegroundService (type="health") + OngoingActivity

```

> **Regla crítica de sincronización:** Ambos módulos (`:wear` y `:mobile`) deben compartir exactamente el mismo `applicationId` (ej. `com.wger.companion`) y estar firmados con la misma clave criptográfica (keystore de debug idéntico); de lo contrario, `DataClient` descarta los paquetes silenciosamente.

---

## 3. CATÁLOGO DE DEPENDENCIAS Y VERSIONES (`gradle/libs.versions.toml`)

Configuración validada para evitar colisiones de memoria en Linux y garantizar soporte nativo para Wear OS 5:

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
wearCompose = "1.5.0"
wearOngoing = "1.1.0"
playServicesWearable = "18.2.0"
healthServices = "1.1.0-alpha04"
room = "2.6.1"
coroutines = "1.9.0"
ktor = "3.0.1"

[libraries]
# Wear Compose Material 3 (Descartar compose-material 1.x)
wear-compose-material3 = { group = "androidx.wear.compose", name = "compose-material3", version.ref = "wearCompose" }
wear-compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearCompose" }
wear-compose-navigation = { group = "androidx.wear.compose", name = "compose-navigation", version.ref = "wearCompose" }
wear-ongoing = { group = "androidx.wear", name = "wear-ongoing", version.ref = "wearOngoing" }

# Capa de datos y sensores
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }
health-services-client = { group = "androidx.health", name = "health-services-client", version.ref = "healthServices" }

# Persistencia local Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Corrutinas
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }

# Cliente HTTP Móvil (Ktor)
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }

```

---

## 4. ESQUEMA DE BASE DE DATOS LOCAL ROOM (`:wear`)

Diseñado para soportar secuencias lineales de entrenamiento y sincronización en ráfaga:

```kotlin
package com.wger.wear.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "routine_cache")
data class RoutineCacheEntity(
    @PrimaryKey val routineId: Long,
    val name: String,
    val description: String,
    val isCurrentActive: Boolean
)

@Entity(
    tableName = "routine_exercise_slot",
    indices = [Index(value = ["routineId", "executionOrder"])]
)
data class RoutineExerciseSlotEntity(
    @PrimaryKey val slotEntryId: Long,
    val routineId: Long,
    val exerciseName: String,
    val setNumber: Int,
    val totalSetsForExercise: Int,
    val executionOrder: Int,
    val targetReps: Int,
    val defaultWeightKg: Float,
    val restDurationSeconds: Int
)

@Entity(tableName = "logged_workout_session")
data class LoggedWorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val localSessionId: Long = 0,
    val routineId: Long,
    val startTimestampMs: Long,
    val endTimestampMs: Long = 0,
    val avgHeartRateBpm: Int = 0,
    val syncStatus: String // PENDING, SYNCING, SYNCED
)

@Entity(
    tableName = "logged_set_entry",
    foreignKeys = [
        ForeignKey(
            entity = LoggedWorkoutSessionEntity::class,
            parentColumns = ["localSessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class LoggedSetEntryEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val sessionId: Long,
    val slotEntryId: Long,
    val exerciseName: String,
    val completedReps: Int,
    val weightUsedKg: Float,
    val completedTimestampMs: Long
)

```

---

## 5. REQUISITOS DEL MANIFIESTO Y SERVICIOS EN BACKGROUND (`:wear`)

En Wear OS 5, leer sensores con la pantalla apagada requiere imperativamente el tipo `health`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <uses-permission android:name="android.permission.BODY_SENSORS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="wger Tracker"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">

        <!-- Servicio de monitorización biométrica -->
        <service
            android:name=".service.WorkoutTrackingService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="health" />

        <!-- Listener de Data Layer para recibir rutinas desde el móvil -->
        <service
            android:name=".datalayer.WearDataLayerListenerService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
                <data
                    android:host="*"
                    android:pathPrefix="/wger"
                    android:scheme="wear" />
            </intent-filter>
        </service>

        <activity
            android:name=".presentation.MainActivity"
            android:exported="true"
            android:taskAffinity=""
            android:theme="@android:style/Theme.DeviceDefault">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>

```

---

## 6. LÓGICA DE SINCRONIZACIÓN ASÍNCRONA (`WearSyncManager.kt`)

Uso obligatorio de `DataClient` con `setUrgent()` para evitar que el kernel de Wear OS 5 posponga la replicación hasta 30 minutos:

```kotlin
package com.wger.wear.datalayer

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wger.wear.data.local.LoggedSetEntryEntity
import com.wger.wear.data.local.LoggedWorkoutSessionEntity
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class WearSyncManager(context: Context) {
    private val dataClient: DataClient = Wearable.getDataClient(context)

    suspend fun dispatchSessionToPhone(
        session: LoggedWorkoutSessionEntity,
        sets: List<LoggedSetEntryEntity>
    ): Boolean {
        return try {
            val requestUri = "/wger/completed_session/${session.localSessionId}"
            val putDataMapRequest = PutDataMapRequest.create(requestUri).apply {
                val setsArray = JSONArray()
                sets.forEach { set ->
                    val setObj = JSONObject().apply {
                        put("slotEntryId", set.slotEntryId)
                        put("reps", set.completedReps)
                        put("weightKg", set.weightUsedKg.toDouble())
                        put("timestamp", set.completedTimestampMs)
                    }
                    setsArray.put(setObj)
                }

                dataMap.putLong("localSessionId", session.localSessionId)
                dataMap.putLong("routineId", session.routineId)
                dataMap.putLong("startTimestamp", session.startTimestampMs)
                dataMap.putLong("endTimestamp", session.endTimestampMs)
                dataMap.putInt("avgHeartRate", session.avgHeartRateBpm)
                dataMap.putString("setsJson", setsArray.toString())
                
                // CRÍTICO: Obliga a Wear OS 5 a no encolar el paquete por ahorro de batería
                setUrgent()
            }

            val putDataRequest = putDataMapRequest.asPutDataRequest()
            dataClient.putDataItem(putDataRequest).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

```

---

## 7. MAPEO DE LA API REST DE WGER (`:mobile`)

El módulo del teléfono consume los endpoints de wger resolviendo la secuencia calculada:

1. **Obtener rutina del día:**
* `GET /api/v2/routine/{id}/date-sequence-gym/`
* Devuelve la secuencia lineal aplanada (ejercicios, series pautadas, repeticiones y descanso).
* El móvil empaqueta este JSON y lo envía al reloj vía `/wger/routine_update`.


2. **Registrar sesión completada:**
* Al recibir `/wger/completed_session/*`, el servicio móvil:
* Crea la sesión: `POST /api/v2/workoutsession/` con `date`, `notes` (incluyendo pulso medio y duración) e `impression`.
* Registra cada serie: `POST /api/v2/workoutlog/` con `session`, `slot_entry`, `reps` y `weight`.
* Envía confirmación ACK de vuelta al reloj para marcar el estado en Room como `SYNCED`.





---

## 8. INSTRUCCIONES DE DESPLIEGUE DIRECTO (LINUX CLI)

Comandos para compilar e instalar directamente por terminal:

```bash
# 1. Emparejar el Pixel Watch 3 (solo la primera vez)
adb pair 192.168.1.XX:PUERTO_PAIRING CODIGO_PIN

# 2. Conectar al reloj vía WiFi
adb connect 192.168.1.XX:PUERTO_ADB

# 3. Compilar e instalar ambos módulos
# Móvil conectado por USB:
./gradlew :mobile:installDebug

# Reloj conectado por WiFi:
./gradlew :wear:installDebug

```