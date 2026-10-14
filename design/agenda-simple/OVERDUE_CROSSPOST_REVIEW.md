# Agenda · Atrasados y cruce IG/FB · Revisión final de Sol

13 de octubre de 2026 · revisión contra OVERDUE_CROSSPOST_PLAN.md.

## Resultado

Código aprobado por lectura. No quedan bloqueos materiales del alcance revisado. El ajuste final de metadatos dispone logo/nombre y fecha/hora verticalmente por plataforma, con todo el ancho textual disponible y sin reducir letra. El coordinador confirmó recompilación final de `:feature:calendar:compileDebugKotlin` y `:app:compileDebugKotlin` con salida 0, usando el workaround temporal conocido y restaurando el entorno. `git diff --check` correcto.

## Criterios verificados

| Criterio | Evidencia final |
| --- | --- |
| Solo candidatos atrasados | Filtra slots con nextVideo y nextDate anterior a today antes de pendingGroups. Mañana/hoy/sin fecha no entran en metadatos ni menú aunque compartan fileId con un atrasado. Conserva varios atrasados reales. |
| Pendientes únicamente al consultar hoy | Sección completa solo en Success, selectedDate == today y grupos no vacíos; no encabezado/placeholder de pendientes en otros días o sin atrasados. |
| Permanencia y descarte | Sigue derivada del slot real hasta actualización de la central; sin estado local «publicado» ni eliminación optimista. Confirmación y recarga existentes conservadas. |
| Rollover y restauración | Seguir hoy avanza al nuevo today; selección histórica dentro de la semana permanece. Selección restaurada fuera de semana vuelve a hoy, incluso con previousToday ya inicializado. |
| Pareja acreditada | operationId, fileName, deviceId y source idénticos/no vacíos; un IG/un FB sobre evidencia global cargada, IDs válidos/distintos y mismo día local seleccionado. No mezcla por título, tiempo o linkedFileId. |
| Eventos independientes | Singles se filtran por selectedDate; ambigüedad, IDs vacíos, miembros sueltos y días distintos no inventan una pareja. Eventos originales se conservan. |
| Identidad y orden | Claves con codificación de longitudes y miembros ordenados; ocurrencias duplicadas de singles distinguibles. Orden por el instante más reciente del grupo. |
| Portada compartida segura | Grupo usa exclusivamente thumbnailEvent Instagram y su descriptor validado. No copia esa fuente al mapa Facebook ni relaja el resolver global de plataforma/platformId. |
| Visibilidad y horas | La fila actualiza/retira bounds de ambos eventos. Cada miembro muestra su hora propia; no atribuye a Facebook la hora de Instagram. |
| Adaptación y lenguaje visual | Bloques verticales por plataforma en pendientes y publicados; logos de 14 dp con nombres visibles, fecha/hora de ancho disponible, una portada/título y divisorias. Cabecera/semana, controles de 48 dp y carga única conservados. |

Se revisaron CalendarScreen, PublishedHistoryGroups y CalendarStudioPreview; se comparó CalendarScreen contra la base local `recovery/android-agenda-overdue-crosspost-20261013/before`. Sol editó solo este informe; no modificó Kotlin ni FEATURES.

## Previews y límite real

Previews del contenido productivo incluyen atrasado + mañana con mismo fileId, varios atrasados, ausencia de atrasados, pareja acreditada con horas distintas, pareja ambigua, evento con id vacío y eventos sueltos. La fecha fija ahora es miércoles 14: el fixture histórico de ayer pertenece a la semana y selecciona ese día. Se declaran 320 dp y texto 1.3; los guards de platformId vacío se revisaron por lectura.

El descriptor sintético IG apunta a archivo inexistente: representa la ruta declarada y su fallback, no una portada compartida renderizada. No se observó render de la nueva fuente ni imagen real. Hay emulador disponible, pero este bloque no generó ni instaló APK. Revisión visual del binario nuevo sigue pendiente.
