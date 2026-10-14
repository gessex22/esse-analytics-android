# Videos · Detalle Studio Android

13 de octubre de 2026

## Resultado

Las miniaturas y el cuerpo de fila de Local, Nube y PC abren el detalle del video correspondiente. La miniatura ya no muestra ni activa el overlay decorativo de play; el elemento completo expone «Detalles de {nombre}, duración {valor}» cuando hay duración. El menú de fila conserva «Reproducir» con sus reproductores independientes y «Editar plataformas» sigue abriendo el detalle.

`VideoDetailDialog` es una sola ventana Studio con cierre, título/metadatos, estado por plataforma y acciones. La ventana conserva un `LazyListState` y un único host de reproducción en el mismo punto del árbol al cambiar entre detalle y pantalla completa. El mismo ExoPlayer y MediaItem se mantienen al expandir y volver; no hay un segundo reproductor ni otro `prepare`. Back vuelve primero al detalle y después cierra. El cierre o cambio de URI retira observer/listener, desacopla el `PlayerView` y libera el player una vez. La intención de reproducción solo se recupera tras ciclo de vida si estaba activa antes de pausar.

Los tres adaptadores conservan sus ViewModels, estados, callbacks y operaciones. Local reproduce la URI de `filePath`, incluidas rutas `file://` y `content://`; Nube/PC usan su `streamUrl` nullable. `PlayerView` mantiene controles, fondo negro y ajuste FIT; errores y URI ausente muestran copy humano en el host y dejan visibles edición y acciones. La altura inline usa ancho disponible y ratio válido, acotada al 40 % del área útil y 320 dp; en fullscreen el host llena el espacio útil sin forzar orientación.

Las filas de estado colocan nombre/edición y estado en líneas separadas, con targets de al menos 48 dp. Reutilizan el badge de plataforma de la lista: color de marca para publicado, gris para pendiente y gris tachado para descartado. `LinkEditorDialog` usa el tema Creator y conserva guardado, indicador y cierre condicionado. Nube muestra «Publicar» solo al recibir callback: `LibraryScreen` no pasa uno y `RemoteLibraryScreen` conserva el suyo. La acción de PC usa `DetailPrimaryAction`. Se mantienen estados y bloqueos de guardado existentes.

`VideoDetailStudioPreview.kt` compone `VideoDetailStudioContent`, un layout puro que recibe player-placeholder, estado, slots y callbacks. No crea Hilt, ExoPlayer ni acceso a red. Sus fixtures muestran Local/Nube/PC, tema claro/oscuro, 320 dp y fontScale 1.3, paisaje inline/fullscreen, ratio vertical/horizontal, título largo, «Publicado · Sin enlace», URI ausente/error de reproducción, y acciones Running/Saved/Failed.

## Alcance y límites

No cambian ViewModels, contratos, DTO, repositorios, builders de URL, navegación, `feature:remotelibrary`, Agenda, dependencias, backend, Desktop ni iOS. No hay llamadas nuevas al Lab ni se afirma una prueba Lab.

La operación existente de guardar un archivo Local todavía usa `File(filePath)`, por lo que `content://` puede fallar al guardar en galería. La reproducción de esa URI sí se admite. La sesión del player no persiste ante recreación de Activity. El render del binario no se observó: no se generó ni instaló APK.

## Verificación

La compilación final de `:feature:library:compileDebugKotlin` y `:app:compileDebugKotlin` terminó con exit 0. En este host se usó el workaround de sockets indicado por el plan; el path alternativo se comprobó inexistente, `JAVA_TOOL_OPTIONS` se restauró en `finally` y el directorio no se creó. La instrucción ejecutada fue:

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

`git diff --check` limpio. No se añadieron ni ejecutaron pruebas nuevas; no se generó APK, instalación, push o despliegue. La revisión final de código de Sol no encontró bloqueos.
