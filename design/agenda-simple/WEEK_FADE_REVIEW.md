# Agenda · Continuidad semanal · Revisión final de Sol

13 de octubre de 2026 · revisión focalizada contra WEEK_FADE_PLAN.md.

## Resultado

Código aprobado por lectura, sin bloqueos materiales del alcance revisado. El coordinador confirmó compilación final de `:feature:calendar:compileDebugKotlin` y `:app:compileDebugKotlin` con salida 0 después de requiredWidth y opt-in Foundation. `git diff --check` correcto; los avisos CRLF/LF no son errores del diff. No se ha observado render del nuevo binario.

## Criterios verificados

| Criterio | Evidencia final |
| --- | --- |
| Fechas reales de continuidad | Vecinos calculados con week.first().minusDays(1) y week.last().plusDays(1): domingo anterior y lunes siguiente, con cambio de mes/año real. |
| Decoración sin navegación | WeekEdgeDay no recibe click ni selección; clearAndSetSemantics vacío elimina anuncios de descendientes. No muestra marcadores, Hoy ni eventos de otra semana. |
| Alpha propio | Capa Offscreen y drawRect con Brush horizontal/BlendMode.DstIn solo dentro de cada vecino de 24 dp: alpha exterior 0 e interior 0.65, inverso a la derecha. Sin gradiente del fondo ni blur. |
| Medición intacta | WeekStrip exige requiredWidth(384.dp), evitando que restricciones de 336–383 dp compriman los últimos hijos. Siete controles de 48 dp y dos vecinos de 24 dp suman el ancho real previsto. |
| Centrado y clip | Desde 336 dp, Box centra la tira extendida simétrica: el bloque principal de 336 dp queda centrado y completo; clip del viewport recorta solo contexto que exceda. La máscara no afecta lunes/domingo principal ni selección. |
| Ancho reducido | Debajo de 336 dp, horizontalScroll conserva anchos de controles/contexto. ScrollState e initialScrollHandled guardados por semana; BringIntoView conserva primera visibilidad de hoy sin reset repetido. |
| Estados de Agenda | WeekDayCell conserva selección, rol, descripción, Hoy, marcadores y target mínimo 48 × 68 dp. No se modifican filtros pasado/hoy/futuro, agrupación Meta, portadas, datos o descarte. |

Se inspeccionaron CalendarScreen y CalendarStudioPreview y se comparó CalendarScreen con `recovery/android-agenda-week-fade-20261013/before`. La corrección de medición se volvió a leer tras la edición de Luna. Sol editó únicamente este informe; no modificó fuentes ni FEATURES, ni ejecutó assemble/instalación.

## Previews y límite visual

Previews del contenido productivo declaran claro/oscuro de 393 dp, ancho de 480 dp, 320 dp/texto 1.3, lunes/domingo seleccionados y fecha fija 31 de diciembre de 2026 para una semana cruzando año. Los vecinos se calculan de esa semana; no son números decorativos ficticios ni provocan consultas adicionales.

No se generó ni instaló APK en este bloque. Hay emulador disponible, pero las previews declaradas y compilación no acreditan el degradado, legibilidad o tacto del nuevo binario renderizado. Revisión visual real pendiente. El informe queda estable para el respaldo after.
