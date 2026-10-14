# Agenda · Studio

12 de octubre de 2026 · base `redesign/home-metrica-tactil` · checkout compartido.

## Encargo y límites

Sol planifica y revisa; Luna implementa este bloque de Agenda en Android. La pantalla debe continuar la identidad de Inicio y Datos y priorizar el próximo trabajo: atrasadas, pendientes de hoy, mañana y semana. No se rediseñan otras pantallas en este bloque. El plan se puede ejecutar sin decisiones adicionales del usuario.

Antes de editar, revisar `git status` y conservar cambios ajenos. La lectura inicial de este plan se hizo con el checkout limpio. No modificar backend, Laboratorio, DTO, endpoints, autenticación, fuentes, paletas globales ni cálculo de cadencia. No generar APK, instalar, desplegar, publicar ni hacer push. La revisión instalada está pendiente porque no hay dispositivo adb conectado.

Se leyeron `AGENTS.md`, `../LAB_FEATURE_PROMPT.md`, `design/datos-studio/PLAN.md`, `design/datos-studio/DESIGN_REVIEW.md`, CalendarScreen, CalendarViewModel, CreatorHomeTheme, CreatorTypography, CreatorVideoStyle, CreatorHome, CreatorStudioBackdrop, CreatorLoadingBar y la integración del NavHost.

## Problemas comprobados en la base

- CalendarScreen usa Swiss y la barra compartida mientras Inicio/Datos usan Creator Studio; las miniaturas de Agenda miden 52 × 64 y 48 × 60 dp frente a 64 × 64 dp.
- Los grupos operativos filtran solo `nextDate`: un slot sin `nextVideo` se cuenta como pendiente. Los candidatos sin fecha y los próximos posteriores a mañana solo aparecen, si coinciden, dentro del detalle de un día de la semana.
- Success con `slots.isEmpty()` oculta también el historial disponible. Hoy y mañana mezclan sus conteos con publicados y muestran estos antes de los pendientes.
- Siete celdas con `weight(1f)` y espacios de 8 dp quedan por debajo de 48 dp de ancho en 320 dp. Las iniciales estrechas de los días no distinguen bien martes/miércoles. La selección de hoy no cambia el aspecto porque el color de hoy tiene prioridad; no hay semántica de selección.
- El panel de día presenta publicados y pendientes sin etiquetas que los distingan; varios títulos están limitados a una línea. Descartar comparte una fila con miniatura y título, comprimiendo el texto en ancho reducido.
- La carga inicial usa un spinner; el refresh se muestra en AppTopBar. El error inicial no ofrece reintento dentro de la pantalla. La semana está congelada por `remember { weekDates() }`.
- Los colores de barra de estado solo contemplan Inicio/Datos. Agenda mantiene el título «Calendario» de la barra compartida pese a que la navegación Studio la denomina «Agenda».

## Contrato y significado de los datos, sin cambios

| Fuente existente | Uso conservado |
| --- | --- |
| `GET /api/sync/calendar-config` → `List<CalendarConfigDto>` mediante SyncRepository | Un slot por plataforma con último publicado, intervalo y candidato `NextVideoDto?`. Carga inicial `force=false`; actualización manual y posterior a descarte `force=true`. |
| `GET /api/sync/history?limit=60&offset=0` → UploadHistoryResponse | Historial real disponible; CalendarViewModel conserva el anterior si esta consulta falla y no bloquea la agenda. El límite de 60 no garantiza una semana completa. |
| `POST /api/sync/calendar-config/{platform}/skip-next`, cuerpo `{fileId}` | Descartar solo ese candidato para esa plataforma; confirmar, invocar `discardNext(slot)`, recargar. El fallo aparece en `refreshError`. |

Mantener la autenticación y los errores del consumidor existente. `nextDate` es `lastPublishedDate + intervalDays`; no contiene hora, zona horaria de publicación ni confirmación de una tarea automática. Mostrarla como fecha por cadencia. No añadir horas, publicación automática, reprogramación, nueva paginación, estado de procesamiento, pendientes históricos ni recurrencias futuras inventadas. Hay como máximo un próximo candidato por slot, no una cola completa.

Las fechas de historial se obtienen de `Instant.parse(publishedAt)` en la zona local del dispositivo; `lastPublishedDate` se interpreta como fecha ISO sin convertirla a instante. Preservar ambas reglas. No derivar publicados del título/fecha del slot ni deduplicar por título: los IDs del historial representan publicaciones reales por plataforma.

## Orden y agrupación decididos

Derivar toda la presentación del Success actual. Usar una sola fecha local `today` para los grupos y la semana lunes–domingo; recalcular al refrescar/recomponer y al regresar a primer plano, sin mantener una semana con `remember` sin clave de fecha. No añadir un servicio ni cambiar CalendarViewModel para esto.

Sea `candidates = slots.filter { it.nextVideo != null }`. Cada candidato aparece una sola vez en la lista operativa, con identidad estable por plataforma y candidato; puede repetirse únicamente en el panel contextual del día seleccionado.

1. Encabezado «Agenda».
2. Error de actualización/descarte, cuando exista, con texto legible y botón «Reintentar» que llama a `refresh()`.
3. **Atrasadas**: candidatos con fecha anterior a hoy; orden por fecha y plataforma, fecha completa en cada fila. Conteo solo de candidatos. Etiqueta «Atrasada» además de color; el rojo de error se reserva para este estado y Descartar.
4. **Pendientes hoy**: candidatos con fecha igual a hoy, sin publicaciones en el conteo. Estado textual «Hoy» y fecha legible. Mostrar «No hay próximos videos pendientes para hoy» cuando esté vacío, en un mensaje compacto.
5. **Mañana**: candidatos con fecha igual a hoy + 1. Mostrar sección solo si hay candidatos.
6. **Esta semana**: rango legible de lunes a domingo, selector de siete días y candidatos desde hoy + 2 hasta el domingo inclusive. Son listas exclusivas; no repetir atrasadas, hoy o mañana aquí. Si hoy es domingo, mañana sigue en su sección aunque pertenezca a otra semana. El selector representa toda la semana actual, incluidos los publicados y días anteriores.
7. **Próximas fechas**: candidatos con fecha posterior al domingo que no hayan entrado en mañana, ordenados por fecha. Mostrar solo cuando haya candidatos. Evita que una cadencia larga desaparezca.
8. **Sin fecha calculada**: candidatos con `nextDate == null`; explicar «No hay una fecha base disponible para calcular la cadencia». No prometer cuándo se publicarán ni trasladarlos a hoy. Conservan candidato, plataforma, intervalo y descarte confirmado.
9. **Publicados hoy**: bloque secundario basado exclusivamente en el historial del día, orden descendente por `publishedAt`; conteo independiente y etiqueta «Publicado». Si no hay filas, omitirlo. Los publicados de otros días siguen accesibles en el selector semanal, incluidos registros futuros que devuelva la fuente, claramente etiquetados como historial. No crear un historial global nuevo.
10. **Cadencias sin próximo video**: bloque secundario para `nextVideo == null`, sin contarlos como pendientes ni dibujar puntos de pendientes. Conserva plataforma, intervalo, estado «Sin próximo video disponible» y último publicado expandible. Mostrar solo si hay slots de este tipo.

El panel del día seleccionado muestra primero «Pendientes» y después «Publicados», con conteos separados, títulos completos accesibles, plataforma explícita y duración cuando exista. Un panel vacío dice «No hay próximos videos ni publicaciones disponibles para este día»; esta frase expresa el alcance de los datos cargados. No mostrar «No hubo publicaciones» ni otras conclusiones de historial completo.

Success siempre conserva encabezado y selector semanal, incluso con slots vacíos y/o historial vacío. Con ambas listas vacías, añadir un mensaje contextual sin esconder el contenedor. No afirmar que es obligatorio publicar en todas las plataformas para iniciar la cadencia.

## Identidad, retícula y composición

- Envolver Agenda en `CreatorHomeTheme` y `CreatorStudioBackdrop`; Fraunces para el título y encabezados destacados, DM Sans para contenido/controles. Reutilizar la paleta existente en claro y oscuro. Sin colores verdes/naranjas hardcodeados ni fondos Swiss nuevos.
- Márgenes laterales `CreatorVideoStyle.screenHorizontalPadding` (8 dp). Tarjetas Studio con superficie, borde fino `outlineVariant` y radios de la familia de Inicio/Datos. Padding interior horizontal 12 dp de inicio y 8 dp de final. Las filas de video usan los 4 dp verticales del estilo compartido; encabezados y controles pueden reservar altura adicional real para legibilidad y tacto.
- Todas las miniaturas de candidatos y último publicado son cuadradas de 64 dp y usan `listThumbnailShape`. Mantener local primero, remoto después y placeholder existente. El historial no tiene URL de miniatura en su DTO: usar un placeholder neutro de la misma geometría si la fila necesita portada; no resolver URLs nuevas ni reutilizar una portada de otro título.
- Candidato: plataforma identificada con nombre y logo; cadencia secundaria; miniatura y título de hasta dos líneas con ellipsis; fecha/estado y duración legibles. Evitar logos como única identificación y conservar respaldo textual para plataformas desconocidas.
- Colocar Descartar en la zona inferior de acciones, con altura mínima 48 dp, fuera de la fila de título/miniatura. Conservar la confirmación con nombre del video y plataforma y las acciones Descartar/Cancelar. El diálogo permanece bajo CreatorHomeTheme. No convertir el cuerpo completo de tarjeta en una acción destructiva.
- Conservar «Ver último publicado» / «Ocultar último publicado», expansión breve y detalle con título, fecha y miniatura disponible. En slots sin información de último publicado, indicar el dato no disponible sin fingir un título/fecha ni añadir navegación.
- Las tarjetas crecen para acomodar fecha, estado y título. En 320 dp o `fontScale > 1.2` los metadatos se disponen debajo del título, sin columnas estrechas ni reducción del tamaño de letra. Las fechas largas pueden envolver; no truncar la fecha/estado principal.
- Separación regular de 8 dp entre elementos relacionados y mayor espacio antes de las secciones. Padding inferior de la lista equivalente a Datos (124 dp) para la navegación flotante. Mantener keys para evitar recrear/animar toda la lista durante refresh.

## Encabezado único y carga única

En EsseAnalyticsNavHost, excluir únicamente la ruta principal CALENDAR de AppTopBar y del título compartido; añadirla al tratamiento de iconos del sistema de Inicio/Datos. Pasar `username` y `onProfileClick` a CalendarScreen. El adaptador de CalendarScreen conecta Actualizar a su `viewModel::refresh`; no obtener otro CalendarViewModel en el NavHost. No cambiar destinos, selección de pestaña, navegación inferior, DetailScaffold ni rutas de otras features.

Agenda dibuja un único título desplazable de 30 sp con controles flotantes arriba a la derecha. Decisión explícita: **Actualizar y Perfil**, en dos superficies locales de 48 dp con el lenguaje de CreatorFloatingHomeActions. Actualizar llama a `refresh()`, Perfil abre Ajustes. No mostrar campana en Agenda: la acción existente de notificaciones de Inicio/Datos abre esta misma pantalla. No cambiar el componente global ni introducir un botón que navegue a sí mismo. El avatar compacto omite el nombre visible y conserva «Abrir perfil de {nombre}» como etiqueta accesible.

Reservar el espacio horizontal/vertical de los controles para que el título y primera sección no queden tapados. En texto extremo, permitir que el título ocupe una segunda línea o quede debajo de controles. Una sola aplicación de `statusBarsPadding()` al contenido/controles según el patrón de Datos; Scaffold conserva sus insets existentes. Los controles flotantes mantienen respuesta de presionado, contraste y etiquetas.

`CreatorLoadingBar` es el único indicador animado: `Loading || Success.isRefreshing`, situado bajo el área de estado. Usar PullToRefreshBox con indicador vacío y pasar `distanceFraction` a CreatorLoadingBar como en Datos. No añadir spinner inicial ni spinner de pull/refresh. En carga inicial, mantener título y estructura con placeholders estáticos; durante refresh mantener los datos anteriores y la selección visibles. El flujo de descarte conserva la retroalimentación actual de recarga/error; no crear progreso ficticio durante el POST ni cambiar el ViewModel.

Error inicial: mensaje y «Reintentar». Error con datos anteriores: mensaje no bloqueante y reintento. Los mensajes locales se presentan bajo el tema Studio; no editar RefreshErrorBanner global para este bloque.

## Semana y accesibilidad

Selector horizontal desplazable de siete días; no comprimir siete columnas al ancho disponible. Celdas de al menos 52 dp de ancho y 64 dp de alto; aumentar ancho/alto si escala de fuente lo requiere. Cada zona de interacción es al menos 48 × 48 dp y no depende de expansión táctil sobre controles vecinos. Inicialmente dejar hoy visible; el refresh no vuelve a desplazar el selector ni cambia la elección del usuario.

Mostrar abreviaciones inequívocas «lun», «mar», «mié», «jue», «vie», «sáb», «dom», número del día y marca de actividad. Hoy conserva una marca textual «Hoy»; seleccionado usa borde, marca visible y superficie tonal. Ambas marcas deben convivir cuando se selecciona hoy. Los puntos por plataforma son auxiliares y no transmiten por sí solos estado/cantidad.

Cada día expone fecha completa, número de próximos candidatos y de publicados disponibles, rol de selección y `selected`; texto de estado para hoy. La fila expone desplazamiento horizontal. Selección y expansión se guardan como estado local (por fecha/plataforma, con rememberSaveable cuando sea práctico), sin afectar el estado de datos. Mantener toggle actual: tocar el día seleccionado oculta el panel.

Controles de expansión con estado expandido/contraído; Descartar con etiqueta que identifica video/plataforma; mensaje de error anunciable sin exponer secretos. Decoraciones y miniaturas tienen descripción nula cuando la fila ya anuncia título y estado. El orden de lectura sigue la jerarquía de la pantalla. No usar solo color, iconos sin nombre o títulos invisibles para distinguir pendiente/publicado.

## Archivos exactos permitidos para Luna

1. `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarScreen.kt`: composición, grupos locales, estado UI, header local, pull-to-refresh, tarjetas, fecha/semana, diálogo, semántica y previews.
2. `app/src/main/kotlin/com/esseanalytics/android/app/EsseAnalyticsNavHost.kt`: solamente integración CALENDAR descrita (ocultar topbar, callbacks e iconos del sistema). Comentarios cercanos pueden ajustarse para reflejar Agenda.
3. `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarStudioPreview.kt`: archivo opcional de previews si evita crecer CalendarScreen. Usar funciones internas de presentación; datos exclusivamente de desarrollo.
4. `design/agenda-studio/IMPLEMENTATION.md`: decisiones aplicadas, archivos cambiados, validación y limitaciones. La revisión de Sol se guardará después en `design/agenda-studio/DESIGN_REVIEW.md`.

CalendarViewModel queda intacto. Tampoco editar componentes globales Creator, Home/Dashboard, Stats, build scripts, dependencias, recursos de fuentes, core/network, repos iOS/Web ni FEATURES desde la ejecución de Luna. Si la implementación descubre una necesidad fuera de esta lista, documentarla y comunicarla a Sol antes de ampliar alcance; no reinterpretar el plan como autorización para corregir otras features.

El coordinador registra al cierre en `../FEATURES.md` la actualización visual Android de la fila Calendario y un changelog breve, indicando contrato conservado y revisión instalada pendiente. Este plan no requiere crear escenarios ni modificar los consumidores Desktop/iOS: se mantienen los flujos y contratos existentes. No certificar pruebas Lab nuevas que no se ejecutaron.

## Criterios verificables y validación

| Caso | Resultado esperado |
| --- | --- |
| Slot con fecha pasada y sin nextVideo | No aparece ni suma en Atrasadas; sí conserva cadencia y último publicado. |
| Candidato atrasado/hoy/mañana/resto de semana/fuera de semana/sin fecha | Una sola fila operativa en el grupo correspondiente; ningún candidato desaparece. Fecha real por cadencia, sin hora inventada. |
| Hoy domingo y próximo mañana | Mañana sigue visible; no se duplica en Próximas fechas. |
| Historial hoy + pendiente hoy | Pendiente precede al historial; conteos independientes y etiquetas explícitas. |
| Slots vacíos con historial de esta semana | Semana y panel permiten leer publicados. |
| Hoy seleccionado | Marca Hoy y selección visual/semántica conviven; segundo toque cierra panel. |
| 320 dp y texto 1.3+ | Días con ≥48 dp, scroll horizontal, título/header sin solapamiento, fechas legibles, Descartar no estrangula el título. |
| Claro/oscuro y plataformas desconocidas | Colores Studio legibles y plataforma textual; sin verde fijo ni texto heredado de tema externo. |
| Carga, refresh, error y vacío | Header estable, una sola barra; refresh conserva filas/selección; errores con reintento; vacío honesto. |
| Descartar/Cancelar/error | Confirmación por video/plataforma, cancelar no llama al ViewModel, confirmar usa fileId existente; fallo visible y sin desaparición optimista. |
| Último publicado | Expansión mantiene título, fecha y miniatura/fallback real. |
| Navegación | Perfil abre Ajustes, Actualizar consulta realmente, Inicio/Datos siguen entrando a Agenda, pestaña Agenda seleccionada y cápsula sin cambio. |

Separar contenido visual del adaptador Hilt/ViewModel para previews. Añadir previews de claro, oscuro, 320 dp, 320 dp con `fontScale=1.3`, día seleccionado con pendientes/publicados, título/fecha largos, sin candidatos, solo historial, carga y error con datos previos. Usar una fecha fija de preview para escenarios deterministas. No incorporar fixtures al flujo de producción.

Al terminar Luna: revisar `git diff --check`, `git diff --stat` y el diff completo, confirmar la allowlist y compilar desde la raíz Android:

```powershell
./gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --console=plain --quiet
```

No ejecutar assemble, generar APK, instalar ni añadir tests para este cambio visual. Sol revisará por lectura de código las once reglas ya usadas en Inicio/Datos: jerarquía, tarea, consistencia, retícula, proximidad, espacio, acento, respuesta, navegación, prevención de errores y legibilidad. Previews declaradas y compilación no acreditan apariencia instalada: registrar por separado revisión de código, render si estuviera disponible y revisión en dispositivo pendiente. Una falla de compilación preexistente o del entorno se documenta con su error preciso; no corregir otra feature para ocultarla.
