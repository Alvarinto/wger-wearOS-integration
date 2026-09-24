package com.wger.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.wger.wear.data.local.AppDatabase
import com.wger.wear.data.local.LoggedWorkoutSessionEntity
import com.wger.wear.datalayer.WearSyncManager
import com.wger.wear.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WorkoutTrackingService : Service(), SensorEventListener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var sensorManager: SensorManager? = null
    private var heartRateSensor: Sensor? = null

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    private val heartRateReadings = mutableListOf<Int>()
    private var timerJob: Job? = null
    private var startTimestampMs: Long = 0

    inner class LocalBinder : Binder() {
        fun getService(): WorkoutTrackingService = this@WorkoutTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    }

    fun startTracking(routineId: Long, onSessionCreated: (Long) -> Unit) {
        serviceScope.launch {
            startTimestampMs = System.currentTimeMillis()
            heartRateReadings.clear()

            val db = AppDatabase.getInstance(applicationContext)
            val session = LoggedWorkoutSessionEntity(
                routineId = routineId,
                startTimestampMs = startTimestampMs,
                syncStatus = "PENDING"
            )
            val sessionId = db.workoutSessionDao().insertSession(session)
            _activeSessionId.value = sessionId

            // Registro de sensor de ritmo cardíaco
            heartRateSensor?.let {
                sensorManager?.registerListener(
                    this@WorkoutTrackingService,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }

            // Notificación Foreground con OngoingActivity (Wear OS 5)
            val notification = buildOngoingNotification("Entrenamiento en curso", sessionId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Temporizador de duración
            timerJob?.cancel()
            timerJob = serviceScope.launch {
                while (isActive) {
                    delay(1000)
                    _elapsedSeconds.value = (System.currentTimeMillis() - startTimestampMs) / 1000
                }
            }

            launch(Dispatchers.Main) {
                onSessionCreated(sessionId)
            }
        }
    }

    fun finishTracking(onComplete: (LoggedWorkoutSessionEntity) -> Unit) {
        val sessionId = _activeSessionId.value ?: return

        serviceScope.launch {
            timerJob?.cancel()
            sensorManager?.unregisterListener(this@WorkoutTrackingService)

            val endTimestamp = System.currentTimeMillis()
            val avgHr = if (heartRateReadings.isNotEmpty()) {
                heartRateReadings.average().toInt()
            } else {
                0
            }

            val db = AppDatabase.getInstance(applicationContext)
            val existing = db.workoutSessionDao().getSessionById(sessionId)
            if (existing != null) {
                val updated = existing.copy(
                    endTimestampMs = endTimestamp,
                    avgHeartRateBpm = avgHr
                )
                db.workoutSessionDao().updateSession(updated)

                val sets = db.workoutSessionDao().getSetsForSession(sessionId)

                // Replicación inmediata al móvil usando DataClient con .setUrgent()
                val syncManager = WearSyncManager(applicationContext)
                syncManager.dispatchSessionToPhone(updated, sets)

                launch(Dispatchers.Main) {
                    onComplete(updated)
                }
            }

            _activeSessionId.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val bpm = event.values.firstOrNull()?.toInt() ?: 0
            if (bpm > 0) {
                _currentHeartRate.value = bpm
                heartRateReadings.add(bpm)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Entrenamiento wger",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado activo de la sesión de entrenamiento"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(title: String, sessionId: Long): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("activeSessionId", sessionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Grabando biometría y series...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        val ongoingActivityStatus = Status.Builder()
            .addTemplate("Entrenamiento wger")
            .build()

        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notificationBuilder)
            .setStaticIcon(android.R.drawable.ic_media_play)
            .setTouchIntent(pendingIntent)
            .setStatus(ongoingActivityStatus)
            .build()
            .apply(applicationContext)

        return notificationBuilder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "wger_workout_channel"
        const val NOTIFICATION_ID = 1001
    }
}
