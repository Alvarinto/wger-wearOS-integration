# Especificación técnica — Cliente wger para Pixel Watch 3 y relay móvil

Qué debe hacer el sistema y por qué. El **cómo** está en el código; el contrato del Data Layer y las reglas de implementación, en `AGENTS.md`. Este documento no copia código a propósito: las copias se desfasan.

Estado: ✅ hecho · ⚠️ parcial / se desvía · ❌ pendiente

---

## 1. Objetivo

Registrar entrenamientos de gimnasio desde el **Pixel Watch 3** sin depender del móvil ni de la red durante la sesión, y volcarlos después a una instancia **autoalojada de wger** que solo es accesible desde el móvil (LAN o Tailscale).

## 2. Contexto y restricciones

| Aspecto | Decisión |
|---|---|
| Reloj | Pixel Watch 3, Wear OS 5 (Android 14, API 34). `:wear` minSdk 30 |
| Móvil | Android, minSdk 26. Único nodo con acceso al servidor |
| Servidor | wger en Docker, API REST v2, autenticación `Authorization: Token …` |
| Entorno de desarrollo | Linux (Arch/CachyOS), CLI de Android, ADB WiFi al reloj |
| Transporte reloj ⇄ móvil | Wearable Data Layer (Google Play Services) |
| Credenciales | `local.properties` → `BuildConfig` del móvil. Nada se teclea en el reloj |

**Por qué un relay:** el reloj no tiene acceso a Tailscale ni a la LAN del servidor de forma fiable, y meter un token en el reloj obligaría a teclearlo o a sincronizarlo. El móvil ya tiene la VPN y las credenciales.

**Por qué `DataClient` y no `MessageClient`:** `DataClient` persiste el `DataItem` y lo entrega cuando el otro nodo vuelve a estar disponible; `MessageClient` se pierde si el otro nodo no está conectado. Los datos del entrenamiento no se pueden perder.

**Por qué `setUrgent()`:** sin él, Wear OS agrupa las escrituras del Data Layer para ahorrar batería y puede retrasar la entrega varios minutos (hasta ~30).

## 3. Requisitos funcionales

### Reloj (`:wear`)

| ID | Requisito | Estado |
|---|---|---|
| W1 | Guardar la rutina activa en Room y funcionar sin conexión durante toda la sesión. Los datos locales sobreviven a las actualizaciones de la app | ✅ |
| W2 | Pedir la rutina al móvil desde el reloj | ✅ `/wger/request_routine` |
| W3 | Recorrer las series en orden, con reps y peso objetivo, y ajustar reps/kg antes de registrar | ✅ |
| W4 | Temporizador de descanso entre series con vibración al terminar (+30 s / saltar) | ✅ |
| W5 | Medir el pulso de forma continua con la pantalla apagada: foreground service `health` + `OngoingActivity` | ⚠️ Usa `SensorManager` (`TYPE_HEART_RATE`), no `ExerciseClient` de Health Services |
| W6 | Resumen al terminar: duración, series y pulso medio | ✅ |
| W7 | Enviar la sesión terminada al móvil y marcarla `SYNCED` al recibir el ACK | ✅ |
| W8 | Reintentar las sesiones que se quedaron en `PENDING` | ❌ #3 |

### Móvil (`:mobile`)

| ID | Requisito | Estado |
|---|---|---|
| M1 | Probar la conexión con el servidor wger | ✅ |
| M2 | Obtener la rutina activa y su secuencia y enviarla al reloj | ⚠️ Envía el **primer día con ejercicios**, no el día de hoy |
| M3 | Al recibir una sesión, crearla en wger y registrar cada serie | ✅ |
| M4 | Mandar el ACK al reloj solo si la subida se ha completado | ✅ #2 |
| M5 | No duplicar la sesión en wger si llega dos veces | ✅ #1 |

### Herramientas

| ID | Requisito | Estado |
|---|---|---|
| T1 | Importar a wger una rutina escrita en JSON (`scripts/import_routine.py`) | ⚠️ Los ejercicios se resuelven con un mapa fijo; los desconocidos se importan como push-up (ID 1551) |

## 4. Flujos

### 4.1 Cargar la rutina en el reloj

1. El usuario pulsa **Obtener Rutinas de wger** y **Enviar Rutina al Reloj** en el móvil, o **Pedir rutina al móvil** en el reloj (`MessageClient` → `/wger/request_routine`).
2. El móvil llama a `GET /api/v2/routine/` y elige la rutina con `is_active`, o la primera si ninguna lo tiene.
3. El móvil llama a `GET /api/v2/routine/{id}/date-sequence-gym/` y lo aplana a una lista de series (una entrada por serie, con `executionOrder` y `exerciseId`). wger repite el `slot_entry_id` en todas las series de un ejercicio.
   - El nombre del ejercicio sale del `comment` del slot (el texto antes de `:`).
   - Si faltan campos, se usan valores por defecto: 10 reps, 60 s de descanso, 0 kg y `slotEntryId` sintético.
4. El móvil envía `/wger/routine_update` y el reloj sustituye su rutina en Room en una transacción.

### 4.2 Entrenar y sincronizar

1. **Iniciar** crea una `LoggedWorkoutSession` en estado `PENDING`, arranca el foreground service y empieza a leer el pulso.
2. Cada **Registrar serie** inserta un `LoggedSetEntry` y lleva al descanso.
3. **Finalizar** calcula el pulso medio, guarda la sesión y envía `/wger/completed_session/{localId}`.
4. El móvil hace `POST /api/v2/workoutsession/` con `datetime_start`, `datetime_end`, `notes` (duración y pulso medio) e `impression = 2`. wger devuelve el ID de la sesión como UUID. Después hace un `POST /api/v2/workoutlog/` por serie con `session`, `exercise` (obligatorio), `slot_entry`, `repetitions` y `weight`.
5. El móvil envía `/wger/session_synced/{localId}` y el reloj marca la sesión como `SYNCED`.

## 5. Modelo de datos local (Room, `:wear`)

Definición en `wear/src/main/java/com/wger/wear/data/local/Entities.kt`.

- `routine_cache`: la rutina activa (`isCurrentActive`).
- `routine_exercise_slot`: una fila por serie planificada. Clave `(routineId, executionOrder)`; incluye `exerciseId`.
- `logged_workout_session`: la sesión, con `syncStatus` (`PENDING` / `SYNCING` / `SYNCED`; `SYNCING` no se usa todavía).
- `logged_set_entry`: las series hechas, con FK a la sesión y borrado en cascada.

La BD está en la versión 2 (v1→v2: nueva clave de las series planificadas y columna `exerciseId`; lo cubre `MigrationTest`). Su esquema se exporta a `wear/schemas/` y se versiona en git. **Un cambio de esquema nunca borra datos:** sin `Migration`, la app falla al abrir en lugar de vaciar la BD (lo comprueba `AppDatabaseSchemaChangeTest`, en JVM y en el reloj). Los cambios de esquema se hacen con `@AutoMigration` (procedimiento en `AGENTS.md`, regla 5).

## 6. Deuda técnica conocida

1. Sincronización fiable, en issues de GitHub y por este orden (#1 idempotencia y #2 ACK solo si todo se sube ya hechos): falta #3 reintento de `PENDING`.
2. Elegir en M2 el día que toca hoy, no el primero con ejercicios.
3. Decidir sobre W5: migrar a `ExerciseClient` o quitar la dependencia `health-services-client`, que no se usa.
4. `import_routine.py`: buscar los ejercicios en la API de wger y fallar si no existen, en lugar de usar push-up por defecto.
5. Tests: hay base en `:wear` (`sharedTest`: BD, migraciones y `RoutineDao`) y en `:mobile` (`WgerApiClientTest` con `MockEngine`). Falta cubrir el mapeo del `DataMap` entre el reloj y el móvil.
6. No hay firma de release: ahora mismo solo funciona con la keystore de debug compartida.
