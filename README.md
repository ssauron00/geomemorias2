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
- OSMDroid (mapa open-source, sin API key)
- Play Services Location (Geofencing)
- WorkManager (fallback de proximidad para dispositivos sin GMS)

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
│       │   ├── MainActivity.kt          UI: mapa OSMDroid + captura
│       │   ├── Reminder.kt              modelo (Room @Entity)
│       │   ├── ReminderDao.kt           acceso a datos
│       │   ├── AppDatabase.kt           Room DB
│       │   ├── AppDatabaseProvider.kt   singleton
│       │   ├── GeoUtils.kt              haversine + histeresis + polling
│       │   ├── GeofenceHelper.kt        registra geocercas (Play Services)
│       │   ├── GeofenceBroadcastReceiver.kt  evento ENTER/EXIT
│       │   └── NotificationHelper.kt    canal + notificación
│       └── res/  (layout, values, mipmap, drawable)
├── local.properties (tu SDK, no subir)
├── BRIEF.md / to_do.txt
└── README.md
```

## Pendiente / siguiente paso sugerido
- Verificar en tu dispositivo Android 9+: al guardar y acercarte al punto, llega
  la notificación (geofence ENTER). Al alejarte > radio, el flag se resetea.
- Ajustar UI (lista lateral, borrar, editar radio después de guardar).
- Modo conducción (TTS) — era pendiente de v1.
- Fallback WorkManager para dispositivos sin Google Play Services.
