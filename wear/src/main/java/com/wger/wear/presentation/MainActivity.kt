package com.wger.wear.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import com.wger.wear.data.ActiveWorkout
import com.wger.wear.data.WorkoutRepository
import com.wger.wear.data.local.AppDatabase
import com.wger.wear.data.local.LoggedWorkoutSessionEntity
import com.wger.wear.datalayer.WearSyncManager
import com.wger.wear.presentation.theme.WgerWearTheme
import com.wger.wear.service.WorkoutTrackingService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var trackingService: WorkoutTrackingService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WorkoutTrackingService.LocalBinder
            trackingService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, WorkoutTrackingService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WgerWearTheme {
                AppScaffold(
                    modifier = Modifier.fillMaxSize(),
                    timeText = { TimeText() }
                ) {
                    WearAppRoot(
                        getService = { trackingService }
                    )
                }
            }
        }
    }
}

@Composable
fun WearAppRoot(
    getService: () -> WorkoutTrackingService?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val syncManager = remember { WearSyncManager(context) }
    val repository = remember { WorkoutRepository(db, { s, sets -> syncManager.dispatchSessionToPhone(s, sets) }) }

    val activeRoutine by db.routineDao().getActiveRoutine().collectAsState(initial = null)
    val slots by if (activeRoutine != null) {
        db.routineDao().getSlotsForRoutine(activeRoutine!!.routineId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    // Entreno en curso según Room: al reabrir la app (o si Android recrea la pantalla) se vuelve a él
    var loaded by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<ActiveWorkout?>(null) }
    LaunchedEffect(Unit) {
        repository.activeWorkout.collect {
            active = it
            loaded = true
        }
    }

    var finishedSession by remember { mutableStateOf<LoggedWorkoutSessionEntity?>(null) }
    var finishedSets by remember { mutableIntStateOf(0) }
    // ponytail: saltar el descanso vive en memoria; si se cierra la app durante el descanso, vuelve con el tiempo que quede
    var restSkippedForSet by rememberSaveable { mutableStateOf<Long?>(null) }

    var heartRate by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    // Sincronizar estados del servicio
    DisposableEffect(getService()) {
        val service = getService()
        val job = coroutineScope.launch {
            service?.currentHeartRate?.collect { hr ->
                heartRate = hr
            }
        }
        val timerJob = coroutineScope.launch {
            service?.elapsedSeconds?.collect { secs ->
                elapsedSeconds = secs
            }
        }
        onDispose {
            job.cancel()
            timerJob.cancel()
        }
    }

    // Con un entreno en curso, el servicio debe estar en marcha (si murió el proceso, recupera pulso y notificación)
    val activeSessionId = active?.session?.localSessionId
    LaunchedEffect(activeSessionId) {
        if (activeSessionId != null) {
            context.startService(Intent(context, WorkoutTrackingService::class.java))
        }
    }

    fun startWorkout(routineId: Long) {
        coroutineScope.launch { repository.start(routineId) }
    }

    // Permisos biométricos
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        activeRoutine?.let { if (isGranted) startWorkout(it.routineId) }
    }

    fun requestStart() {
        val routine = activeRoutine ?: return
        val hasSensorPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSensorPermission) {
            startWorkout(routine.routineId)
        } else {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    fun finishWorkout() {
        finishedSets = active?.sets?.size ?: 0
        val onFinished: (LoggedWorkoutSessionEntity?) -> Unit = { finishedSession = it }
        // Sin servicio enlazado no hay media de pulso, pero el entreno se cierra igual
        getService()?.finishTracking(onFinished)
            ?: coroutineScope.launch { onFinished(repository.finish(avgHeartRateBpm = 0)) }
    }

    if (!loaded) return // aún no se sabe si hay un entreno en curso

    val workout = active
    val summary = finishedSession
    when {
        summary != null -> SummaryScreen(
            // Se actualiza al llegar el ACK del móvil
            session = remember(summary.localSessionId) { db.workoutSessionDao().getSessionFlow(summary.localSessionId) }
                .collectAsState(initial = summary).value ?: summary,
            totalSets = finishedSets,
            onDone = { finishedSession = null }
        )

        workout == null -> RoutineScreen(
            routine = activeRoutine,
            slots = slots,
            onStartWorkout = { requestStart() },
            onRefreshRoutine = {
                coroutineScope.launch {
                    syncManager.requestRoutineRefresh()
                }
            }
        )

        else -> {
            val lastSet = workout.sets.lastOrNull()
            val restEndsAt = workout.restEndsAtMs
            val slot = workout.currentSlot
            when {
                lastSet != null && restEndsAt != null && restSkippedForSet != lastSet.setId &&
                    System.currentTimeMillis() < restEndsAt -> key(lastSet.setId) {
                    RestTimerScreen(
                        initialSeconds = ((restEndsAt - System.currentTimeMillis()) / 1000).toInt(),
                        onRestFinished = { restSkippedForSet = lastSet.setId }
                    )
                }

                // Sin series pendientes: se cierra solo, como al acabar la última serie
                slot == null -> LaunchedEffect(workout.session.localSessionId) { finishWorkout() }

                else -> WorkoutScreen(
                    currentSlot = slot,
                    slotIndex = workout.sets.size,
                    totalSlots = workout.slots.size,
                    heartRateBpm = heartRate,
                    elapsedSeconds = elapsedSeconds,
                    onCompleteSet = { reps, weightKg ->
                        coroutineScope.launch { repository.logSet(reps, weightKg) }
                    },
                    onFinishWorkout = { finishWorkout() }
                )
            }
        }
    }
}
