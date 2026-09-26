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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import com.wger.wear.data.local.AppDatabase
import com.wger.wear.data.local.LoggedSetEntryEntity
import com.wger.wear.data.local.LoggedWorkoutSessionEntity
import com.wger.wear.datalayer.WearSyncManager
import com.wger.wear.presentation.theme.WgerWearTheme
import com.wger.wear.service.WorkoutTrackingService
import kotlinx.coroutines.launch

enum class ScreenState {
    ROUTINE_PREVIEW,
    ACTIVE_WORKOUT,
    REST_TIMER,
    SUMMARY
}

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

    val activeRoutine by db.routineDao().getActiveRoutine().collectAsState(initial = null)
    val slots by if (activeRoutine != null) {
        db.routineDao().getSlotsForRoutine(activeRoutine!!.routineId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    var screenState by remember { mutableStateOf(ScreenState.ROUTINE_PREVIEW) }
    var currentSlotIndex by remember { mutableIntStateOf(0) }
    var restDurationSeconds by remember { mutableIntStateOf(60) }
    var finishedSession by remember { mutableStateOf<LoggedWorkoutSessionEntity?>(null) }
    var completedSetsCount by remember { mutableIntStateOf(0) }

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

    // Permisos biométricos
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && activeRoutine != null) {
            val service = getService()
            service?.startTracking(activeRoutine!!.routineId) {
                currentSlotIndex = 0
                completedSetsCount = 0
                screenState = ScreenState.ACTIVE_WORKOUT
            }
        }
    }

    fun startWorkout() {
        val hasSensorPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSensorPermission) {
            val service = getService()
            activeRoutine?.let { routine ->
                service?.startTracking(routine.routineId) {
                    currentSlotIndex = 0
                    completedSetsCount = 0
                    screenState = ScreenState.ACTIVE_WORKOUT
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    fun finishWorkout() {
        val service = getService()
        service?.finishTracking { session ->
            finishedSession = session
            screenState = ScreenState.SUMMARY
        } ?: run {
            screenState = ScreenState.ROUTINE_PREVIEW
        }
    }

    when (screenState) {
        ScreenState.ROUTINE_PREVIEW -> {
            RoutineScreen(
                routine = activeRoutine,
                slots = slots,
                onStartWorkout = { startWorkout() },
                onRefreshRoutine = {
                    coroutineScope.launch {
                        syncManager.requestRoutineRefresh()
                    }
                }
            )
        }

        ScreenState.ACTIVE_WORKOUT -> {
            if (slots.isNotEmpty() && currentSlotIndex < slots.size) {
                val currentSlot = slots[currentSlotIndex]
                WorkoutScreen(
                    currentSlot = currentSlot,
                    slotIndex = currentSlotIndex,
                    totalSlots = slots.size,
                    heartRateBpm = heartRate,
                    elapsedSeconds = elapsedSeconds,
                    onCompleteSet = { reps, weightKg ->
                        coroutineScope.launch {
                            val service = getService()
                            val sessionId = service?.activeSessionId?.value ?: 1L
                            val loggedSet = LoggedSetEntryEntity(
                                sessionId = sessionId,
                                slotEntryId = currentSlot.slotEntryId,
                                exerciseId = currentSlot.exerciseId,
                                exerciseName = currentSlot.exerciseName,
                                completedReps = reps,
                                weightUsedKg = weightKg,
                                completedTimestampMs = System.currentTimeMillis()
                            )
                            db.workoutSessionDao().insertSet(loggedSet)
                            completedSetsCount++

                            if (currentSlot.restDurationSeconds > 0) {
                                restDurationSeconds = currentSlot.restDurationSeconds
                                screenState = ScreenState.REST_TIMER
                            } else {
                                if (currentSlotIndex + 1 < slots.size) {
                                    currentSlotIndex++
                                } else {
                                    finishWorkout()
                                }
                            }
                        }
                    },
                    onFinishWorkout = { finishWorkout() }
                )
            } else {
                finishWorkout()
            }
        }

        ScreenState.REST_TIMER -> {
            RestTimerScreen(
                initialSeconds = restDurationSeconds,
                onRestFinished = {
                    if (currentSlotIndex + 1 < slots.size) {
                        currentSlotIndex++
                        screenState = ScreenState.ACTIVE_WORKOUT
                    } else {
                        finishWorkout()
                    }
                }
            )
        }

        ScreenState.SUMMARY -> {
            finishedSession?.let { session ->
                SummaryScreen(
                    session = session,
                    totalSets = completedSetsCount,
                    onDone = {
                        screenState = ScreenState.ROUTINE_PREVIEW
                    }
                )
            } ?: run {
                screenState = ScreenState.ROUTINE_PREVIEW
            }
        }
    }
}
