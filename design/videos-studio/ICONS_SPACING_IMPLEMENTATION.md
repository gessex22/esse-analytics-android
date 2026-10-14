# Videos Studio: iconos y contorno

13 de octubre de 2026

## Cambios

La lista mantiene 16 dp entre el último bloque de cabecera/error y el primer video. Las filas siguen siendo items lazy independientes con sus claves y callbacks originales. Cada item dibuja la misma superficie de papel; solo el primero y el último reciben las esquinas externas de 18 dp y la fila única combina ambas. El clip antecede al fondo y cada divisoria se dibuja dentro de esa superficie, alineada con el texto. La lista vacía conserva su presentación sin contorno.

Los estados de plataforma se representan únicamente con logos de 20 dp en cajas de 24 dp, agrupados en un Row indivisible dentro de FlowRow. El color de marca identifica publicaciones confirmadas; pendientes y no publicados usan el gris neutral del tema. Descartados conservan el logo gris y llevan una diagonal gris de 2 dp con una base de 4 dp del color real de superficie. Los iconos no muestran texto, puntos ni siglas; cada caja conserva semántica accesible con nombre completo de plataforma y estado.

Facebook solo aparece cuando el elemento trae Facebook en `platforms`. Los helpers internos `PlatformBadgeState`, `platformColor`, `platformShortLabel` y `platformIcon` permanecen sin cambios. Próximo mantiene su cálculo, texto «Próximo · {sigla}», tamaño y color previos. También siguen intactos los targets de miniatura de 64 dp y menú de 48 dp, el título, duración, origen, filtros, vacíos/error, callbacks y scopes de tema.

Las previews cubren una fila con Facebook publicado y los tres estados, varias filas, tema claro/oscuro, Nube/PC vacías, error LAN, 320 dp y escala de texto 1.3. Son fixtures de desarrollo sin ViewModel ni red; el descriptor sintético inexistente acredita solo el fallback, no el render de una portada real.

## Revisión y validación

Sol revisó los criterios del plan y aprobó el código sin bloqueos materiales. Los contratos, ViewModel, NavHost, Agenda, Desktop, iOS y backend no cambiaron; no se añadieron ni ejecutaron pruebas de Laboratorio.

Compilación final correcta, exit 0:

```powershell
.\gradlew.bat :feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
```

El host necesitó el fallback TCP de sockets del JDK. Se usó solo en el proceso Gradle, verificando primero que la carpeta no existiera y restaurando en `finally` el valor previo de `JAVA_TOOL_OPTIONS`:

```powershell
$socketFallback = 'C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\nonexistent-socket-directory'
if (Test-Path -LiteralPath $socketFallback) { throw 'La ruta de fallback debe permanecer inexistente.' }
$priorJavaToolOptions = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'Process')
try {
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', "-Djdk.net.unixdomain.tmpdir=$socketFallback", 'Process')
    .\gradlew.bat :feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }
} finally {
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $priorJavaToolOptions, 'Process')
}
```

`git diff --check` quedó limpio y los archivos nuevos se revisaron por espacios finales. El emulador está disponible, pero no se generó ni instaló APK; el render de esta versión sigue pendiente.
