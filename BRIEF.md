# BRIEF.md — geomemorias2 (Android)

Plantilla para pedir features (igual filosofía que la v1 web).

```text
¿QUÉ:           <resultado visible final>
REGLA LADO/POS:  <dónde en la UI>
CUÁNDO OCULTAR:  <qué pasa si la app está en bg, si no hay permiso, etc.>
EXCEPCIÓN:       <qué SIEMPRE queda igual>
NO:              <qué NUNCA debe pasar>
VERIFICACIÓN:    <cómo sabes que está bien (en tu dispositivo Android 9+)>
```

## Regla de oro (heredada de v1)
El recordatorio YA EXISTE. La app valida proximidad (distancia <= radioM) y
notifica; NO crea nada al chequear. El "crear" es captura (texto+coords+radio).

## Diferencia clave vs v1 web
- v1 web: polling adaptativo + Service Worker (no despierta en bg real).
- v2 Android: Geofencing API de Play Services DESPIERTA la app al cruzar el
  radio, aunque esté cerrada. Esto es la "proactividad real" que la v1 no tenía.

## Recuerda al agente
- minSdk 28 (Android 9). No uses APIs > 28 sin guard con version.
- Pedir permisos al iniciar (ubicación + POST_NOTIFICATIONS en API 33+).
- Persistencia device-only (Room), sin nube.
- Tras entregar, retroalimenta cómo pedir lo mismo más directo.
- Mantener to_do.txt al día.
