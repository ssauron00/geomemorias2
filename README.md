# geomemorias2 — App Android de recordatorios geolocalizados

Versión nativa de la v1 web (geomemorias) para Android, desde API 28 (Android 9).

## Qué es
Guardas un recordatorio anclado a una coordenada + radio. La app avisa (notificación)
cuando pasas cerca (distancia <= radio). El recordatorio ya existe; la app solo
valida proximidad y notifica (regla de oro heredada de v1).

## Diferencia vs v1 web
- **v1 web**: polling adaptativo, no despierta en segundo plano real.
- **v2 Android**: Geofencing API de Play Services despierta la app al cruzar el
  radio, aunque esté cerrada. Proactividad real.

## Stack
- Kotlin + Android Gradle Plugin 8.2
- minSdk 28, targetSdk 34
- Room (persistencia local, device-only)
- OSMDroid 6.1.18 (mapa open-source, sin API key)
- Play Services Location (Geofencing)
- Android Auto Car App Library 1.4.0
- Foreground Service + TTS (modo conducción)
- SpeechRecognizer (comando de voz)

## Cómo abrir y correr (en tu máquina con Android Studio)
1. Abre Android Studio → "Open" → esta carpeta (geomemorias2).
2. Edita `local.properties` y pon tu SDK: `sdk.dir=C:\\...\\Android\\Sdk`
   (o `sdk.dir=/home/.../Android/Sdk`).
3. `Sync Project with Gradle Files`.
4. Conecta un dispositivo con Android 9+ (o un emulator API 28+).
5. `Run 'app'`.

## Estructura
```
geomemorias2/
├── build.gradle (root) / settings.gradle
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/geomemorias2/
│       │   ├── MainActivity.kt              UI principal: mapa OSMDroid + captura
│       │   ├── Reminder.kt                  modelo (Room @Entity)
│       │   ├── ReminderDao.kt               acceso a datos (Room)
│       │   ├── AppDatabase.kt               Room DB
│       │   ├── AppDatabaseProvider.kt       singleton DB
│       │   ├── ReminderAdapter.kt           RecyclerView adapter para lista
│       │   ├── GeoUtils.kt                  haversine + histeresis + polling
│       │   ├── GeofenceHelper.kt            registra geocercas (Play Services)
│       │   ├── GeofenceBroadcastReceiver.kt evento ENTER/EXIT
│       │   ├── NotificationHelper.kt        canal + notificación
│       │   ├── DrivingModeService.kt        Foreground Service (modo conducción)
│       │   ├── DrivingTtsManager.kt         Controlador TTS centralizado
│       │   ├── GeomemoriasCarService.kt     Entry point Android Auto
│       │   ├── ReminderListScreen.kt        Pantalla lista Android Auto
│       │   └── ReminderDetailScreen.kt      Pantalla detalle Android Auto
│       └── res/
│           ├── layout/    (8 layouts XML)
│           ├── values/    (strings, colors, themes)
│           ├── drawable/  (iconos vectoriales)
│           ├── mipmap-anydpi-v26/  (adaptive icon)
│           ├── anim/      (FAB animations)
│           └── menu/      (menú opciones)
├── local.properties (tu SDK, no subir)
├── BRIEF.md / to_do.txt
└── README.md
```

## Funcionalidades implementadas
- ✅ Geofencing con notificaciones al entrar/salir del radio
- ✅ Lista lateral de recordatorios con distancia en tiempo real
- ✅ Borrar y editar recordatorios (radio, texto)
- ✅ Botón "Mi ubicación" con marcador azul en tiempo real
- ✅ Círculos de radio visualizados en el mapa
- ✅ Modo conducción: TTS al estar dentro del radio
- ✅ Foreground Service para segundo plano
- ✅ Comando de voz para captura (SpeechRecognizer)
- ✅ Mock de ubicación para pruebas sin moverse
- ✅ Android Auto: lista + detalle de recordatorios
- ✅ Persistencia de geocercas al reiniciar la app

## Pendiente
- Verificar geofencing en dispositivo real (Android 9+) — en VM no se pudo probar (sin GPS ni Play Services)
- Fallback WorkManager para dispositivos sin Google Play Services
- ProGuard release + firma APK
- Probar notificaciones en Android Auto
