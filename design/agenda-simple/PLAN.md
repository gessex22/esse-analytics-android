# Agenda simple · Plan de Sol para Luna

12 de octubre de 2026 · checkout compartido · rama `redesign/home-metrica-tactil`.

## Objetivo y referencias

Aplicar el nuevo pedido del usuario: mostrar principalmente el video que necesita publicarse y los que ya se publicaron, con mucha menos información. Este plan sustituye la jerarquía anterior de Agenda Studio para esta iteración. Sol planifica/revisa; Luna implementa.

Se inspeccionaron las dos imágenes proporcionadas: la primera usa fecha destacada, semana compacta, selección circular y marcadores; la segunda agrupa filas compactas en una superficie clara con divisorias finas. Adoptar esa estructura con la identidad Creator Studio existente: paleta, Fraunces/DM Sans, fondo, geometría de miniaturas y controles. No copiar el rosa de las referencias ni introducir otro tema.

Antes de editar, revisar git status y preservar los cambios locales de Agenda Studio, NavHost, previews y documentos. No resetear, restaurar ni sustituir la base por HEAD. No editar Kotlin desde Sol ni delegar esta planificación.

## Diseño decidido: cabecera y dos secciones

1. **Cabecera de fecha y semana.** Eliminar el título grande «Agenda». La fecha seleccionada es el encabezado visual principal, por ejemplo «12 de octubre de 2026», con Fraunces de 24–28 sp. Debajo, una semana lunes–domingo con abreviación y número; selección circular con marca visible, hoy distinguible y actividad mediante puntos pequeños. Sin tarjeta enmarcando toda la semana, rango repetido, contadores o texto explicativo permanente.
2. **Por publicar.** Una sola superficie Studio con filas compactas y divisorias finas entre ellas, sin tarjeta individual por plataforma ni subgrupos Atrasadas/Hoy/Mañana/Semana/Futuro/Sin fecha. Mostrar todos los candidatos reales que entrega el estado actual; ordenar por fecha más antigua primero y dejar sin fecha al final. Se mantienen visibles cuando se toca cualquier día.
3. **Publicados · {fecha seleccionada}.** Segunda superficie de filas compactas de historial real correspondiente a ese día, del más reciente al más antiguo. Rotular la fecha de forma breve junto al encabezado, permitiendo envolver el texto. Sin conteo numérico. No mostrar otro panel semanal que repita estas filas.

La pantalla mantiene únicamente esas dos secciones de contenido. Eliminar bloques de cadencia, slots sin candidato, duración permanente, explicaciones operativas, último publicado expandible por plataforma y resúmenes numéricos. Es una reducción de información solicitada, no una eliminación del descarte.

## Semántica de selección y datos

Al entrar, seleccionar hoy. Tocar un día cambia la fecha de cabecera y **solo el historial de Publicados**; Por publicar no se filtra ni se duplica en un panel del día. No usar toggle a selección nula. El rótulo fechado de Publicados y la etiqueta accesible del día («Ver publicaciones del {fecha}») comunican este alcance sin un párrafo permanente.

Usar la semana actual y los datos ya cargados. Conservar actualización de today al refrescar y al volver a primer plano. Si cambia la semana y la selección anterior queda fuera de ella, seleccionar hoy; dentro de la misma semana, refresh conserva la selección y posición del usuario. Mantener el mecanismo de primera visibilidad de hoy sin reset de scroll en cada recomposición.

- Candidato real: exclusivamente `slot.nextVideo != null`. Hay como máximo un próximo por plataforma; no una cola completa. Fechas continúan siendo `lastPublishedDate + intervalDays`, sin horas ni promesas de ejecución automática.
- Agrupar pendientes únicamente por `nextVideo.fileId` no vacío. Un fileId vacío conserva una fila independiente con identidad de plataforma; nunca unir por título. Orden estable por menor nextDate conocido y plataforma/fileId como desempate.
- Una fila agrupada representa el mismo video y conserva todos sus slots/plataformas reales. Título y portada proceden de uno de esos slots en orden estable; elegir una miniatura disponible del mismo grupo, manteniendo local antes de remoto. No resolver nuevas URLs ni datos.
- Metadato breve por plataforma y fecha: «YouTube · Hoy», «Instagram · Mañana», «TikTok · Atrasado, 10 oct» o «Facebook · Sin fecha». Si varias plataformas tienen fechas distintas, mostrarlas en metadatos que puedan envolver; no asignar una fecha común falsa ni ocultar una plataforma. Añadir año cuando sea necesario para evitar ambigüedad. Sin intervalo/duración ni un bloque separado de urgencia.
- Historial: mantener **una fila por evento `id`**. No fusionar por título, linkedFileId o proximidad temporal; no es necesario implementar agrupación por lote. Preservar plataforma y fecha local de `Instant.parse(publishedAt)`. No deducir publicados de la configuración del slot.
- Slots vacíos no ocultan history. Con candidatos vacíos, un mensaje breve en Por publicar; con historial del día vacío, «No hay publicados disponibles para este día». Expresar datos disponibles, sin afirmar historial completo: la consulta existente está limitada a 60 eventos y conserva su fallback de error.

## Filas, controles y descarte

Reutilizar márgenes laterales de 8 dp y padding interior de 12 dp al inicio/8 dp al final. Cada sección tiene una superficie suave, borde Studio fino y esquinas de la familia existente. Filas con miniatura real/fallback de 64 dp, metadato secundario breve arriba, título hasta dos líneas y acciones al final. Divisorias solo entre filas, alineadas con el inicio del texto; sin sombras/tarjetas individuales. Para historial sin portada, mantener placeholder de la geometría común, sin asociar una miniatura por título.

Mostrar logos pequeños junto a los nombres de plataformas si aportan reconocimiento. Títulos y metadatos permiten adaptación a 320 dp y texto 1.3+; no reducir letra para comprimir información. Con varias plataformas, los metadatos envuelven y la fila crece. Evitar cifras, etiquetas diminutas y decoración que compita con el título.

Pendientes tienen un único menú «Más opciones de {título}», con área propia mínima de 48 × 48 dp. Dentro aparece «Descartar próximo en {plataforma}» para cada slot del grupo; fileId vacío no permite enviar. La selección de menú abre la confirmación existente para ese slot exacto, con título y plataforma. Confirmar invoca `discardNext(slot)`; cancelar no envía. El rojo se reserva para la opción destructiva/confirmación y el estado de atraso si procede, sin botón rojo permanente en cada fila. No abrir otro detalle inexistente al tocar una fila informativa.

Conservar Actualizar y Perfil como controles flotantes de 48 dp y sus callbacks reales; solo estos flotan. Fecha/semana se desplazan con el contenido. Reservar espacio al encabezado para las acciones, permitiendo envolver la fecha en ancho estrecho. Conservar una CreatorLoadingBar, PullToRefreshBox sin indicador propio y padding inferior de 124 dp. Mantener carga inicial estática, errores con reintento y refresh con datos visibles; diálogo y menú dentro de CreatorHomeTheme.

Días con área mínima de 48 × 48 dp; si siete no caben, desplazar horizontalmente. La selección debe verse sin depender solo del color y exponer selected, fecha completa y acción de mostrar publicados; puntos decorativos no sustituyen los nombres accesibles. Miniaturas no repiten el título hablado; el menú identifica el video.

## Archivos permitidos y límites

- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarScreen.kt`: simplificar composición productiva, grupos UI, selector, superficies/filas y menú confirmado. CalendarViewModel permanece intacto.
- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarStudioPreview.kt`: adaptar previews al contenido productivo, con datos exclusivamente de desarrollo.
- `design/agenda-simple/IMPLEMENTATION.md` y `DESIGN_REVIEW.md`: ejecución de Luna y revisión de Sol. Conservar la documentación anterior como registro de esa iteración.
- `../FEATURES.md`: únicamente actualizar la nota Android de Calendario y añadir un changelog de simplificación/validaciones, conservando el resto del archivo.

NavHost ya aporta perfil y ocultación de barra/tema; **no requiere cambios** para este diseño. No editar backend, Laboratorio, DTO, endpoints, VM, repositorio, componentes globales, paleta, fuentes, otras pantallas ni dependencias. Conservar los contratos existentes documentados en agenda-studio. No generar APK, instalar, desplegar, publicar ni hacer push.

## Comprobación y entrega

Luna revisa diff contra el snapshot local previo, la allowlist y `git diff --check`. Compilar `:feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet`; si es necesario, reutilizar el ajuste temporal de JAVA_TOOL_OPTIONS documentado en `design/agenda-studio/IMPLEMENTATION.md`, verificando ruta inexistente y restaurando el entorno. No ejecutar assemble ni añadir tests.

Previews del mismo contenido productivo: claro/oscuro, 320 dp, texto 1.3, candidato compartido por plataformas con fechas distintas, candidato sin fecha, fileId vacío, día distinto de hoy, solo historial, vacíos y error con datos. Comprobar por lectura que seleccionar un día no oculta candidatos, compartir fileId no altera el slot descartado, history-only funciona y la pantalla muestra únicamente dos secciones sin cadencias/último publicado/contadores.

Sol revisa la implementación y registra compilación real y alcance del diff. Previews declaradas no acreditan render: revisión visual instalada sigue pendiente si no hay dispositivo. Entregar el resultado concreto sin abrir otra ronda de decisiones opcionales.
