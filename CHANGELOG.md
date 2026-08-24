# CHANGELOG — geomemorias2

Todos los cambios notables en este proyecto. Formato basado en [Keep a Changelog](https://keepachangelog.com/).

---

## [1.2.0] — 2026-08-24

### Added — Documentación
- **BRIEF.md**: Sección de permisos requeridos con tablas detalladas y código de verificación
- **BRIEF.md**: Diagrama de flujo de permisos en Mermaid (diagrama detallado + simplificado)
- **BRIEF.md**: Diagrama de arquitectura del sistema (componentes, secuencia, ubicación, persistencia)
- **BRIEF.md**: Diagrama de estado del TTS en modo conducción (state machine con 5 estados)
- **BRIEF.md**: Tabla de transiciones del TTS con variables de estado y constantes
- **BRIEF.md**: Sección completa de Android Auto (componentes, funcionalidades, verificación)
- **BRIEF.md**: Sección completa de Modo Conducción (Driving Mode)
- **README.md**: Estructura actualizada del proyecto (todos los archivos nuevos)
- **README.md**: Stack actualizado (Car App Library, Foreground Service, SpeechRecognizer)
- **README.md**: Lista de funcionalidades implementadas con checkmarks
- **CHANGELOG.md**: Este archivo

### Fixed
- **Lint**: POST_NOTIFICATIONS guard solo en API 33+ (verificación de versión)
- **Lint**: UUID substring fix para evitar crashes
- **OSMDroid**: Eliminado `setTileCacheEnabled` (no existe en osmdroid 6.1.18)
- **OSMDroid**: Eliminado `setTileRequestTimeout` (no existe en osmdroid 6.1.18)
- **OSMDroid**: `setTileSource` ahora se ejecuta después de `load()` + config de red/cache

---

## [1.1.0] — 2026-08-20

### Added — Android Auto (Car App Library)
- **GeomemoriasCarService.kt**: Entry point para Android Auto (`CarAppService`)
- **ReminderListScreen.kt**: Lista de recordatorios con distancia en tiempo real
  - Auto-refresh cada 10s (lifecycleScope + repeatOnLifecycle)
  - Botón 🔄 para refresh manual
  - Navegación a detalle al seleccionar item
- **ReminderDetailScreen.kt**: Detalle del recordatorio (texto, coordenadas, radio, estado)
- **Dependencia**: `androidx.car.app:app:1.4.0`
- **Strings**: `car_list_title`, `car_empty_list`, `car_no_distance`, etc.

### Added — Android Auto (Notificaciones)
- **Meta-data**: `com.google.android.gms.car.notification = true` en AndroidManifest
- **Meta-data**: `com.google.android.gms.car.APPLICATION_ID` en AndroidManifest
- **NotificationHelper**: `CATEGORY_NAVIGATION` + `BigTextStyle` para notificaciones en Auto

### Added — Driving Mode (Modo Conducción)
- **DrivingModeService.kt**: Foreground Service para ubicación + TTS en background
  - Acciones: `ACTION_START` / `ACTION_STOP`
  - Notificación persistente con acción "Detener"
  - SharedFlow para comunicación servicio→activity
  - Lifecycle: `START_STICKY`
- **DrivingTtsManager.kt**: Controlador TTS centralizado con state machine
  - Estados: OUTSIDE → ENTERED → INSIDE → EXITED → OUTSIDE
  - Supresión de TTS para recordatorios recién creados
  - Constantes: `MAX_INSIDE_SPOKEN=2`, `EXIT_DISTANCE_MULTIPLIER=3.0`
- **Overlay de conducción**: Fondo transparente, barra superior + inferior
- **Botones grandes**: 🎤 Hablar + 📌 Fijar punto (64dp, fácil de tocar conduciendo)
- **Verificación de batería**: Diálogo de whitelist al activar modo conducción

### Added — Comandos de Voz
- **SpeechRecognizer**: Botón 🎤 junto al campo de texto
- **Modo conducción**: State machine conversacional (IDLE → ASK_TEXT → ASK_RADIUS)
- **Permisos**: `RECORD_AUDIO` bajo demanda (no al inicio)

### Added — Mock de Ubicación
- **Botón 📍 Mock**: Fija ubicación tocando el mapa
- **GPS desactivado**: Mientras el modo mock está activo

### Added — Persistencia de Geocercas
- **reregisterGeofences()**: Re-registra todas las geocercas desde Room al iniciar
- **Llamado desde**: `onCreate()` + `permLauncher` al conceder permiso

### Added — UI Mejorada
- **Lista lateral**: DrawerLayout + RecyclerView con distancia en tiempo real
- **Borrar recordatorio**: Long-press en marker → diálogo de confirmación
- **Editar radio**: Diálogo para cambiar radio después de guardar
- **Botón "Mi ubicación"**: Centra el mapa en la posición actual
- **Marcador azul**: Ubicación actual que se actualiza en tiempo real
- **Círculos de radio**: Visualización del radio en el mapa
- **String resources**: Todos los strings movidos a `strings.xml`

### Fixed
- **Modo conducción**: Solo habla cuando estás DENTRO del radio
- **ViewBinding**: Referencias correctas con `binding.drivingModeOverlay.viewId`
- **Proximidad**: Eliminado `checkProximityAlerts` (alertas antes de llegar al radio)
- **TTS repeticiones**: Max 2 veces dentro del radio, 1 vez al salir
- **TTS múltiples**: Procesa TODOS los recordatorios dentro del radio (QUEUE_ADD)
- **Lint**: Safe calls innecesarios eliminados
- **Lint**: Parámetro no usado `mapView` renombrado a `_` en `addMarker`
- **Notificación**: Consolidación de canales (`DRIVING_CHANNEL_ID` como única fuente)

### Changed
- **GeofenceBroadcastReceiver**: Actualiza `notified` flag en Room (insert con copy)

---

## [1.0.0] — 2026-08-15

### Added — Core
- **Estructura Gradle**: Root + app para Android Studio
- **Kotlin 1.9**, minSdk 28 (Android 9), targetSdk 34
- **Room**: Persistencia device-only (sin nube)
  - `Reminder.kt`: Modelo @Entity (id, text, lat, lng, radiusM, notified)
  - `ReminderDao.kt`: CRUD completo (getAll, insert, delete, deleteById)
  - `AppDatabase.kt` + `AppDatabaseProvider.kt`: Singleton Room
- **GeoUtils.kt**: Haversine + histeresis + polling adaptativo (portado de v1)
- **GeofenceHelper.kt**: Registra/quita geocercas en Play Services
- **GeofenceBroadcastReceiver.kt**: ENTER → notifica; EXIT → resetea flag
- **NotificationHelper.kt**: Canal (Android 8+) + notificación HIGH
- **MainActivity.kt**: Mapa OSMDroid + formulario + guardar + add geofence

### Added — UI
- **Mapa OSMDroid**: Open-source, sin API key
- **Touch para ubicar**: Toca el mapa → fija punto → formulario
- **Formulario**: Texto + radio + guardar
- **Icono vectorial**: `ic_launcher_foreground.xml`

### Added — Permisos
- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
- `POST_NOTIFICATIONS` (API 33+)
- `INTERNET` + `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`

### Added — Configuración
- `proguard-rules.pro`: Reglas de ofuscación
- `gradle.properties`: Configuración de Gradle
- `settings.gradle`: Configuración del proyecto

### Fixed — OSMDroid (Múltiples iteraciones)
- **Iteración 1**: `Configuration.setTileSource()` no existe → usar `MapView.setTileSource()`
- **Iteración 2**: `Configuration.load()` debe ir ANTES de `setContentView()`
- **Iteración 3**: `Configuration.defaultTileSource` es propiedad, no método
- **Iteración 4**: `setDefaultTileSource()` ANTES de `setContentView`; `load()` DESPUÉS
- **Iteración 5**: Simplificar — set tile source solo en MapView
- **Iteración 6**: `setTileSource` después de `load()` + config de red/cache
- **Iteración 7**: Eliminar `setTileCacheEnabled` y `setTileRequestTimeout` (no existen)

### Fixed — Kotlin
- `triggeringGeofences` nullable
- `fromPixels(Int)` vs `fromPixels(Float)`
- `return@setOnClickListener`
- Smart-cast nullable de `pendingPoint`
- Import duplicado de `ContextCompat`

---

## [0.1.0] — 2026-08-10

### Added
- Commit inicial desde v1 web (geomemorias)
- Estructura básica del proyecto
- Documentación inicial (BRIEF.md, README.md, to_do.txt)

---

## Legenda

- **Added**: Nuevas funcionalidades
- **Changed**: Cambios en funcionalidades existentes
- **Deprecated**: Funcionalidades que serán removidas
- **Removed**: Funcionalidades removidas
- **Fixed**: Corrección de bugs
- **Security**: Vulnerabilidades de seguridad
