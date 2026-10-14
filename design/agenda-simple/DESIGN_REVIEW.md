# Agenda simple · Revisión final de Sol

12 de octubre de 2026 · rama `redesign/home-metrica-tactil`.

## Resultado

Código aprobado respecto de PLAN.md y la estructura de las referencias inspeccionadas. Los cuatro ajustes están resueltos. Compilación final de `:feature:calendar:compileDebugKotlin` y `:app:compileDebugKotlin` con salida 0, confirmada por el coordinador y registrada por Luna en IMPLEMENTATION.md.

El build usó Temurin OpenJDK 21.0.12+8 y el ajuste temporal de JAVA_TOOL_OPTIONS para una ruta de sockets inexistente; el entorno previo se restauró. `git diff --check` correcto. Sol no editó Kotlin ni ejecutó assemble, instalación o publicación.

## Ajustes verificados

| Punto | Estado | Evidencia final |
| --- | --- | --- |
| Selección circular sin tarjetas diarias | Resuelto | WeekDayCell usa superficie transparente y círculo sobre el número seleccionado. Conserva target de 48 dp, semántica selected, actividad y marca Hoy. |
| Fechas repetidas en publicados | Resuelto | PublishedEventRow contiene plataforma + hora HH:mm y título. La fecha queda en el encabezado de la sección; no hay tercera línea repetida. |
| Año en candidatos cuando corresponde | Resuelto | candidateShortDate añade año si difiere de today.year; se usa también en atrasados. Hoy/Mañana mantienen etiquetas relativas inequívocas. |
| Preview de otro día dentro de semana | Resuelto | La selección y sus eventos usan previewToday.plusDays(2), miércoles de la semana fija del fixture. |

## Cobertura de código

- Cabecera de fecha y semana desplazables, con Actualizar/Perfil flotantes de 48 dp y una sola CreatorLoadingBar. Sin título redundante Agenda.
- Dos superficies continuas: Por publicar y Publicados del día, filas compactas y divisorias entre ellas. Sin tarjetas individuales, cadencias, buckets de fechas, contadores ni último publicado expandible.
- Candidatos únicamente con nextVideo. Agrupación solo por fileId no vacío; IDs vacíos conservan identidad por plataforma según el contrato. Fechas/plataformas originales visibles, orden por menor fecha y miniatura del mismo grupo con prioridad local.
- Elegir un día filtra solo historial; próximos candidatos permanecen visibles. Cada evento de historial conserva su identidad; no hay fusiones por título o linkedFileId. Slots vacíos no ocultan el historial.
- Menú por plataforma entrega el slot exacto a la confirmación. fileId vacío deshabilita el descarte; cancelar no envía. Menú y diálogo están dentro de CreatorHomeTheme.
- Selección/scroll se conservan dentro de la semana al actualizar; al cambiar la semana se selecciona hoy. La fecha se renueva al actualizar y volver a primer plano. Errores con reintento y refresh con datos anteriores visibles.
- Previews usan CalendarStudioContent productivo, separado de Hilt, con fixtures exclusivos de desarrollo para ancho reducido/texto grande, grupos compartidos, sin fecha/identidad, selección, history-only, vacíos, carga y errores.
- Comparación efectuada contra la base local en recovery/android-agenda-simple-20261012/before. Esta iteración preserva NavHost y CalendarViewModel previos; no altera contratos, DTO, backend, componentes globales ni otras pantallas. Luna registra documentación y FEATURES.

## Único límite pendiente

Revisión visual mediante render y app instalada: correspondencia visual con referencias, 320 dp/texto 1.3+, fecha y controles, selección/scroll, filas agrupadas, menú y confirmación. No se observó un render ni una instalación; las previews declaradas y compilación no certifican esos resultados. El entorno reportado no tiene dispositivo adb conectado.
