# Videos · Revisión final de jerarquía del detalle

13 de octubre de 2026 · Sol · baseline CURRENT `C:/Users/Gessemberg/.codex/recovery/android-videos-detail-layout-20261013/before`.

## Resultado

**Aprobado por revisión de código.** Sin bloqueos materiales pendientes. Luna confirmó compilación final library/app exit 0 después de añadir las dos fixtures de Local/Nube en paisaje al final del detalle. Sol releyó esas fixtures y las fuentes productivas, comprobó el alcance por hashes contra before y ejecutó git diff --check sin errores; solo avisos de CRLF. No se observó render ni ejecución en dispositivo.

## Jerarquía, scroll y espacio inferior

- El único BoxWithConstraints/host de reproducción es el primer hijo de la Column en ambos modos y se invoca desde un único callsite fuera del if de fullscreen. Mantiene FIT, ratio y altura embebida limitada al 40 % del espacio disponible/máximo 320 dp; fullscreen ocupa el resto mediante weight. No se monta player en items lazy ni otro Dialog/AndroidView.
- Título, metadatos, Pantalla completa/Cerrar, plataformas, errores, acciones y cierre final se componen dentro de la LazyColumn inferior. Ya no hay cabecera/título fijos encima del video. La FlowRow de controles puede envolver en 320 dp y texto grande; las tarjetas tienen radios de 18 dp y padding de 14 dp, sin velo negro en el contenido de lectura.
- La LazyColumn mantiene weight(1f) y LazyListState recordado fuera de la rama. No hay altura fija ni scroll interno en el item de acciones: Galería, App, Publicar y Cerrar forman parte de la extensión desplazable completa. Una acción fuera del viewport puede alcanzarse por scroll; no se oculta ni se mueve a una superposición.
- Holgura final de 96 dp (16+80), con safeDrawingPadding exterior consumido una sola vez. No se suma navigationBars otra vez dentro de la lista. Esto deja espacio desplazable después de la última acción/cierre; no se atribuye a una barra concreta la cobertura reportada sin reproducirla.
- Fullscreen oculta solo el detalle y presenta retorno de 48 dp fuera del video, negro/blanco y siempre habilitado incluso sin URI. Atrás conserva la secuencia fullscreen→detalle→cerrar. El mismo estado de scroll se recupera al volver; no hay botones encima de los controles Media3.

## Plataformas y operaciones preservadas

- Cada fila tiene una región de estado de mínimo 48 dp con badge y columna nombre/estado. Los Text pueden envolver sin maxLines/ellipsis/altura fija; la fila crece cuando lo necesita. El IconButton de enlace es un hermano independiente de 48 dp y aparece solo en publicado, sin anidar su click dentro del target de estado.
- Badge compartido conserva color publicado, gris pendiente y gris con diagonal descartado. Labels, toggle, edición de enlace y semántica de estado siguen usando callbacks reales. LinkEditorDialog, isSaving y guardados no cambian.
- La comparación de hashes de las fuentes guardadas en before confirmó cambios exclusivamente en VideoDetailDialog, presentación de VideoDetailPlatformRow en VideoDetailSheet y VideoDetailStudioPreview. El diff de Sheet no cambia inicialización, operaciones de guardar, mensajes, URIs ni callbacks.
- VideoDetailPlayback y VideoDetailActions coinciden exactamente con before; también wrappers Nube/PC, LibraryScreen, previews de listado y cambios anteriores de Agenda/NavHost/SyncDtos. Se conservan un solo ExoPlayer, prepare por URI, lifecycle/errores/liberación, nullable onPublish Nube, publicación PC habilitada, estados de guardar y miniatura/fila→detalle sin play overlay.

## Previews y límites

Las doce previews son puras, sin Hilt/ExoPlayer/red. Usan acciones productivas de Local/Nube/PC y estados Idle/Running/Saved/Failed por escenario, con una sola acción de Galería por fuente. Cubren 320 dp/fontScale1.3, claro/oscuro, ratio vertical/horizontal, fullscreen, reproducción ausente/error y posición inicial del último item.

Local y Nube tienen además fixtures de 800×400 dp en paisaje con bottomItem=true: el estado inicial apunta al item real `detail_actions_and_close`, que contiene Galería y Cerrar, seguido de la holgura final. Estas declaraciones permiten inspeccionar los controles inferiores y no acreditan un render observado. Las fixtures de error usan hasUri ligado al mensaje; esto es una simplificación de preview y no altera la lógica productiva de URI/retorno.

La compilación correcta y la lectura de composición no prueban la causa original ni la ausencia efectiva de cobertura en dispositivo. Render, scroll/insets/touch y reproducción real siguen pendientes de revisión visual/runtime. Sin tests nuevos, APK, instalación, despliegue ni push en esta revisión.
