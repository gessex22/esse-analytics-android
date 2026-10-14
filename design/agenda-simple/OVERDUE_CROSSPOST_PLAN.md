# Agenda · Solo atrasados y cruce Instagram/Facebook

13 de octubre de 2026 · plan acotado de Sol para ejecución de Luna.

## Objetivo y límites

Por publicar debe mostrar exclusivamente candidatos atrasados al consultar hoy. Otros días muestran únicamente Publicados. Un cruce real Instagram/Facebook se presenta en una misma fila y puede reutilizar la portada validada de su evento Instagram. Logos pequeños acompañados de nombres breves ayudan al reconocimiento sin depender solo del símbolo.

Git status tiene las modificaciones locales acumuladas de Agenda y NavHost, más previews/documentos sin seguimiento. Conservarlas: trabajar contra un snapshot previo recuperable, sin reset ni restauración a HEAD. No rediseñar otras pantallas, cambiar red/backend/DTO/endpoints/repositorios globales, generar APK, instalar, desplegar, publicar ni hacer push. Emulador disponible; render del nuevo binario sigue pendiente.

## 1. Por publicar: filtro previo a la agrupación

Elegibles exactamente:

```text
nextVideo != null && nextDate != null && nextDate < today
```

Filtrar los **slots antes de pendingGroups**. Un fileId compartido por Instagram atrasado y YouTube mañana solo incluye el slot atrasado en título/metadatos/menú; no arrastrar la plataforma futura desde el grupo original. Fechas iguales a hoy, mañana, posteriores y nulas quedan fuera. Si hay varios videos atrasados reales, conservar cada grupo y ordenar por fecha vencida; no limitar arbitrariamente a uno.

Componer toda la sección Por publicar únicamente cuando `selectedDate == today`, estado Success y grupos atrasados no vacíos. No encabezado, superficie ni mensaje vacío si no hay atrasados. En otros días, incluidos históricos, omitirla completamente; Publicados conserva su filtro y vacío contextual. Carga/error globales y la barra única se mantienen, sin dibujar candidatos inventados mientras no se conoce Success.

El candidato permanece derivado del estado real hasta que una actualización de la central cambie/elimine el próximo slot. No marcar «Publicado», quitarlo optimistamente, guardar un estado local de resolución ni adelantar la cadencia. Mantener refresh y descarte confirmado existentes: menú solo para los slots elegibles presentes, confirmación por plataforma y posterior recarga real.

**Cambio de día local:** si se estaba siguiendo hoy, al cambiar today por refresh/ON_RESUME avanzar selección al nuevo hoy, incluso dentro de la misma semana. Si el usuario eligió explícitamente otro día, conservar ese día mientras pertenezca a la semana actual; al quedar fuera, seleccionar hoy como ya hace Agenda. Resolver localmente con estado `followingToday` o comparación de selección/oldToday, sin nueva navegación ni temporizadores. Un toque de hoy vuelve a seguir hoy; un toque de otro día deja de seguirlo. Conservar callbacks actuales de selección/visibilidad al cambiar la fecha.

## 2. Agrupación segura del historial Instagram/Facebook

Evidencia: UploadWorker reporta Instagram con operationId del input y solo reporta Facebook en la rama `FacebookCrossPostResult.Published`, usando el mismo operationId, videoFile.fileName y dispositivo. Ambos eventos tienen IDs de plataforma diferentes y tiempos de reporte propios. RecordPublishRequest/UploadHistoryItemDto ya transportan operationId, fileName, deviceId y source. No inferir Facebook publicado a partir del éxito de Instagram.

Crear grupos locales de presentación a partir del historial cargado, conservando todos los eventos originales. Unir **solo una pareja real Instagram + Facebook** que cumpla simultáneamente:

- operationId no vacío e idéntico;
- fileName no vacío e idéntico exacto, sin comparar title, normalizar nombres o usar linkedFileId;
- deviceId no vacío e idéntico; source no vacío e idéntico;
- id de evento y platformId no vacíos en cada miembro, eventos distintos;
- ambos publishedAt parseables, mismo día local y pertenecientes al día seleccionado;
- en el conjunto cargado de eventos de ese operationId + fileName + deviceId + source hay **exactamente un Instagram y un Facebook**. Duplicados/reintentos ambiguos no se fusionan. Otros tipos de plataforma del lote permanecen independientes.

Usar una clave estable de grupo construida de las identidades de los dos eventos ordenadas, no del título/hora. Los eventos de pareja se consumen una vez; todos los no verificables quedan como filas independientes, incluidos faltas de operationId/nombre/origen, días diferentes, solo Instagram, solo Facebook y nombres repetidos con lotes/dispositivos distintos. No seleccionar una pareja arbitraria entre varias.

No agregar nuevas peticiones ni metadatos para lograr el cruce. Si los datos históricos no acreditan pareja, mantener filas separadas y su fallback: no afirmar que cualquier Facebook sin portada se puede resolver.

## 3. Portada, hora, logos y visibilidad de la fila agrupada

- Título/portada principal de pareja: usar el miembro Instagram como representación estable; título con respaldo fileName habitual. No fusionar títulos para acreditar identidad.
- Reutilizar **solo PublishedThumbnailSources ya validadas del evento Instagram** para la superficie compartida. Reusar su PublishedThumbnailKey/namespace/cadena de fallback existentes. Esto representa una pareja cuya identidad fue probada localmente; no copiar ese descriptor al mapa de Facebook ni relajar el resolver global de plataforma + platformId. File-stats central no incluye Facebook, por eso su resolución independiente puede quedarse sin portada.
- Una fila visible agrupada comunica bounds/visibilidad de **todos sus miembros** mediante los callbacks existentes; al disponerla retira todos. Mantener caché, deduplicación, concurrencia, cancelación de sesión/día, generación de retry y prioridad local/Nube/plataforma ya implementadas. No usar la fila agrupada como excusa para resolver todo history.
- Si la pareja tiene horas locales iguales en el formato mostrado HH:mm, puede mostrarse una hora común. Si difieren, mostrar cada hora junto a su plataforma, por ejemplo «Instagram · 10:02» y «Facebook · 10:03». No elegir la hora de Instagram como si acreditara Facebook. Ordenar la fila por el instante más reciente de sus miembros, conservando el orden descendente de historial.
- Logos existentes de 14–16 dp junto a «Instagram»/«Facebook» y nombres accesibles; no solo siglas ni logos sin etiqueta. Metadatos pueden envolver en 320 dp/texto 1.3. Una sola miniatura de 64 dp, un título y la divisoria entre filas: sin nueva tarjeta, contadores ni detalle expandible.
- Aplicar el mismo patrón logo + nombre breve a plataformas en filas independientes y candidatos para coherencia, sin cambiar paleta/componentes globales. Decoración de logos sin descripción duplicada cuando el texto ya identifica la plataforma.

## Archivos permitidos

1. `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarScreen.kt`: filtros, condición de sección, seguimiento de hoy, agrupación de presentación, fila/logo/hora y callbacks de todos los miembros.
2. `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarStudioPreview.kt`: fixtures de producción separados de Hilt/red.
3. `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/PublishedHistoryGroups.kt`: auxiliar local opcional si simplifica la agrupación pura.
4. Documentos `design/agenda-simple/OVERDUE_CROSSPOST_IMPLEMENTATION.md` y `OVERDUE_CROSSPOST_REVIEW.md`, más nota/changelog acotados de `../FEATURES.md`.

CalendarViewModel no requiere cambios: el mapa de fotos y callbacks aceptan los eventos originales. Si aparece una necesidad estricta fuera de esta lista, comunicarla antes de ampliar alcance. No tocar NavHost, UploadWorker, DTO, resolver global ni backend.

## Verificación y entrega

Previews del mismo contenido productivo: slot atrasado y slot mañana con igual fileId; varios atrasados; ningún atrasado; fecha histórica seleccionada; pareja IG/FB con operationId/nombre/dispositivo/source coincidentes y horas distintas; miembros sueltos; pareja ambigua o IDs vacíos; 320 dp/texto 1.3. Incluir descriptor de foto del IG de preview para comprobar la ruta compartida sin consultas de red.

Sol verifica por lectura: mañana/hoy/sin fecha excluidos antes de grouping; sección ausente en otro día/sin atrasados; rollover siguiendo hoy frente a selección histórica; descartes solo para plataformas atrasadas; grupos de historial sin falsos merges/eventos perdidos; una foto validada del IG de la pareja; callbacks para los dos miembros; hora honesta y logos con texto. No cambiar el contrato ni afirmar prueba instalada a partir de previews.

Luna revisa diff contra snapshot local, allowlist y `git diff --check`. Compilar `:feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet`, con workaround temporal de JAVA_TOOL_OPTIONS documentado en THUMBNAILS_IMPLEMENTATION si lo requiere el host, restaurando el entorno. No ejecutar assemble ni instalar. Registrar resultado real y limitación de render/binario nuevo. Entregar para revisión Sol sin abrir otra ronda de preferencias opcionales.
