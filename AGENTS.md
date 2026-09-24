# AGENTS.md — wger Wear OS & Mobile Companion

Guía de arquitectura, directrices y contexto para agentes de Inteligencia Artificial y desarrolladores que trabajen en este repositorio.

---

## 1. Visión General del Proyecto

Este proyecto es un cliente y relay para la plataforma de entrenamiento libre **wger**, diseñado específicamente para el **Google Pixel Watch 3 (Wear OS 5 / Android 14 / API 34)** y un **teléfono Android complementario**.

### Topología del Sistema
```
[ Servidor wger (Docker en LAN / Tailscale) ]
                    ▲
         HTTP REST v2 (Token Auth)
                    │
            [ Android Mobile (:mobile) ]  <-- Posee VPN/Tailscale y credenciales
                    ▲
       Google Play Services: DataClient (.setUrgent())
                    │
            [ Pixel Watch 3 (:wear) ]     <-- 100% autónomo durante el entrenamiento
              ├── UI: Wear Compose Material 3 (1.5.0)
              ├── Persistencia: Room Database local
              ├── Biometría: SensorManager / Health Services
              └── Background: ForegroundService (type="health") + OngoingActivity
```

---

## 2. Regla de Oro Crítica (Data Layer)

> **REGLA ABSOLUTA:** Los módulos `:mobile` y `:wear` **deben compartir exactamente el mismo `applicationId`** (`com.wger.companion`) y estar firmados con la **misma clave criptográfica** (keystore idéntico).
>
> De no cumplirse, Google Play Services Wearable Data Layer descartará los paquetes de forma silenciosa sin emitir ningún error explícito.

---

## 3. Estructura del Repositorio

* **`gradle/`**:
  * `libs.versions.toml`: Catálogo centralizado de versiones (AGP 8.7.2, Kotlin 2.0.21, Wear Compose Material 3 1.5.0, Ktor 3.0.1, Room 2.6.1).
* **`wear/` (Módulo Wear OS)**:
  * `data/local/`: Entidades Room (`RoutineCacheEntity`, `RoutineExerciseSlotEntity`, `LoggedWorkoutSessionEntity`, `LoggedSetEntryEntity`) y DAOs (`RoutineDao`, `WorkoutSessionDao`).
  * `datalayer/`: `WearSyncManager.kt` (envío de sesiones al móvil con `DataClient.setUrgent()`) y `WearDataLayerListenerService.kt` (escucha de rutinas y confirmaciones de sync).
  * `service/`: `WorkoutTrackingService.kt` (`ForegroundService` de tipo `health` con `OngoingActivity` para medición de ritmo cardíaco continuo sin apagado de pantalla).
  * `presentation/`: Pantallas Compose Material 3 (`RoutineScreen`, `WorkoutScreen`, `RestTimerScreen`, `SummaryScreen`).
* **`mobile/` (Módulo Relay Móvil)**:
  * `network/`: `WgerApiClient.kt` (cliente Ktor con OkHttp y serialización JSON hacia los endpoints REST de wger).
  * `datalayer/`: `PhoneSyncManager.kt` (envío de rutinas al reloj con `.setUrgent()`) y `MobileDataLayerListenerService.kt` (subida de sesiones a wger y envío de ACK al reloj).
  * `presentation/`: Panel de control en Jetpack Compose para probar conexión y forzar sincronización.
* **`scripts/`**:
  * `import_routine.py`: Script CLI en Python para importar rutinas desde JSON a cualquier servidor wger.
* **`specs.md`**: Especificación técnica de referencia del proyecto.

---

## 4. Convenciones de Desarrollo

1. **Wear Compose y Pantallas Circulares:**
   * En `ScalingLazyColumn`, **nunca** colocar múltiples elementos `Text` dentro de un único bloque `item { }` sin envolverlos en un `Column(horizontalAlignment = Alignment.CenterHorizontally)`. De lo contrario, Compose los renderiza como un `Box` y se superponen.
   * Respetar los márgenes curvos del Pixel Watch con paddings laterales de al menos `12.dp`.
2. **Replicación Inmediata con `setUrgent()`:**
   * Cualquier `PutDataMapRequest` enviado mediante `DataClient` debe invocar explícitamente `.setUrgent()` para evitar que el kernel de Wear OS 5 postergue la sincronización por ahorro de batería.
3. **Manejo de Secretos:**
   * **NUNCA** commitear `local.properties`. Los tokens y URLs del servidor privado deben residir exclusivamente en `local.properties` e inyectarse en `BuildConfig` en tiempo de compilación.

---

## 5. Comandos de Compilación y Despliegue

```bash
# Compilar ambos módulos
./gradlew assembleDebug

# Instalar en el móvil (por USB)
./gradlew :mobile:installDebug

# Conectar e instalar en Pixel Watch 3 (WiFi ADB)
adb connect <WATCH_IP>:<WATCH_ADB_PORT>
adb -s <WATCH_IP>:<WATCH_ADB_PORT> install -r wear/build/outputs/apk/debug/wear-debug.apk

# Importar una rutina JSON a wger
python3 scripts/import_routine.py rutina.json
```
