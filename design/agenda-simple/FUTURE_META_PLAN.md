# Agenda · Días futuros y fila Meta única

13 de octubre de 2026 · plan acotado de Sol para Luna.

## Evidencia y causa

Se revisaron git status, AGENTS.md, LAB_FEATURE_PROMPT, helper Android, historial central/Lab y contraparte iOS. Conservar todos los cambios locales anteriores y tomar snapshot recuperable antes de editar; sin reset/restauración a HEAD.

- Android solo admite atrasados/hoy y pasa overdueSlots a la semana: por eso una fecha futura seleccionada no muestra sus candidatos ni marcador.
- PublishedHistoryGroups exige operationId, fileName, deviceId y source no vacíos: historial antiguo sin esos campos queda separado aun si representa el mismo contenido.
- **Central ya expone contentId** en GET `/api/sync/history`: `backend/src/controllers/backup.controller.ts`, getUploadHistory, campo tomado de UploadHistoryModel. Es la identidad canónica del contenido, independiente de títulos/nombres/lotes/dispositivos. El DTO Android no la declara y la ignora. Añadirla como nullable no requiere endpoint ni cambio de backend.
- Eso no garantiza que esté poblado: recordUploadEvent conserva el contentId del payload y Android UploadWorker actualmente envía nombre/ID remoto/lote, no contentId. Aunque la central resuelva el archivo al aplicar la publicación, el evento puede seguir con contentId nulo. Por eso el fallback del mismo lote es parte necesaria de esta implementación, y un histórico sin identidad canónica ni lote no puede unirse con seguridad usando el contrato disponible.
- Central devuelve linkedFileId explícitamente null porque es concepto SQLite local. iOS tampoco declara contentId y su agrupación actual cae a linkedFileId/fileName; no copiar esa heurística ni rediseñar iOS aquí.
- Lab history devuelve spread de sus eventos y linkedFileId null; su modelo/siembra actual no proporcionan contentId. El nuevo campo Android con default null es compatible y Lab puede seguir por fallback de lote, pero **no acredita la rama canónica**. No afirmar una prueba Lab canónica nueva.
- File-stats central excluye Facebook. Una pareja acreditada puede compartir la portada ya validada de su miembro Instagram; no inventar evento/portada Facebook ni relajar el matching del resolver.

## Prioridad 1 · Semántica por día seleccionado

Filtrar slots antes de pendingGroups, siempre con nextVideo y nextDate no nulos:

| Fecha seleccionada | Slots elegibles |
| --- | --- |
| Pasado: selectedDate < today | Ninguno; solo Publicados. |
| Hoy: selectedDate == today | nextDate < today; únicamente atrasados reales. |
| Futuro: selectedDate > today | nextDate == selectedDate; candidatos de esa fecha exacta. |

Sección «Por publicar» solo en Success con elegibles no vacíos; no fila/encabezado vacío. Conservar Publicados del día y el título de fecha breve actual. Varios candidatos reales se conservan y se agrupan únicamente por fileId conforme al mecanismo existente. Un slot mañana no se arrastra a un grupo atrasado por compartir fileId.

CalendarWeek recibe todos los candidatos con nextVideo/nextDate, no solamente overdueSlots. Sus marcadores representan nextDate real exacta. No generar recurrencias, desplazar fechas, inventar horarios ni mover un TikTok atrasado al miércoles para llenar la selección.

Conservar followingToday/rollover/restauración, recarga de la central y descarte confirmado por slot/plataforma. No estado local «publicado» ni eliminación optimista: el candidato persiste hasta que la fuente real cambia. El descarte de un futuro conserva la misma confirmación y contrato existentes.

## Prioridad 2 · Agrupación Meta por identidad existente

Agregar `val contentId: String? = null` al final de UploadHistoryItemDto. Mantener todos los campos/tipos/defaults anteriores, especialmente linkedFileId; no reutilizarlo como ID canónico. Tratar contentId no vacío como identificador opaco y comparar exactamente, sin derivarlo del nombre.

PublishedHistoryGroups aplica dos niveles, consumiendo cada evento como máximo una vez:

1. **Identidad canónica preferida:** candidatos IG y FB con contentId no vacío idéntico, día local seleccionado igual y exactamente un evento válido IG/un evento válido FB para ese contentId/día. id y platformId no vacíos, IDs de evento distintos y fechas parseables. No exigir operationId, deviceId, source, fileName o títulos coincidentes cuando la central acredita el mismo contenido. Nombres distintos no invalidan un renombre del contenido canónico.
2. **Fallback de lote para eventos no consumidos:** operationId y fileName no vacíos idénticos; una pareja única IG/FB acreditada por ese lote+archivo entre los eventos cargados. Fechas parseables, mismo día seleccionado e IDs válidos/distintos. Si ambos declaran deviceId y son diferentes, rechazar; lo mismo para source. Campos de origen ausentes no son por sí solos una contradicción de un mismo lote. **Si ambos contentId son no vacíos y diferentes, jamás unir por fallback**, aunque lote/nombre coincidan.

La unicidad canónica se comprueba antes de consumir en la pareja, sobre todo el historial del día/contentId. Si hay varias publicaciones IG/FB de ese contenido en el mismo día, dejar eventos independientes en esta iteración para evitar mezclar republicaciones. La unicidad de fallback debe considerar el historial cargado global del lote/nombre, no reducirlo primero a una pareja conveniente de otro día. Eventos de otras plataformas siguen independientes.

Sin identidad canónica ni lote probado, conservar filas individuales. No unir por fileName solo, title, similitud de tiempos, linkedFileId o contenido «parecido». Una sola plataforma no se completa artificialmente. Claves estables con identidades de miembros ordenadas; singles y filas ambiguas no pierden eventos. Orden descendente por el instante más reciente de la pareja.

Mantener thumbnailEvent = Instagram para pareja Meta. La fila usa sus PublishedThumbnailSources ya validadas y la cadena local/Nube/plataforma/fallback existentes; no copiar su descriptor al mapa Facebook ni cambiar el resolver global. Si no se puede validar/obtener imagen IG, mantener fallback honesto. Callbacks de visibilidad/disposición incluyen todos los miembros y sus claves originales, preservando deduplicación, caché, sesión y retry de Coil.

## Prioridad 3 · Logos y nombres adyacentes

Una fila de pareja tiene **una miniatura, un título y un FlowRow de identificaciones**: logo de 14–16 dp + «Instagram», luego logo + «Facebook». Cada identificación es un bloque indivisible; ambos quedan al lado cuando hay ancho. FlowRow envuelve bloques completos a 320 dp/fontScale 1.3 sin reducir letra ni comprimir nombres. No construir dos Columns completas de plataforma/hora apiladas, ni duplicar título/portada/divisoria.

Mostrar hora común una sola vez si coinciden las horas locales HH:mm. Si difieren, añadir una única línea secundaria con atribución explícita («Instagram 10:02 · Facebook 10:03»), permitiendo envolver texto; no atribuir la hora IG a Facebook. La fila sigue siendo una unidad visual. Singles conservan su hora real; pendientes usan el mismo patrón FlowRow para logo/nombre y metadato de fecha como bloques completos adaptables.

Nombres visibles y semántica completa; logos decorativos sin anuncio duplicado. Conservar superficie continua, padding, miniaturas de 64 dp, controles táctiles de 48 dp, fecha/semana centradas, perfil/refresh flotantes y una sola barra de carga.

## Allowlist y contrato

Android:

- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarScreen.kt`: elegibles por día, marcadores, FlowRow y horas.
- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/PublishedHistoryGroups.kt`: agrupación canónica/fallback segura.
- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarStudioPreview.kt`: fixtures del contenido productivo.
- `core/network/src/main/kotlin/com/esseanalytics/android/core/network/dto/SyncDtos.kt`: únicamente añadir contentId nullable a UploadHistoryItemDto; es modelado de campo ya servido por Central, compatible con payload anterior.
- `design/agenda-simple/FUTURE_META_IMPLEMENTATION.md`, `FUTURE_META_REVIEW.md`, nota/changelog acotados en `../FEATURES.md`.

No requieren cambios CalendarViewModel, resolver global, NavHost, otras features, backend, Lab ni iOS. No alterar endpoints/auth/cálculo de cadencia. Documentar en FEATURES el consumo Android del campo existente y que Lab solo cubre compatibilidad/fallback; no afirmar cobertura canónica instalada o Lab que no se ejecutó. Si una relación histórica no tiene contentId/lote disponible, esa carencia de datos no autoriza inferirla.

## Previews, verificación y entrega

Fixtures mínimos: hoy con atraso y mañana del mismo fileId; pasado con publicado y sin Por publicar; futuro con candidato exacto y marcador; candidato atrasado que no se mueve al futuro; pareja IG/FB con mismo contentId y operationId/deviceId/source ausentes; mismo contentId con fileNames diferentes; nombres iguales/contentIds diferentes sin merge; varias republicaciones del mismo contenido/día; pareja por fallback de lote; miembro suelto; 320 dp/texto 1.3 y horas diferentes. La portada IG sintética acredita ruta/fallback, no una imagen real instalada.

Sol revisa esos criterios, conservación de todos los eventos y estado previo. Luna revisa diff contra snapshot, allowlist y `git diff --check`, y compila `:feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet` con el workaround temporal documentado si hace falta, restaurando entorno. Como se modela un campo HTTP, comprobar además por lectura el default null y mantenimiento de los consumidores existentes; no cambiar los DTO ajenos para silenciar fallos.

No añadir una fase de investigación ni expandir rediseño. No generar APK, instalar, desplegar, publicar o hacer push. Registrar compilación real y pendiente de render del nuevo binario, aunque el emulador esté disponible. Entregar para revisión Sol.
