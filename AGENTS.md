# AGENTS.md — wger Wear OS & Mobile Companion

Contexto e instrucciones para agentes de IA (Claude Code, Codex…) que trabajen en este repo. Visión de producto y requisitos: `specs.md`. Instalación para humanos: `README.md`.

## Qué es

Cliente de entrenamiento **wger** para **Pixel Watch 3** (Wear OS 5, API 34) con un **relay en el móvil**. El reloj funciona offline durante la sesión; el móvil es el único que habla con el servidor wger (LAN/Tailscale).

```
wger (REST v2, Token) ⇄ :mobile (Ktor)  ⇄  Data Layer (DataClient)  ⇄  :wear (Room + Compose)
```

## Mapa del código

| Módulo | Ruta | Contenido |
|---|---|---|
| `:wear` | `wear/src/main/java/com/wger/wear/` | |
| | `data/local/` | Room: `Entities.kt`, `Daos.kt`, `AppDatabase.kt` (versión 2; esquemas en `wear/schemas/`) |
| | `datalayer/` | `WearSyncManager` (envía sesiones, pide rutina) · `WearDataLayerListenerService` (recibe rutina y ACK) |
| | `service/` | `WorkoutTrackingService`: foreground `health` + `OngoingActivity`, pulso vía `SensorManager` |
| | `presentation/` | `MainActivity` (navegación) + `RoutineScreen`, `WorkoutScreen`, `RestTimerScreen`, `SummaryScreen` |
| `:mobile` | `mobile/src/main/java/com/wger/companion/mobile/` | |
| | `network/WgerApiClient.kt` | Ktor + OkHttp contra `/api/v2/` |
| | `data/model/WgerModels.kt` | DTOs serializables |
| | `datalayer/` | `PhoneSyncManager` (envía rutina y ACK) · `MobileDataLayerListenerService` (sube sesiones a wger) |
| | `presentation/MainActivity.kt` | Panel de control Compose |
| — | `scripts/import_routine.py` | Importa `rutina.json` a wger (solo stdlib) |

## Contrato del Data Layer

Si cambias un lado, cambia el otro en el mismo commit. Las claves van dentro de un `DataMap`; listas como JSON en un `String`.

| Ruta | Sentido | API | Claves |
|---|---|---|---|
| `/wger/routine_update` | móvil → reloj | `DataClient` | `routineId`, `routineName`, `routineDescription`, `slotsJson`, `timestamp` |
| `/wger/completed_session/{localId}` | reloj → móvil | `DataClient` | `localSessionId`, `routineId`, `startTimestamp`, `endTimestamp`, `avgHeartRate`, `setsJson` |
| `/wger/session_synced/{localId}` | móvil → reloj | `DataClient` | `sessionId`, `syncedAt` |
| `/wger/request_routine` | reloj → móvil | `MessageClient` | (sin payload) |

Cada elemento de `slotsJson` es **una serie**: `slotEntryId`, `exerciseId`, `exerciseName`, `setNumber`, `totalSetsForExercise`, `executionOrder`, `targetReps`, `defaultWeightKg` y `restDurationSeconds`. Cada elemento de `setsJson` lleva `slotEntryId`, `exerciseId`, `reps`, `weightKg` y `timestamp`.

**Contrato de la API de wger (2.x):**
- `slotEntryId` se **repite** en todas las series de un ejercicio: nunca lo uses como clave (en el reloj la clave es `(routineId, executionOrder)`).
- Los IDs de sesión y de log son **UUID** (`String`).
- `POST /workoutlog/` exige `exercise` y usa `repetitions`.
- `POST /workoutsession/` usa `datetime_start` y `datetime_end`; ya no existe `date`.

## Reglas que no se pueden romper

1. **Mismo `applicationId` (`com.wger.companion`) y misma firma en ambos módulos.** Si no, el Data Layer descarta los mensajes **sin error**. No hay `signingConfig`: se usa `~/.android/debug.keystore`, así que compila los dos APK en la misma máquina.
2. **Todo `PutDataMapRequest` lleva `.setUrgent()`.** Sin él, Wear OS puede retrasar la entrega hasta ~30 min.
3. **Datos críticos solo por `DataClient`**, nunca `MessageClient` (no persiste ni reintenta). `MessageClient` solo para peticiones desechables como `request_routine`.
4. **Secretos:** URL y token solo en `local.properties` (ignorado por git) → `BuildConfig.WGER_SERVER_URL` / `WGER_API_TOKEN`. Nunca los escribas en código, logs ni commits.
5. **La BD del reloj nunca se borra sola** (sin `fallbackToDestructiveMigration`; lo vigila `AppDatabaseSchemaChangeTest`). Guarda sesiones que no existen en ningún otro sitio. Para cambiar una entidad:
   1. Sube `version` en `AppDatabase`.
   2. Añade `autoMigrations = [AutoMigration(from = N, to = N + 1)]`. Si renombras o borras columnas, usa una `AutoMigrationSpec` o una `Migration` manual.
   3. Compila y commitea el nuevo `wear/schemas/.../N+1.json`. Los JSON de los esquemas no se editan a mano.
   4. Si falta la migración, la app se cierra al abrirse en lugar de perder datos.
6. **Wear Compose:** en `ScalingLazyColumn`, varios `Text` en un mismo `item {}` van dentro de `Column(horizontalAlignment = Alignment.CenterHorizontally)` (si no, se superponen). Padding lateral ≥ `12.dp` por la pantalla circular.

## Comandos

Usa siempre `-q`: la salida normal de Gradle desperdicia contexto. Nunca leas `build/`, `.gradle/` ni `.kotlin/`.

```bash
./gradlew -q assembleDebug                 # compilar ambos módulos (verificación mínima)
./gradlew -q :wear:compileDebugKotlin      # compilar solo un módulo, más rápido
./gradlew -q lint                          # Android Lint
# installDebug instala en TODOS los dispositivos conectados: con móvil y reloj a la vez, fija el destino
ANDROID_SERIAL=<SERIAL_MOVIL> ./gradlew -q :mobile:installDebug   # serial: `adb devices`
ANDROID_SERIAL=<IP>:<PUERTO>  ./gradlew -q :wear:installDebug     # reloj por ADB WiFi
python3 scripts/import_routine.py rutina.json                  # ejecutar desde la raíz del repo

./gradlew -q :wear:testDebugUnitTest       # tests JVM (Robolectric)
./gradlew -q :mobile:testDebugUnitTest     # tests de la API con MockEngine de Ktor (sin red)
# Tests en el reloj. SIN el flag, AGP desinstala la app al acabar y se borra la BD real del reloj
ANDROID_SERIAL=<IP>:<PUERTO> ./gradlew -q :wear:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
adb -s <IP>:<PUERTO> uninstall com.wger.companion.test          # limpiar el APK de test
```

**ADB WiFi con el reloj:** usa el `adb` del SDK (`~/Android/Sdk/platform-tools`), no el de `/usr/bin` (no tiene mDNS). Con **Tailscale activo en el PC**, `adb connect` o `adb pair` al reloj fallan (`No route to host` / `protocol fault`) aunque el reloj se anuncie por mDNS: desactívalo mientras depuras. El puerto cambia cada vez; `adb mdns services` muestra el actual.

Los tests de `wear/src/sharedTest/` corren en JVM y en el reloj; usan un nombre de BD propio (`AppDatabase.build(context, "…")`), nunca `wger_wear.db`. Cada versión de la BD lleva su test en `MigrationTest`. En `:mobile`, `WgerApiClient` recibe el `engine` de Ktor por parámetro, así que los tests le pasan un `MockEngine` con respuestas copiadas del servidor real. Con `-q`, si no sale nada es que todo ha ido bien; si falla, el motivo está en `*/build/test-results/**/*.xml`. Tras un cambio, deben pasar `assembleDebug`, `:wear:testDebugUnitTest` y `:mobile:testDebugUnitTest`.

## Estado real (no te fíes de la spec en esto)

- **Pulso:** `SensorManager` + `Sensor.TYPE_HEART_RATE`. La dependencia `health-services-client` está declarada pero **no se usa** (la spec pedía `ExerciseClient`).
- **Sincronización incompleta**, en issues de GitHub (van en este orden y dependen entre sí; el detalle está en cada issue, `gh issue view N`):
  1. #1 **Duplicados:** reprocesar un `completed_session` crea otra sesión en wger.
  2. #2 **ACK optimista:** se manda aunque falle el `POST` de alguna serie.
  3. #3 **Sin reintentos:** una sesión `PENDING` no se reenvía nunca (`getPendingSessions()` no se usa; `SYNCING` nunca se asigna).
- **`import_routine.py`:** los ejercicios que no están en su `exercise_map` se importan como el ID `1551` (push-up) sin avisar.
