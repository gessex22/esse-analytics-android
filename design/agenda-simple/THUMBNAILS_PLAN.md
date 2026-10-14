# Agenda simple · Fecha, semana y miniaturas

12 de octubre de 2026 · plan acotado de Sol para ejecución de Luna.

## Alcance

Aplicar únicamente el pedido actual: cabecera con día y mes, semana centrada cuando cabe y portadas reales para publicaciones que puedan identificarse con seguridad. Preservar las dos superficies de Agenda Simple, agrupación de pendientes, selector que filtra solo historial, menú por plataforma, controles flotantes y una barra de carga.

Se revisaron git status, AGENTS.md y ../LAB_FEATURE_PROMPT.md. Hay cambios locales previos de Agenda y documentación: trabajar sobre ellos, sin reset/restauración. No cambiar backend, Laboratorio, DTO HTTP, endpoints, repositorios globales, paletas, fuentes, NavHost ni otras pantallas. No instalar, generar APK/instaladores, desplegar, publicar ni hacer push.

## Causa comprobada y fuentes utilizables

PublishedEventRow compone siempre PlaceholderThumbnail. UploadHistoryItemDto no contiene portada ni ID de Biblioteca remota. Su linkedFileId Long no acredita identidad de Room en este dispositivo. FileRepository.findByName usa LIMIT 1 y los nombres pueden repetirse; VideoFile tampoco dispone de IDs de publicación por plataforma. No usar el título/nombre o linkedFileId del evento para asignar directamente una portada local.

Existe `SyncRepository.getFileStats(fileName = ...)`, sin caché, que devuelve GroupStatsItemDto con plataformas/platformId/thumbnail y, cuando hay coincidencia central inequívoca, remoteLibraryVideoId/thumbnailStoredFileName. Central busca por nombre y puede devolver otro archivo homónimo: **solo aceptar el resultado si el platformId de la plataforma exacta del evento coincide con event.platformId y ambos son no vacíos**. La respuesta de otra plataforma o ID diferente no autoriza ninguna portada.

Contrato conservado: GET autenticado `/api/sync/file-stats?fileName={nombre}`, respuesta GroupStatsItemDto. Central y Lab ya implementan este endpoint; Lab devuelve portadas ficticias de plataforma y puede devolver IDs remotos nulos. GET `/api/remote-library/videos/{id}/thumbnail` se construye con la utilidad y sesión existentes. Reutilizar manejo de permisos/red/autenticación; no llamar APIs de proveedor desde Android. La central puede refrescar estadísticas/proveedores al consultar file-stats: acotar el trabajo a publicaciones visibles, nunca barrer los 60 eventos en cada carga.

## Cambios visuales concretos

- Cabecera visual `d 'de' MMMM`, por ejemplo «12 de octubre». Sin día de semana ni año visibles. Mantener una descripción accesible de fecha completa y la fecha LocalDate íntegra para selección, claves y comparaciones. No alterar las fechas de candidatos ni sus años cuando corresponden.
- Semana: cuando el ancho medido de siete celdas + gaps cabe en el viewport, centrar el grupo completo. Si no cabe, mantener desplazamiento horizontal con celdas de al menos 48 dp, sin comprimir objetivos táctiles. Conservar círculos, marcadores, selección/Hoy y scroll guardado por semana. No centrar una Row de fillMaxWidth cuyos hijos siguen anclados al inicio: centrar el contenido real o añadir espacio simétrico únicamente cuando cabe.
- Publicados: miniatura de 64 dp con forma CreatorVideoStyle; mismo metadato plataforma/hora y título actuales. El icono de respaldo permanece durante resolución, falta de identidad/portada, fallo HTTP o fallo de imagen. Sin spinner por fila, indicador extra, mensaje técnico ni nueva tarjeta.

## Resolución de portada, orden y seguridad

Para cada evento solicitado que tenga fileName y platformId no vacíos:

1. Pedir o reutilizar file-stats por el fileName exacto, sin sustituirlo por title ni normalizar espacios/capitalización. Deduplicar el nombre entre eventos del mismo día/sesión, pero validar cada evento individualmente contra su plataforma y platformId.
2. Si no coincide la identidad, dejar el placeholder. No probar portadas de otras plataformas del resultado ni otros títulos para adivinar.
3. Tras validar, si existe remoteLibraryVideoId, consultar `findByRemoteLibraryVideoId(id)` y aceptar thumbnailPath únicamente si el archivo real existe. Este vínculo estable sí identifica una copia local del archivo validado; no usar findByName/findById del evento.
4. Si no hay imagen local, construir URL Nube mediante remoteLibraryThumbnailUrl y la sesión/baseUrl actuales, únicamente con los campos remotos de esa respuesta validada.
5. Si no hay URL Nube utilizable, usar thumbnail no vacío de la **misma plataforma/ID validado**. Si tampoco existe, placeholder.

Conservar descriptor de portada y fallback para poder usar la siguiente fuente al fallar Coil: local → Nube → plataforma coincidente → placeholder. No mostrar una foto anterior de otro evento durante carga/cambio de día. Los casos sin fileName/ID y plataformas ausentes del resultado (por ejemplo Facebook si no viene en platforms) quedan honestamente sin portada.

## Trabajo asíncrono acotado

- CalendarViewModel puede incorporar estado de portadas separado del Success de agenda, o auxiliar local del módulo. No bloquear load, cambiar su contrato de datos ni convertir fallo de foto en CalendarUiState.Error/refreshError. La única barra de carga conserva su significado actual de agenda.
- UI comunica eventos **del día seleccionado y realmente visibles** en el viewport de Publicados; observar intersección de filas con viewport, sin asumir que componer todas las filas de una superficie equivale a verlas. No resolver todo history ni todos los días. Deduplicar emisiones y evitar peticiones por recomposición. Cambiar de día cancela/retira solicitudes obsoletas de esa vista.
- Caché en memoria por sesión y fileName, y estado de portada por identidad de evento (id + plataforma + platformId + fileName). Concurrencia máxima de dos consultas file-stats y deduplicación del trabajo en curso por nombre. No cambiar SyncRepository ni persistir la caché.
- Reutilización positiva durante la vida de la sesión del ViewModel; resultados negativos/fallos se recuerdan para evitar loops al componer/desplazar. Actualizar manualmente invalida negativos y metadatos de portada del día visible y permite reintento, conservando una portada válida mientras se actualiza. No disparar automáticamente múltiples reintentos de foto.
- Capturar sesión mediante tokenStore.currentSession(), comparar antes de publicar/cachar y asociar una generación a selección/refresh. Respuestas de otra sesión o generación no se publican. Cancelación no se convierte en fallo ni se traga: propagar CancellationException y limpiar el trabajo en curso en finally.
- Observar cambios de sesión para cancelar y vaciar inmediatamente mapas/caché de imágenes anteriores. No reutilizar IDs/nombres entre usuarios ni URLs con token de la sesión anterior. Construir URLs Nube para la sesión vigente; no guardar tokens/URLs autenticadas en disco, logs, documentos o claves de caché.

## Archivos permitidos

1. `feature/calendar/.../CalendarScreen.kt`: formato de cabecera, centrado de semana, observación de filas visibles y consumo/fallback de portadas.
2. `feature/calendar/.../CalendarViewModel.kt`: coordinación y estado local de portadas, visibilidad/sesión/caché/reintento, sin alterar los flujos de calendario/descarte existentes.
3. `feature/calendar/.../PublishedThumbnailResolver.kt`: auxiliar opcional para encapsular matching y resolución; no introducir otro repositorio global ni DI nueva innecesaria.
4. `feature/calendar/.../CalendarStudioPreview.kt`: previews del contenido productivo con descriptores de portada/fallback de desarrollo; no red desde previews.
5. `design/agenda-simple/THUMBNAILS_IMPLEMENTATION.md`, `THUMBNAILS_REVIEW.md` y nota/changelog acotados en `../FEATURES.md`.

Los `...` son `src/main/kotlin/com/esseanalytics/android/feature/calendar`. No modificar otros archivos de código.

## Validación y entrega

Revisar diff contra snapshot local previo y `git diff --check`. Compilar `:feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet` con el ajuste temporal documentado en agenda-simple/IMPLEMENTATION si hace falta, restaurando el entorno.

Sol verifica: nombre homónimo/ID diferente rechazado; mismo nombre entre plataformas comparte consulta pero exige match por evento; linkedFileId local no se utiliza; fuentes local/Nube/plataforma respetan prioridad y fallbacks; sin identidad/foto/fallo deja placeholder; cambios rápidos de día y sesión no publican imágenes viejas; refresh permite reintentar sin bloquear agenda; concurrencia ≤2 y ausencia de barrido de history. Comprobar fecha visual breve/semántica completa y semana centrada o desplazable a 320 dp/texto 1.3.

Mantener paridad de contratos y documentar que Lab ya soporta la ruta existente. No modificar escenarios/backend ni afirmar pruebas Lab, render o revisión instalada que no se ejecutaron. Registrar compilación real y límites de verificación; no instalar ni generar paquetes para validar.
