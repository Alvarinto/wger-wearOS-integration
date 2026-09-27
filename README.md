# wger Wear OS & Mobile Companion ⌚📱

[![Wear OS](https://img.shields.io/badge/Wear%20OS-5.0%20(API%2034)-4285F4?logo=wearos&logoColor=white)](https://developer.android.com/wear)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![wger](https://img.shields.io/badge/wger-REST%20API%20v2-2ecc71)](https://wger.de)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Entrena en el gimnasio con el **Pixel Watch 3** sin conexión y vuelca la sesión a tu servidor **[wger](https://wger.de)** autoalojado al terminar.

El reloj guarda la rutina y todo lo que registras (series, repeticiones, peso y pulso). Al acabar, el móvil, que es el que tiene acceso a tu servidor por LAN o Tailscale, sube la sesión a wger.

---

## 🏗️ Arquitectura

```
[ Servidor wger (Docker, LAN / Tailscale) ]
                    ▲
         HTTP REST v2 (Token)
                    │
        [ Móvil Android — :mobile ]   ← tiene la VPN y las credenciales
                    ▲
     Wearable Data Layer (DataClient + setUrgent)
                    │
        [ Pixel Watch 3 — :wear ]     ← funciona offline durante el entrenamiento
          ├── UI: Wear Compose Material 3
          ├── Datos: Room
          ├── Pulso: sensor de ritmo cardíaco (SensorManager)
          └── Segundo plano: foreground service "health" + OngoingActivity
```

- **Reloj (`:wear`):** guarda la rutina activa en Room. Durante el entrenamiento no necesita ni Internet ni el móvil.
- **Móvil (`:mobile`):** hace de pasarela. Descarga la rutina de wger y se la manda al reloj; recibe las sesiones terminadas, las sube a wger y confirma al reloj.

## ✨ Funciones

- ⌚ **En el reloj:** series en orden con reps y peso objetivo ajustables, temporizador de descanso con vibración (+30 s o saltar), pulso continuo con la pantalla apagada, acceso directo al entrenamiento desde la esfera (`OngoingActivity`) y resumen final.
- ⚡ **Sincronización:** `DataClient` con `setUrgent()`, para que Wear OS no retrase el envío por ahorro de batería.
- 🔒 **Sin teclear nada en el reloj:** la URL y el token de wger viven en `local.properties` y se compilan dentro de la app del móvil.
- 📋 **Importador de rutinas:** `scripts/import_routine.py` crea en wger una rutina escrita en JSON.

## 🚀 Puesta en marcha

### 1. Requisitos

- Android SDK (API 35 para compilar) con `platform-tools` (`adb`).
- JDK 17 o superior.
- Servidor wger con la API REST v2 accesible desde el móvil y un token de API (en wger: *Ajustes → API key*).

### 2. Credenciales

```bash
cp local.properties.example local.properties
```

```properties
sdk.dir=/home/usuario/Android/Sdk
wger.server.url="https://tu-servidor-wger"
wger.api.token="tu_token_de_api"
```

`local.properties` está en `.gitignore`: nunca lo subas.

### 3. Compilar e instalar

> ⚠️ **Compila los dos APK en la misma máquina.** El móvil y el reloj deben tener el mismo `applicationId` (`com.wger.companion`) y la misma firma. Si no, el Data Layer descarta los mensajes **sin dar ningún error**. En debug se usa `~/.android/debug.keystore`.

**Móvil (USB):**
```bash
./gradlew :mobile:installDebug
```

**Reloj (ADB WiFi):**
1. En el reloj: *Ajustes → Opciones para desarrolladores →* activa **Depuración ADB** y **Depuración inalámbrica**.
2. Empareja (solo la primera vez) y conecta:
   ```bash
   adb pair <IP_RELOJ>:<PUERTO_EMPAREJAMIENTO> <CÓDIGO>
   adb connect <IP_RELOJ>:<PUERTO_ADB>
   ```
3. Instala:
   ```bash
   ANDROID_SERIAL=<IP_RELOJ>:<PUERTO_ADB> ./gradlew :wear:installDebug
   ```

`installDebug` instala en **todos** los dispositivos conectados. Si tienes el móvil y el reloj conectados a la vez, pon `ANDROID_SERIAL` también al instalar en el móvil (el serial sale en `adb devices`).

## 🏋️ Uso

1. **Importa la rutina a wger** (opcional; también puedes crearla desde la web de wger). Ejecútalo desde la raíz del repo, porque lee `local.properties`:
   ```bash
   python3 scripts/import_routine.py rutina.json
   ```
   Tienes el formato en [`rutina.json`](rutina.json). Los ejercicios se buscan por nombre en un mapa interno del script; **un nombre que no esté en ese mapa se importa como "push-up"**, así que revisa la rutina en wger después de importarla.
2. **En el móvil:** *Probar conexión HTTP → Obtener Rutinas de wger → Enviar Rutina al Reloj*. También puedes pulsar *Pedir rutina al móvil* desde el reloj.
3. **En el reloj:** *Iniciar entrenamiento*, registra cada serie y pulsa *Finalizar sesión*. La sesión se sube a wger en cuanto el móvil la recibe.

## ⚠️ Limitaciones conocidas

- Se envía al reloj el **primer día de la rutina que tiene ejercicios**, no el que toca hoy.
- Si la subida a wger falla (servidor inaccesible, por ejemplo), la sesión se queda pendiente en el reloj y **no se reintenta sola** todavía ([#3](https://github.com/Alvarinto/wger-wearOS-integration/issues/3)).
- El entreno en curso vive en memoria: si Android cierra la pantalla o el servicio a mitad de entreno, se puede perder el progreso.
- El importador asigna los ejercicios con un mapa fijo; **los que no están en él se importan como push-up**.

Detalle en [`specs.md`](specs.md).

## 🗺️ Hoja de ruta

Por fases y en este orden: cada una deja la app funcionando y prepara la siguiente.

| Fase | Qué ganas | Qué cambia por dentro |
|---|---|---|
| **0. Cimientos** (sin funciones nuevas, [#4](https://github.com/Alvarinto/wger-wearOS-integration/issues/4)) | Un entreno sobrevive a que se apague la pantalla, a que muera el servicio o a que se cierre la app, y continúa donde ibas. Las sesiones pendientes se reintentan ([#3](https://github.com/Alvarinto/wger-wearOS-integration/issues/3)). | El estado del entreno pasa a Room y una sola clase (`WorkoutRepository`) lo modifica. El servicio se arranca de verdad. La interfaz solo muestra y llama. Los listeners del Data Layer no cortan su trabajo a medias. |
| **1. Elegir entreno** | El reloj guarda todos los días de la rutina activa, propone el de hoy y permite empezar cualquiera. Desde el móvil eliges y envías un día concreto. Puedes saltar series y ejercicios. | BD v3: días y posición dentro de la sesión (`AutoMigration`). Versión 2 del contrato `routine_update`, con todos los días y su fecha. El importador crea los días de descanso según `day_of_week`; hoy lo ignora, y wger reparte los 4 días en un ciclo seguido desde la fecha de importación (el "Lunes" cae en jueves, lunes, viernes, martes…). |
| **2. Entrenar desde el móvil** | El móvil deja de ser solo una pasarela: puedes entrenar en él sin conexión y la sesión se sube a wger al terminar. | Nuevo módulo `:core` con la BD y `WorkoutRepository`, compartido por las dos apps. En el móvil la sesión se sube con `SessionUploader`; en el reloj, con el Data Layer. |
| **3. Interfaz** | Rediseño del reloj y del móvil. Pantalla de historial en los dos para ver qué entrenos están sincronizados con wger y cuáles pendientes. | Solo pantallas. Tras la fase 0 se puede hacer por partes, sin riesgo para los datos. |
| **4. Carrera** | Por definir. | Primero, una investigación: qué acepta wger para una carrera (distancia y tiempo; hoy *Running* se registra como series de "repeticiones") y si usar `ExerciseClient` de Health Services para el GPS y el ritmo. Después se decide el alcance. |

## 🩺 Solución de problemas

| Síntoma | Causa probable |
|---|---|
| El reloj no recibe la rutina y el móvil no da error | APKs firmados con claves distintas, o `applicationId` distinto |
| *Probar conexión HTTP* falla | La VPN o Tailscale están apagados en el móvil, o la URL o el token están mal en `local.properties` (tras corregirlos, recompila) |
| El pulso se queda en 0 | No se concedió el permiso de sensores corporales en el reloj |

## 🛠️ Tecnologías

Kotlin 2.0.21 · Gradle 8.10.2 (version catalog en `gradle/libs.versions.toml`) · Wear Compose Material 3 1.5.0 · Wear Ongoing 1.1.0 · Play Services Wearable 18.2.0 · Room 2.6.1 + KSP · Ktor 3.0.1 (OkHttp + kotlinx.serialization) · Jetpack Compose Material 3 (móvil).

## 🤖 Desarrollo con agentes de IA

Las instrucciones para Claude Code, Codex y similares están en [`AGENTS.md`](AGENTS.md).

## 📄 Licencia

[Apache 2.0](LICENSE). Inspirado en el ecosistema de fitness libre de [wger Workout Manager](https://github.com/wger-project/wger).
