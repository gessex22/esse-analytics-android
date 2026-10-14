# Miniaturas y cabecera de Agenda simple

Fecha: 2026-10-12 · implementación Android en la rama `redesign/home-metrica-tactil`.

## Cambios

- La cabecera muestra día y mes (`d de MMMM`), con fecha completa disponible para accesibilidad. La semana centra sus siete días cuando caben; en anchos menores conserva objetivos táctiles de 48 dp y desplazamiento horizontal guardado.
- Las miniaturas de Publicados se resuelven solo para filas del día seleccionado que intersectan el viewport. El trabajo conserva límite de dos consultas concurrentes, deduplicación por sesión y nombre exacto, cancelación por cambio de día y caché en memoria.
- La identidad de plataforma y `platformId` deben coincidir antes de aceptar cualquier fuente. La prioridad es archivo local buscado por `remoteLibraryVideoId`, URL Nube autenticada derivada de esa respuesta validada y miniatura de esa misma plataforma; Coil pasa a la siguiente fuente y termina en el placeholder.
- Cada descriptor y clave de caché incorpora una generación además de un identificador opaco del evento. El refresh avanza la generación y resetea el índice de fallback aunque las URLs no cambien. Cambiar día o sesión cancela trabajo obsoleto y evita publicar o pintar una miniatura de otra identidad.
- No se usa `linkedFileId`, título ni `FileRepository.findByName` para vincular una portada de historial. Los previews usan descriptores locales de desarrollo y no hacen peticiones de red.

## Contrato y alcance

`SyncRepository.getFileStats(fileName)` y el endpoint existente `/api/sync/file-stats` ya están soportados por Central y Lab. La implementación solo consume ese contrato y el endpoint existente de miniatura Nube. No se cambiaron API, DTO, ViewModel de red, repositorios globales, NavHost, Desktop, iOS ni backend. No se ejecutaron pruebas de Lab.

## Validación

Compilación ejecutada desde la raíz de `essenalytics-android`:

```powershell
.\gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
```

Resultado final: exit 0. Para este host Windows, Gradle necesitó forzar el fallback TCP de la tubería Java. El directorio indicado debe seguir inexistente; el bloque conserva y restaura `JAVA_TOOL_OPTIONS` en `finally`:

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

La revisión final de código de Sol aceptó los cambios de identidad, sesión, visibilidad de filas, reinicio del fallback al actualizar, fecha y centrado de semana. El emulador está disponible; la revisión visual de este binario sigue pendiente porque en este bloque no se generó ni instaló un APK. No se ejecutaron tests, `assemble`, instalación ni publicación.