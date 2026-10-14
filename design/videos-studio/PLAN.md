# Videos · Studio

13 de octubre de 2026 · plan de Sol para ejecución y revisión de Luna.

## Objetivo y alcance

Convertir la pestaña principal Videos (`Routes.LIBRARY`) en continuación simple de Inicio, Datos y Agenda: portada, título y estado necesario, listado continuo y acciones táctiles claras. Primera ronda exclusivamente de la pantalla/listados principales y confirmación de eliminación. Mantener el detalle, reproductores y publicación existentes.

Se leyeron AGENTS.md, ../LAB_FEATURE_PROMPT.md, LibraryScreen, LibraryViewModel, LibraryListItem, contratos de Nube/PC, acciones de detalle y geometría Creator. Git status tiene cambios locales de Agenda y documentación: preservarlos sin reset ni restauración a HEAD. Tomar snapshot de los archivos del alcance en `C:/Users/Gessemberg/.codex/recovery/android-videos-studio-20261013/before` antes de editar; copiar resultado/documentos a after al cerrar revisión.

No generar APK/instaladores, instalar, desplegar, publicar, hacer push ni añadir tests. No rediseñar otras pantallas ni introducir métricas/dashboard, búsqueda, multiselección o transferencias nuevas. Esas funciones no existen en el listado actual.

## Inventario real que se conserva

| Área | Función actual y preservación |
| --- | --- |
| Importar | onImportClick abre la ruta de ingesta; conservar callback y funcionamiento externo. |
| Fuentes | ALL/LOCAL/REMOTE/LAN. UI llama a setFilter; Nube condicionada por canUseCloudStorage, PC por canSeeLanLibrary. Mantener aparición/desaparición y retorno a ALL cuando el VM pierde PC. |
| Datos de Todos | VM prioriza Local sobre Nube y Nube sobre PC con su reconciliación actual; fuentes individuales completas y orden descendente de sortInstant. No rehacer deduplicación en UI. |
| PC | startLanDiscovery/stopLanDiscovery ref-counted al entrar/salir. Los ítems conservan su baseUrl y streams/portadas correspondientes, sin mostrar direcciones o credenciales. |
| Tap de fila | Local → VideoDetailSheet; Nube → RemoteVideoDetailSheet; PC → LanVideoDetailSheet. |
| Reproducir | Thumbnail/acción play → reproductor local o RemoteVideoPlayerDialog con URL de esa fuente. No llevar el tap de play al editor. |
| Editar | Acción actual «Editar plataformas» abre el mismo detalle por origen. Sus editores, enlaces y estados siguen intactos. |
| Eliminar | Solo Local/Nube; confirmación por origen. Local content:// quita referencia sin borrar original; copia propia elimina archivo; Nube elimina video remoto. PC no tiene eliminación. |
| Publicar y guardar | Local publica por onOpenUpload; Nube conserva publicación y Guardar en app/Galería del sheet; PC conserva LocalPcPublishSheet y su estado/reset. No mover estas funciones al listado ni reescribirlas. |
| Estado por plataforma | Publicado/descartado/pendiente de las listas reales del ítem. Próximo usa el map nextUploads actual, por nombre; no presentarlo como nueva identidad canónica ni crear una cola nueva. |

La pantalla no expone búsqueda ni selección múltiple. No crear affordances que parezcan tenerlas. Los detalles/reproductores no forman parte de esta entrega aunque se abran desde las filas nuevas.

## Composición decidida

1. **Título «Videos» desplazable**, Fraunces de 30 sp como Datos. Sin el contador redundante de biblioteca ni subtítulo permanente. Reservar ancho/altura para controles flotantes, permitiendo envolver a escala grande.
2. **Actualizar y Perfil flotantes**, dos superficies de 48 dp como Agenda. Perfil abre Ajustes. Actualizar llama a refreshRemote solo con entitlement y siempre refreshNextUploads. PC continúa con su watcher real; no fingir un refresh LAN ni incrementar ref-count por cada pulsación.
3. **Toolbar compacta**: selector horizontal Todos / Local / Nube / PC con controles de al menos 48 dp; acción Importar fija al extremo derecho, icono Add de 48 dp con etiqueta accesible «Importar video». El selector ocupa el ancho restante y desplaza sus etiquetas si no caben. Mantener la selección explícita mediante superficie/borde y semántica, sin controles inaccesibles por texto truncado. Si solo está disponible Local, se puede omitir el selector como actualmente, manteniendo Importar.
4. **Listado continuo lazy**: filas contiguas con superficie papel/bosque Studio y divisorias finas, sin tarjeta independiente separada para cada video ni panel de información sobre cada fuente. Cada item permanece virtualizado con su key estable local_/remote_/lan_. No colocar cientos/miles de filas en una sola Column dentro de un item LazyColumn.
5. **Vacío contextual** por filtro: «No hay videos disponibles» con referencia breve al origen. En Local/Todos permitir Importar existente; en PC no afirmar biblioteca vacía si hay lanLoadError. No concluir éxito de carga Nube por falta de elementos.

## Filas y acciones

- Margen lateral `CreatorVideoStyle.screenHorizontalPadding` (8 dp). Interior 12 dp al inicio/8 dp al final, forma/superficie de la familia Studio; divisoria alineada al inicio del texto, solo entre filas. Continuidad puede construirse con superficies/bordes de filas lazy y radios solo en primer/último elemento: conservar virtualización.
- Miniatura cuadrada de 64 dp y forma `listThumbnailShape` para las tres fuentes. Local usa thumbnailPath; Nube/LAN usan las URL proporcionadas por el VM, con fallback por origen. No resolver nuevos endpoints ni adivinar por título. AsyncImage/placeholder no anuncian el título dos veces.
- **Toda la miniatura es target de reproducción de 64 dp**, con etiqueta «Reproducir {nombre}». Un icono play pequeño puede ser decorativo dentro, sin su propio target de 24 dp. Cuerpo de fila abre detalles; botón de menú funciona independientemente, sin propagar tap al cuerpo.
- Nombre real del archivo hasta dos líneas, texto semibold de 14–15 sp. Una línea secundaria de duración si existe; origen Local/Nube/PC solo donde ayuda, especialmente Todos. Resolución y metadatos extensos quedan en el detalle existente. No imprimir guiones de datos ausentes ni añadir fechas/contadores/estadísticas de rendimiento.
- Estados de plataforma compactos con los logos existentes, marca visible y nombre/estado completo en accesibilidad. Publicado usa identidad de marca; pendiente/descartado no se distinguen solo por color. Mantener el dato Próximo cuando el mapa actual lo indica, en un texto breve que puede envolver. En 320 dp/fontScale > 1.2, metadatos y badges usan FlowRow/bloques completos o disposición vertical con ancho suficiente, sin achicar letra.
- Facebook se muestra únicamente si el ítem lo incluye realmente en platforms; no inventar FB pendiente porque exista Instagram. Logo/color Facebook de lista pueden tener helper privado Studio. No cambiar helpers internal compartidos con detalle.
- **Overflow 48 dp** «Más opciones de {nombre}»: Reproducir, Editar plataformas y Eliminar solo si Local/Nube. PC conserva edición/play, sin acción de borrado. Eliminar vive en el menú, no como botón rojo permanente. Confirmación actual mantiene texto específico de referencia/local/Nube y Cancelar; diálogo bajo CreatorHomeTheme.

## Tema y estados sin ampliar ViewModel

LibraryViewModel no expone loading/error de Nube; refreshRemote es fire-and-forget y conserva lista anterior al fallar. No deducir carga/error de items vacíos, no usar temporizadores, no crear porcentajes ni otra infraestructura en esta ronda.

El indicador anterior de la barra compartida usa isAnyRefreshing real. NavHost puede pasar ese mismo estado a LibraryScreen; reemplazarlo por **una CreatorLoadingBar** sin añadir spinner de lista ni afirmar que representa un progreso remoto propio. Respuesta al toque/presionado y actualizaciones reales del flujo son la retroalimentación del botón Actualizar. No introducir pull-to-refresh con estado inventado.

Conservar LaunchedEffect de carga remota al habilitar entitlement, refreshNextUploads inicial, Room reactivo y ciclo de discovery. Mostrar lanLoadError existente como mensaje no bloqueante en Todos/PC cuando sea relevante, sin fabricar un callback de reintento LAN. Otros errores y sus flujos existentes se mantienen y sus limitaciones se documentan.

Aplicar CreatorHomeTheme/CreatorStudioBackdrop a la composición principal y al diálogo de eliminar. Scaffold local conserva SnackbarHost y contentWindowInsets=0; su contenedor es transparente para que se vea el fondo. Aplicar statusBarsPadding una sola vez en contenido/controles como Datos/Agenda, y clearance inferior para cápsula/nav del sistema (mínimo equivalente a 124 dp sin doble inset).

**Sheets de detalle, reproductores y LocalPcPublishSheet se componen fuera del nuevo ámbito Creator de la lista**, manteniendo su tema original y funciones. No retocar sus archivos para uniformar esta ronda. Preservar exactamente firmas y comportamiento de internal PlatformBadgeState, platformColor, platformShortLabel y platformIcon de LibraryScreen: VideoDetailSheet/LocalPcPublishSheet los reutilizan. Solo componentes privados nuevos de listado pueden variar el estilo.

## Integración y allowlist

1. `feature/library/src/main/kotlin/com/esseanalytics/android/feature/library/LibraryScreen.kt`: adaptador actual, contenido Studio separado, filas/toolbar/menús/confirmación y presentación del error LAN ya disponible.
2. `feature/library/src/main/kotlin/com/esseanalytics/android/feature/library/LibraryStudioPreview.kt`: archivo nuevo opcional de previews del mismo contenido productivo, sin Hilt/red.
3. `app/src/main/kotlin/com/esseanalytics/android/app/EsseAnalyticsNavHost.kt`: únicamente añadir LIBRARY a ocultación del AppTopBar/título y apariencia Studio de iconos del sistema; pasar username, onProfileClick e isAnyRefreshing reales. Conservar callbacks de importación/publicación y todos los cambios de Agenda/Datos existentes. No cambiar rutas, bottom nav ni contenedores de detalle.
4. `design/videos-studio/IMPLEMENTATION.md`, `DESIGN_REVIEW.md` y fila/notas/changelog acotados en `../FEATURES.md`.

No modificar LibraryViewModel, LibraryListItem, sheets, reproductores, DeleteVideoUseCase, repositorios, DTO, backend, Lab, fuentes/paletas/componentes globales, dependencias ni otras features. Si la implementación descubre una necesidad estricta fuera de esta lista, comunicarla antes de ampliar alcance.

Contratos intactos: Local Room/archivos; GET/DELETE `api/remote-library/videos` y URLs de miniatura/stream Nube existentes; PC autorizada con GET `api/videos` paginado y utilidades LAN existentes; GET `api/sync/calendar-config` para Próximo. Las operaciones de detalle/publicación conservan su red actual. No ampliar paginación ni cambiar autenticación/entitlements. FEATURES debe distinguir UI Android revisada/compilada de paridad contractual preservada y render pendiente, sin afirmar pruebas Lab nuevas.

## Previews y validación

Previews del contenido productivo: claro/oscuro, 320 dp, 320 dp/fontScale 1.3, tres orígenes en Todos, títulos largos, estados publicado/descartado/pendiente/Próximo, Facebook realmente publicado, Local-only, Nube/PC vacíos, error LAN con datos, miniatura/fallback y confirmación de referencia/local/Nube. Fixtures exclusivamente de desarrollo; no instanciar ViewModel/Hilt ni pedir URLs reales en preview.

Luna revisa diff contra before, allowlist y `git diff --check`. Compilar `:feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet`, usando el workaround Java temporal documentado si el host lo necesita, restaurando entorno. No tests/assemble/APK/install/push.

Sol verifica conservación de cada callback/origen/gate, ayudas internal compartidas, virtualización, play64/menu48, no borrado PC, diálogo por fuente, header único/insets/carga real, legibilidad y límites de estado remoto. Registrar compilación real y revisión visual del binario nuevo pendiente aunque el emulador esté conectado. Respaldar after tras revisión y entregar sin ampliar rediseño a detalle/Subir u otras vistas.
