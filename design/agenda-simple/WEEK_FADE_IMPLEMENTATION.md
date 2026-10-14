# Agenda · continuidad visual de la semana

Fecha: 2026-10-13 · Android, rama `redesign/home-metrica-tactil`.

## Comportamiento

`CalendarWeek` conserva siete controles centrales de 48 dp, de lunes a domingo, con selección, Hoy y marcadores de datos existentes. Añade a sus extremos el domingo real anterior y el lunes real siguiente, calculados desde la semana activa. Esos dos días son decorativos: no reciben clics, selección, semántica propia, marca Hoy, puntos ni consultas de historial/miniaturas.

La tira mide 24 + 7 × 48 + 24 = 384 dp. Cuando el viewport tiene al menos 336 dp, centra los siete controles principales y deja que el recorte del viewport corte únicamente el contexto exterior. En anchos menores conserva los tamaños de 48/24 dp, habilita desplazamiento horizontal, trae Hoy a la vista inicialmente y guarda la posición por semana.

Cada vecino se dibuja en su propia capa offscreen con una máscara horizontal `DstIn`: el extremo exterior tiene alpha 0 y el borde junto a la semana central alpha 0.65. No hay fondo superpuesto ni blur, y la semana principal nunca pasa por esa máscara.

## Alcance preservado

Solo se cambiaron `CalendarScreen.kt`, `CalendarStudioPreview.kt`, este documento y la fila/changelog de Calendario en `../FEATURES.md`. Se conservaron los filtros de fechas, selección e historial, la agrupación Meta, las portadas y sus callbacks, ViewModel, DTO, resolver, NavHost, contratos y otras plataformas. No se añadieron dependencias ni tests; no se ejecutaron pruebas Lab.

Las previews cubren claro/oscuro a 393 dp, ancho amplio, 320 dp con texto 1.3, lunes y domingo seleccionados y la semana que cruza del 31 de diciembre de 2026 al 1 de enero de 2027. Son composiciones declaradas, no evidencia de render instalado.

## Validación

Desde la raíz de `essenalytics-android`:

```powershell
.\gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
```

La compilación final, posterior a los ajustes de ancho requerido y opt-in de Foundation, terminó con exit 0. En Windows se usó temporalmente el workaround de Java con el directorio inexistente ya documentado; el valor de `JAVA_TOOL_OPTIONS` se restauró en `finally`:

```powershell
$socketFallback = 'C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\nonexistent-socket-directory'
if (Test-Path -LiteralPath $socketFallback) { throw 'La ruta debe permanecer inexistente.' }
$hadJavaToolOptions = Test-Path Env:JAVA_TOOL_OPTIONS
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$socketFallback"
    .\gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }
} finally {
    if ($hadJavaToolOptions) { $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions }
    else { Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
}
```

No se generó ni instaló APK. Hay emulador disponible, pero el render visual de este nuevo binario queda pendiente.

La revisión de código final de Sol está aprobada y se registra en `WEEK_FADE_REVIEW.md`.

El snapshot previo a esta ronda está en `C:\Users\Gessemberg\.codex\recovery\android-agenda-week-fade-20261013\before`. El snapshot final se guardará en `after` tras la revisión de Sol.
