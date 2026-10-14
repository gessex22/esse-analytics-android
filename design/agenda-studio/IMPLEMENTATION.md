# Agenda Studio · Implementación Android

Fecha: 12 de octubre de 2026

## Cambios

- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarScreen.kt`: composición Studio, grupos exclusivos de candidatos con `nextVideo`, selector semanal desplazable y accesible, historial por día, último publicado expandible, descarte con confirmación e identidad validada, carga/error/vacío con reintento y estado de refresco. El título se desplaza con el contenido; Actualizar y Perfil quedan en una capa superior. La fecha semanal se renueva al refrescar y volver a primer plano. Las miniaturas usan la geometría común de 64 dp.
- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarStudioPreview.kt`: previews sintéticas deterministas de claro/oscuro, 320 dp, texto 1.3, día seleccionado, candidatos vacíos, solo historial, carga y error con datos previos. Los datos de ejemplo solo se usan en previews.
- `app/src/main/kotlin/com/esseanalytics/android/app/EsseAnalyticsNavHost.kt`: Agenda oculta el AppTopBar/título compartido, recibe nombre y callback de perfil y se suma al tratamiento Studio de iconos del sistema.

El ViewModel, DTO, autenticación y solicitudes existentes no cambiaron. Actualizar llama a `refresh()`. El descarte confirma el título/plataforma y conserva `discardNext(slot)`; un `fileId` vacío inhabilita la acción y no se envía.

## Validación y límites

- `git diff --check`: correcto en archivos rastreados. Los archivos nuevos también se revisaron con búsqueda de espacios finales; no se encontraron.
- Compilación solicitada (`:feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet`): completada con exit 0 en Temurin OpenJDK `21.0.12+8`. El primer inicio falló en la conexión loopback de Gradle; para compilar se usó, solo en el proceso, una ruta inexistente para activar el fallback TCP de JDK 21. El valor previo de entorno se restauró al terminar y la ruta no se creó.

Comando reproducible en PowerShell (comprueba que la ruta del socket no exista, restaura `JAVA_TOOL_OPTIONS` incluso si falla Gradle y sale con el código de Gradle):

```powershell
$socketFallbackPath = 'C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\nonexistent-socket-directory'
if (Test-Path -LiteralPath $socketFallbackPath) { throw 'La ruta ya existe; elige una ruta inexistente antes de compilar.' }
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$socketFallbackPath"
try {
    .\gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
    $compileExit = $LASTEXITCODE
} finally {
    if ($null -eq $previousJavaToolOptions) {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    }
}
exit $compileExit
```

- La revisión de código de Sol está documentada por separado en `DESIGN_REVIEW.md` y no encontró bloqueantes tras los seis ajustes finales.
- Revisión instalada: pendiente; el entorno reportado no tiene dispositivo adb conectado.
- Las previews son una validación de composición declarada, no certifican el render instalado.
- Sin cambios de contrato ni nuevas pruebas de Laboratorio.

## Copias de recuperación

- `C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\before\FEATURES.md`: copia previa exacta de `C:\Users\Gessemberg\Documents\Docu\proyectos\UIEssePanel\FEATURES.md`. Las copias previas Kotlin están en el mismo directorio `before/`, con rutas relativas al repo Android.
- `C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\after\FEATURES.md`: copia final de `UIEssePanel/FEATURES.md`. En `after/` también están las copias finales Kotlin y `design/agenda-studio/{PLAN.md,DESIGN_REVIEW.md,IMPLEMENTATION.md}`, con rutas relativas al repo Android.