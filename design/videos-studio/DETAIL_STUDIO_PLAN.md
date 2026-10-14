# Videos · Detalle Studio y pantalla completa

13 de octubre de 2026 · planificación de Sol para la ejecución autorizada por el usuario.

## Alcance y estado previo

- Miniatura y cuerpo de una fila abren el mismo detalle del mismo video para Local, Nube y PC. Retirar únicamente el overlay decorativo de play de la miniatura; semántica «Detalles de {nombre}, duración {valor}». Conservar la duración y su medición, logos/color/gris/tachado, Próximo, radios, espacio, filtros, menús y confirmaciones.
- El menú «Reproducir» sigue usando sus reproductores independientes actuales. «Editar plataformas» sigue abriendo el detalle. No modificar LocalPcPublishSheet ni navegación de publicación.
- El checkout contiene cambios locales previos en Agenda, NavHost, SyncDtos, LibraryScreen y previews/documentación. Comparar contra CURRENT antes de esta iteración; no restaurar ni sustituir archivos con HEAD.
- Los tres wrappers ya comparten VideoDetailDialog y los callbacks de edición. Ahora crean tres reproductores equivalentes a 280 dp sin listener de errores. VideoDetailEditor es contrato de los ViewModels y permanece intacto.

## Composición y comportamiento

1. En LibraryScreen pasar onOpenDetails a LibraryStudioThumbnail, renombrar solo su callback y etiqueta accesible, y quitar su Icon PlayArrow. Conservar el import/uso de PlayArrow en el menú. No cambiar onPlay del menú, datos ni layout de filas.
2. Convertir VideoDetailDialog en ventana única Creator: CreatorHomeTheme, CreatorStudioBackdrop, cabecera de cierre de 48 dp, título y metadatos, reproductor, tarjeta «Estado por plataforma» y tarjeta de acciones. Evitar el margen ficticio de 80 dp de la barra de la app: esta ventana tiene su propio inset inferior. Aplicar CreatorHomeTheme también dentro de LinkEditorDialog, que se compone fuera del cuerpo del detalle; conservar isSaving, acciones y cierre condicionado existentes.
3. Extraer un VideoDetailStudioContent puro que recibe estado, slots y callbacks; no crea Hilt, ExoPlayer ni consulta red. La ventana productiva aporta reproducción real; las previews aportan un placeholder sin URL/decodificador. Conservar el mismo contenido y geometría en ambos.
4. Los tres wrappers entregan URI y mensaje de error específicos al diálogo en lugar de crear PlayerView/ExoPlayer y fijar height(280.dp). Local: Uri.parse(shownFile.filePath), incluidos file/content; Nube/PC: streamUrl existente nullable. Mantener LaunchedEffect/setInitial, backfill, linkedPlatforms/links, estados de guardar y linkEditor sin cambios.
5. Conservar acciones y condiciones: Local Publicar/Galería; Nube App/Galería y Publicar únicamente si el caller entrega un callback real; PC Publicar. Para Nube cambiar onPublish a (() -> Unit)? = null y componer el botón/espacio solo cuando sea no nulo: LibraryScreen no lo pasa; RemoteLibraryScreen sí pasa un callback hacia su RemotePublishForm y debe seguir funcionando sin editar ese caller. No inventar navegación hacia Subir. Reutilizar DetailPrimaryAction también en PC. Mantener Idle/Running/Saved/Failed, exclusión de guardados simultáneos, mensajes y callbacks funcionales. No añadir opciones App/Galería a PC ni cambiar I/O.
6. Filas de plataforma con controles de al menos 48 dp, descripción de estado y enlace; en 320 dp/texto grande, distribuir nombre/editar y estado en líneas separadas cuando no quepan. Evitar que «Publicado · Sin enlace» quite todo el ancho al nombre. Conservar Platform.publishable y las transiciones actuales.

## Un reproductor durante la expansión

- Crear VideoDetailPlayback.kt con una sesión recordada por URI/identidad de reproducción dentro del propietario del detalle y fuera de cualquier rama de modo. Media3 1.5.1 y lifecycle ya están disponibles; no actualizar ni añadir dependencias.
- Un solo ExoPlayer, un solo MediaItem y un solo prepare al abrir/cambiar URI. Un solo AndroidView/PlayerView llamado en una posición estable del árbol: misma Column/cabecera/Box del player en ambos modos. Cambiar únicamente tamaño, fondo y visibilidad del contenido inferior; no poner player() en dos ramas if, dos slots, dos diálogos ni en items lazy. No recrear al editar plataformas/enlaces, guardar o expandir.
- El host del player recibe Modifier configurable. AndroidView.factory crea PlayerView, update asegura su player actual y onRelease lo desacopla; únicamente el propietario libera ExoPlayer en DisposableEffect. Listener para buffering/ready/ended/error y VideoSize, retirado en dispose. No imprimir URIs autenticadas ni mensajes técnicos que puedan contener tokens.
- Mantener controles Media3 de play/pausa/seek habilitados, resizeMode FIT y fondo negro. Estado de carga real; URI ausente y PlaybackException muestran texto humano, nunca spinner permanente. Los errores de reproducción no esconden edición/acciones ni sustituyen errores de guardado.
- Calcular aspect ratio válido desde VideoSize/pixel ratio, fallback 16:9 mientras no se conozca. En detalle usar ancho disponible y acotar altura aproximadamente al 40 % del área útil, conservando espacio para la lista; no fijar 280 dp ni recortar videos verticales. En pantalla completa el host ocupa todo el espacio útil y mantiene FIT para vertical/horizontal. No forzar orientación de Activity.
- Botón explícito «Pantalla completa» desde detalle, área mínima 48 dp; deshabilitado si no hay URI. En modo expandido se ocultan tarjetas y aparece «Volver al detalle». expand/collapse solo cambia el booleano: currentPosition y playWhenReady permanecen en el mismo player, también si estaba pausado o ended.
- Un único Dialog con usePlatformDefaultWidth=false, decorFitsSystemWindows=false y fillMaxSize. Mantener cabecera y controles dentro de safeDrawing/ventana, incluyendo navegación por gestos/tres botones y cutouts. El fondo negro puede cubrir toda la ventana; no es otro cuadro de 280 dp. No cambiar la apariencia global de barras en NavHost.
- onDismissRequest y botón de volver primero desactivan fullscreen; el siguiente Atrás/cierre en detalle llama al onDismiss original. Conservar LazyListState fuera de la rama de fullscreen para recuperar la posición de las tarjetas. Estados de editor/guardados viven en wrappers y permanecen durante expand/collapse.
- Observar ciclo de vida con APIs existentes: pausar al dejar la app y recuperar únicamente la intención de reproducción previa al volver. No reanudar un video que el usuario pausó. Quitar observer/listener y desacoplar/liberar al cerrar detalle/publicar o cambiar URI; expandir no libera ni crea otra sesión. Todo acceso al player en el hilo principal.

Documentación oficial de referencia: [ciclo crear/adjuntar/preparar/liberar y controles Media3](https://developer.android.com/media/media3/exoplayer/hello-world) y [DialogProperties/insets](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties). Usar solo APIs compatibles con las versiones del checkout.

## Allowlist

Directorio de fuentes: `feature/library/src/main/kotlin/com/esseanalytics/android/feature/library/`.

- LibraryScreen.kt: solo callback de miniatura, semántica y overlay.
- VideoDetailDialog.kt: ventana, tema, contenido puro y modo expandido.
- VideoDetailSheet.kt: adaptar player, presentación de filas y tema de LinkEditorDialog; conservar saveVideoToGallery y lógica de editor/guardado.
- RemoteVideoDetailSheet.kt y LanVideoDetailSheet.kt: adaptar player y acción PC compartida; mantener estados/callbacks.
- VideoDetailActions.kt: apariencia Creator y adaptación de texto/targets; mantener habilitación/status.
- Nuevos VideoDetailPlayback.kt y VideoDetailStudioPreview.kt.
- design/videos-studio/DETAIL_STUDIO_PLAN.md, DETAIL_STUDIO_IMPLEMENTATION.md y DETAIL_STUDIO_REVIEW.md; nota de matriz y changelog acotados en ../FEATURES.md al cerrar, preservando todo el historial.

Fuera del alcance: VideoDetailEditor, ViewModels, DTO, repositorios, builders de URLs, VideoPlayerDialog.kt y feature:remotelibrary, core/designsystem, Agenda, NavHost, Gradle/dependencias, backend, Desktop e iOS. Reportar una necesidad estricta antes de ampliarlo.

## Recuperación y verificación

- Antes de editar Kotlin: snapshot CURRENT en `C:/Users/Gessemberg/.codex/recovery/android-videos-detail-studio-20261013/before`, preservando estructura relativa de allowlist, fuentes nuevas de Videos/Agenda ya existentes, documentación previa y ../FEATURES.md. Registrar git status y diff actuales sin .env/secretos. Resultado y documentos a after al cerrar. No usar git reset/checkout para preparar esta fase.
- Previews puras: Local/Nube/PC; claro/oscuro; 320 dp y fontScale 1.3; ancho y paisaje; placeholder vertical/horizontal, pantalla completa; título largo y «Publicado · Sin enlace»; URI ausente/error de reproducción; Running/Saved/Failed. Fixtures de desarrollo, sin Hilt, ExoPlayer ni red.
- Revisión Sol: miniatura/cuerpo usan mismo detalle, menú preservado, árbol estable con un solo AndroidView/player, no prepare/reset al expandir, posición/pausa conservadas por construcción, Atrás en dos pasos, liberación única, insets, acciones/editors/errores y callbacks intactos. Revisar diff contra before y git diff --check.
- Compilar library/app, sin tests nuevos ni assemble/APK/instalación/push/despliegue. Workaround temporal conocido, comprobando que la ruta NO exista y restaurando el entorno:

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

- El protocolo ../LAB_FEATURE_PROMPT.md fue leído. Esta iteración usa contratos/estados/URLs existentes, sin llamadas nuevas: no requiere cambiar Central/Lab ni escenarios. Documentar sin afirmar pruebas Lab nuevas ni render observado. Registrar compilación real y revisión visual del nuevo binario pendiente.
- Límites previos: Publicar en Nube era un callback vacío por defecto; ocultar esa acción al faltar callback evita un control silencioso. Publicación de Nube sigue en el flujo Subir existente, sin nuevo wiring en esta ronda. Guardar galería Local usa File(filePath), por lo que un content:// puede dar el error actual; esta fase conserva esa operación, aunque reproducción sí debe usar content://. No presentar esas capacidades como reparadas. La conservación al expandir/cerrar pantalla completa no implica persistencia del player ante recreación de Activity.
