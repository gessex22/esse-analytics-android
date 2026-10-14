# Agenda Studio · Revisión final de Sol

12 de octubre de 2026 · rama `redesign/home-metrica-tactil`.

## Resultado

Código aprobado respecto de PLAN.md. Las seis correcciones comunicadas están resueltas. Compilación de `:feature:calendar:compileDebugKotlin` y `:app:compileDebugKotlin` terminada con salida 0, confirmada por el coordinador y documentada por Luna en `IMPLEMENTATION.md`.

El build usó Temurin OpenJDK 21.0.12+8 y una opción temporal del proceso `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=...` para habilitar el fallback TCP de Gradle. El entorno anterior se restauró; no se cambió la configuración del proyecto. `git diff --check` correcto.

## Correcciones verificadas

| Hallazgo | Estado | Evidencia final en CalendarScreen.kt |
| --- | --- | --- |
| Diálogo fuera del tema Studio | Resuelto | AlertDialog se compone dentro de CreatorHomeTheme; confirmar y cancelar conservan sus callbacks. |
| Fecha sin renovación al actualizar | Resuelto | El callback común de Actualizar, pull-to-refresh y Reintentar renueva `today` antes de llamar a refresh. ON_RESUME también renueva la fecha. |
| Encabezado inconsistente con Inicio/Datos | Resuelto | «Agenda» es el primer item desplazable de LazyColumn con espacio reservado para las acciones; solo Actualizar y Perfil flotan en el overlay superior. No hay franja fija completa. |
| Scroll semanal reiniciado y offset fijo | Resuelto | `initialScrollHandled` se guarda por semana y BringIntoView apunta a la celda real de hoy. Se conserva el scroll/selección al refrescar o recomponer la misma semana. |
| Padding vertical diferente al compartido | Resuelto | CardShell permite variar el padding; SlotCard y PublishedCard usan CreatorVideoStyle.cardVerticalPadding de 4 dp. |
| Expansores con mínimo propio de 40 dp | Resuelto | Ambos controles de último publicado declaran altura mínima de 48 dp. |

## Cobertura revisada por lectura

- Candidatos exclusivamente con `nextVideo != null`. Partición exhaustiva y exclusiva entre Atrasadas, Hoy, Mañana, resto de semana, fechas posteriores y Sin fecha calculada. En domingo, mañana aparece solo en Mañana. El panel de día es la repetición contextual permitida.
- Slots sin candidato conservan cadencia y último publicado sin sumar pendientes. Success con slots vacíos conserva el historial y selector semanal. Publicados aparecen en bloques secundarios con conteos independientes; el panel lista primero pendientes y después publicados.
- Fechas, duración, intervalos, títulos, miniaturas e identidad de descarte proceden de las fuentes existentes. Sin horas, publicación automática, nueva paginación ni datos fabricados. CalendarViewModel, contratos, DTO, autenticación y backend intactos.
- Miniaturas cuadradas de 64 dp con estilo compartido; local/remoto para candidato, local/fallback para último publicado y placeholder para historial. Descarte separado del título, confirmación por video/plataforma, cancelación sin envío y fileId vacío deshabilitado.
- Una CreatorLoadingBar, indicador de PullToRefreshBox vacío, errores con reintento y datos anteriores visibles durante refresh. Selector desplazable con celdas de al menos 56 × 68 dp, semántica de fecha/actividad/selección y marcas de Hoy y seleccionado que conviven.
- NavHost limita su diff a CALENDAR: ocultación de AppTopBar/título compartido, iconos del sistema y paso de nombre/callback de perfil. Rutas, navegación inferior y otras features conservadas. Actualizar consulta realmente y Perfil abre Ajustes.
- Previews llaman directamente CalendarStudioContent, usado también por producción, sin instanciar Hilt. Fixtures exclusivos de previews con fecha fija: claro, oscuro, 320 dp, texto 1.3, selección, sin candidatos, solo historial, carga y error con datos anteriores.

FEATURES ya registra la mejora visual Android y sus límites; el coordinador comparó sus cambios contra backup y confirmó que afectan únicamente la fila Calendario y un changelog. Sol editó documentos, sin modificar Kotlin.

## Único pendiente

Revisión visual mediante render y app instalada: claro/oscuro, 320 dp, texto 1.3+, títulos/fechas largos, selección de hoy, desplazamiento semanal, expansión y confirmación. No se observó un render ni una instalación; previews declaradas y compilación no certifican esos resultados. El entorno reportado no tiene dispositivo adb conectado.
