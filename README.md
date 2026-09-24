# wger Wear OS & Mobile Companion ⌚📱

[![Wear OS](https://img.shields.io/badge/Wear%20OS-5.0%20(API%2034)-4285F4?logo=wearos&logoColor=white)](https://developer.android.com/wear)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![wger](https://img.shields.io/badge/wger-REST%20API%20v2-2ecc71)](https://wger.de)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Cliente autónomo para **Google Pixel Watch 3 (Wear OS 5)** y relay móvil complementario para la plataforma de entrenamiento libre y autoalojada **[wger](https://wger.de)**.

Permite entrenar en el gimnasio de forma **100% offline** directamente desde el reloj con registro biométrico de pulso cardíaco en tiempo real, temporizadores hápticos de descanso y sincronización automática en ráfaga con tu servidor autoalojado al terminar la sesión.

---

## 🏗️ Arquitectura del Sistema

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

* **Reloj autónomo (`:wear`):** Guarda la rutina activa en una base de datos local **Room**. Durante el entrenamiento no requiere conexión a Internet ni al teléfono. Registra series, repeticiones, peso y pulso medio.
* **Relay móvil (`:mobile`):** Actúa como pasarela segura entre el reloj y tu servidor wger (compatible con redes privadas **Tailscale** o VPNs). Escucha las sesiones finalizadas mediante `WearableListenerService` y las sube automáticamente a wger.

---

## ✨ Características Principales

* ⌚ **Diseñado para Pixel Watch 3 (Wear OS 5):**
  * Interfaz moderna en **Wear Compose Material 3** optimizada para pantallas circulares.
  * **Servicio Foreground de salud (`type="health"`):** Lectura continua de frecuencia cardíaca (BPM) sin que la pantalla se apague ni el sistema operativo cierre el proceso.
  * **OngoingActivity:** Notificación persistente para volver al entrenamiento activo desde cualquier carátula o menú.
  * **Temporizador de descanso interactivo:** Cuenta atrás automática entre series con retroalimentación háptica (vibración física) al concluir el descanso.
* ⚡ **Replicación Inmediata con `DataClient`:**
  * Uso prioritario de `.setUrgent()` para evitar que el kernel de Wear OS 5 aplace la sincronización para ahorrar batería.
* 🔒 **Seguridad y Privacidad:**
  * Cero teclados en el reloj: Toda la autenticación y tokens residen exclusivamente en `local.properties` del móvil y se inyectan en `BuildConfig` en tiempo de compilación.
* 📋 **Importador de Rutinas JSON:**
  * Incluye un script en Python (`scripts/import_routine.py`) para parsear e importar cualquier rutina estructurada en formato JSON directamente a tu servidor de wger.

---

## 🚀 Puesta en Marcha

### 1. Requisitos Previos
* **Android SDK** (API 34 o superior) con `platform-tools` (`adb`).
* **Java 17 o 21** (`openjdk`).
* Servidor wger con API REST v2 activa (LAN o Tailscale).

### 2. Configurar Credenciales
Copia la plantilla de configuración e ingresa tu URL y Token de wger:

```bash
cp local.properties.example local.properties
```

Edita `local.properties`:
```properties
sdk.dir=/home/usuario/Android/Sdk
wger.server.url="https://tu-servidor-wger.com"
wger.api.token="tu_token_de_api_wger"
```

### 3. Compilar e Instalar

#### En el Teléfono (por USB):
```bash
./gradlew :mobile:installDebug
```

#### En el Pixel Watch 3 (vía WiFi):
1. En el reloj: **Ajustes** > **Opciones para desarrolladores** > Activa **Depuración ADB** y **Depuración inalámbrica**.
2. Empareja y conecta desde tu terminal:
   ```bash
   adb pair 192.168.1.XX:PUERTO_PAIRING CODIGO_PIN
   adb connect 192.168.1.XX:PUERTO_ADB
   ```
3. Instala el APK en el reloj:
   ```bash
   adb -s 192.168.1.XX:PUERTO_ADB install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```

---

## 📥 Importar Rutinas a wger desde JSON

Puedes definir tus días, ejercicios y descansos en un archivo JSON (ver ejemplo en [`rutina.json`](rutina.json)):

```bash
python3 scripts/import_routine.py rutina.json
```

Una vez importada:
1. Abre la app en el teléfono y pulsa **"Obtener Rutinas de wger"**.
2. Pulsa **"Enviar Rutina al Reloj"**.
3. ¡Tu Pixel Watch 3 se actualizará automáticamente con las series del día!

---

## 🛠️ Tecnologías Utilizadas

* **Lenguaje:** Kotlin 2.0.21
* **Build System:** Gradle 8.10.2 con Version Catalogs (`libs.versions.toml`)
* **Wear OS:** Wear Compose Material 3 1.5.0, Wear Ongoing 1.1.0, Health Services Client
* **Data Layer:** Google Play Services Wearable 18.2.0
* **Persistencia:** Room Database 2.6.1 + KSP
* **Networking Móvil:** Ktor Client 3.0.1 (OkHttp Engine + Kotlinx Serialization)

---

## 📄 Licencia

Este proyecto está distribuido bajo la licencia [Apache 2.0](LICENSE).
Inspirado por el ecosistema de fitness libre de [wger Workout Manager](https://github.com/wger-project/wger).
