# Agenda · Continuidad de la tira semanal

13 de octubre de 2026 · plan visual acotado de Sol para Luna.

## Alcance

Añadir contexto real en los extremos de CalendarWeek: domingo anterior al lunes inicial y lunes posterior al domingo final, difuminados mediante degradado horizontal de alpha. Los siete días principales siguen centrados y plenamente legibles. No cambia la navegación semanal ni la selección, filtros de pasado/hoy/futuro, Meta, portadas, ViewModel, DTO o contratos.

Git status contiene cambios locales de rondas anteriores; conservarlos sin reset/restauración. Antes de editar, copiar los archivos del alcance al nuevo `C:/Users/Gessemberg/.codex/recovery/android-agenda-week-fade-20261013/before`; resultado y documentos irán a after tras revisión. No generar APK, instalar, desplegar, publicar, hacer push ni añadir tests.

## Decisiones concretas

- Fechas vecinas: `week.first().minusDays(1)` y `week.last().plusDays(1)`, incluso al cruzar mes/año. No construir solo números ni usar fechas fijas de preview en producción.
- **Vecinos exclusivamente decorativos:** sin click, rol/selected, contentDescription, eventos de historial, puntos de candidato/publicado ni marca Hoy. Suprimir toda su semántica de descendientes. No llamar onDateSelected ni resolver miniaturas por ellos; selección continúa limitada a la semana actual.
- Mostrar abreviación de día y número en la misma alineación vertical que WeekDayCell, con tipografía/colores existentes. Sin círculo seleccionado, tarjeta, nuevo fondo o gris artificial. Los vecinos tienen ancho visual de 24 dp; no necesitan target de 48 dp porque no son controles.
- Siete días principales mantienen ancho propio de 48 dp y altura mínima actual de 68 dp, círculo/selected/Hoy, marcadores y accesibilidad existentes. Mantener sus targets contiguos sin superposición; no reducirlos para que quepan nueve fechas.

## Geometría responsive

Usar una tira extendida de nueve posiciones con siete controles centrales de 48 dp y contexto de 24 dp a cada lado. Sin gaps entre las posiciones centrales en esta iteración, coherente con el centrado actual cuando la semana cabe: principal = 336 dp, total visual = 384 dp.

1. Si el viewport tiene al menos 336 dp, centrar exactamente el bloque principal de siete días respecto del viewport. Ubicar los vecinos inmediatamente antes/después; pueden quedar parcialmente recortados al borde en anchos de 336–383 dp, sin invadir los siete controles. Clip del viewport impide dibujar fuera de la tira. En 393 dp de pantalla con márgenes actuales, el contexto queda visible aproximadamente 20 dp en cada extremo mientras todos los principales permanecen completos.
2. Si no caben los siete controles (por ejemplo 320 dp), toda la tira extendida tiene desplazamiento horizontal, manteniendo anchos de 48/24 dp. Conservar la primera visibilidad del día actual y el scroll guardado por semana; los extremos vecinos aparecen al desplazarse hasta ellos. No prometer nueve fechas simultáneas en ancho reducido ni comprimir letra/targets para lograrlo.
3. Si se implementa un contenedor desplazable también para el ancho intermedio, su posición inicial debe centrar el bloque principal (offset simétrico) y no anclar las nueve posiciones a izquierda. No alterar la posición elegida por el usuario en cada recomposición/refresh.

La transparencia se aplica **solamente a la composición de cada vecino**, no al viewport entero ni al bloque principal. Evitar overlays que consuman tacto o degraden lunes/domingo principales. No usar un fade global para ocultar controles cuando se desplazan.

## Degradado real

Renderizar cada vecino en una capa propia con máscara de alpha horizontal: izquierdo desde 0 en el borde exterior hasta aproximadamente 0.65 en el interior; derecho desde aproximadamente 0.65 en el interior hasta 0 en el exterior. Usar Brush horizontal y composición DstIn/capa offscreen o equivalente real de alpha. No dibujar un degradado del color de fondo encima ni usar blur global/RenderEffect.

El fondo Studio queda visible a través de la letra, incluido en oscuro. No alterar paleta ni tintar etiquetas; alpha uniforme `.alpha(...)` por sí solo no satisface el degradado solicitado. Las máscaras terminan en el límite de sus cajas decorativas: ningún píxel de selección, Hoy o semana principal pierde alpha.

## Allowlist

- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarScreen.kt`: geometría de CalendarWeek y composable decorativo/máscara local. WeekDayCell conserva su comportamiento.
- `feature/calendar/src/main/kotlin/com/esseanalytics/android/feature/calendar/CalendarStudioPreview.kt`: previews del contenido productivo con extremos/contexto.
- `design/agenda-simple/WEEK_FADE_IMPLEMENTATION.md`, `WEEK_FADE_REVIEW.md` y únicamente nota/changelog acotados de `../FEATURES.md`.

Sin cambios en helper de agrupación, ViewModel, resolver, DTO, NavHost, otras pantallas, dependencias ni recursos globales.

## Validación y entrega

Previews: claro/oscuro a 393 dp y un ancho amplio; 320 dp con texto 1.3; lunes y domingo seleccionados; semana cruzando mes/año (por ejemplo fecha fija 31 de diciembre de 2026). Usar las fechas reales calculadas de esa semana para ambos vecinos. No agregar consultas para datos de otra semana.

Revisión Sol por lectura: vecinos domingo/lunes correctos, sin semántica/acciones; principal centrado y sin máscara; targets ≥48 dp; scroll y selección conservados; degradado de alpha propio en cada vecino, sin fondo/blur; alcance de diff y preservación de estados de Agenda. Previews declaradas no acreditan render.

Luna revisa diff contra before y `git diff --check`. Compilar `:feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet`, reutilizando workaround temporal de Java si el host lo necesita y restaurando entorno. No assemble/instalación. Registrar compilación real, recuperación after y revisión visual del binario nuevo pendiente aunque exista emulador. Entregar para revisión Sol sin ampliar el bloque.
