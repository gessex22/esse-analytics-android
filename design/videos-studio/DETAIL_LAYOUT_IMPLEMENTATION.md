# Videos · Recuperar jerarquía del detalle Android

13 de octubre de 2026

## Resultado

El detalle vuelve a mostrar el video primero. El único `BoxWithConstraints`/host con `AndroidView` ocupa el primer hijo de la `Column` en detalle y fullscreen. El mismo player conserva URI, posición e intención de reproducción; fullscreen cambia su tamaño y ofrece «Volver al detalle» en una fila separada, fuera de los controles Media3.

En modo detalle, título y metadatos, los botones «Pantalla completa» y «Cerrar», estados por plataforma, error, acciones existentes y el cierre final viven en una `LazyColumn` con el `LazyListState` conservado fuera de la rama de fullscreen. El contenido inferior puede desplazarse completo. La lista usa 96 dp de holgura final (16+80 dp); `safeDrawingPadding` exterior cubre los insets una sola vez, sin sumar otra navegación. Se retiró el velo negro del contenido de lectura y las tarjetas usan radios de 18 dp y padding de 14 dp.

Las filas de plataforma ahora son una sola región tocable de al menos 48 dp, con badge compartido y columna de nombre/estado que puede envolver texto. El botón de enlace conserva su propio target de 48 dp y solo aparece en plataformas publicadas. El editor, acciones, callbacks y operaciones de guardado se mantienen.

## Previews

`VideoDetailStudioPreview.kt` sigue usando el contenido productivo con un placeholder y sin Hilt, ExoPlayer o red. Incluye Local y Nube en paisaje de 800×400 con el estado inicial de scroll en el último item para inspeccionar las acciones reales y Cerrar; Local a 320 dp/fontScale 1.3; fullscreen vertical, detalle inline horizontal, modo oscuro, URL ausente/error y previews separadas para guardar Idle, Running, Saved y Failed.

Las previews no demuestran un render observado. No se generó ni instaló APK; la revisión visual del nuevo binario queda pendiente. El reporte del usuario sobre Guardar en galería se abordó colocando la acción dentro del contenido desplazable con holgura inferior; no se afirma haber reproducido el problema en un dispositivo ni se atribuye una causa de superposición.

## Alcance y verificación

En esta iteración, los cambios de fuente se limitaron a `VideoDetailDialog.kt`, la presentación de `VideoDetailPlatformRow` en `VideoDetailSheet.kt` y `VideoDetailStudioPreview.kt`. Reproducción, lifecycle, URI, callbacks, estados de guardado, editor, ViewModels, contratos, lista, navegación y otras plataformas no se modificaron. No cambian endpoints ni estados compartidos; no se ejecutaron pruebas nuevas de Laboratorio.

La compilación final de `:feature:library:compileDebugKotlin` y `:app:compileDebugKotlin` terminó con exit 0. En este host se usó el workaround temporal indicado por el plan, se confirmó que el directorio de fallback no existía y se restauró el valor previo de `JAVA_TOOL_OPTIONS` en `finally`:

```powershell
$fallback = 'C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\nonexistent-socket-directory'
if (Test-Path -LiteralPath $fallback) { throw 'La ruta de fallback debe permanecer inexistente.' }
$previous = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'Process')
try {
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', "-Djdk.net.unixdomain.tmpdir=$fallback", 'Process')
    .\gradlew.bat :feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }
} finally {
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $previous, 'Process')
}
```

`git diff --check` quedó limpio; solo informa avisos CRLF ya presentes en archivos con cambios previos. Sol revisó la fuente productiva y no encontró bloqueos. No se añadieron ni ejecutaron tests, no se generó APK, instalación, push o despliegue.
