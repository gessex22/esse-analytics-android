# Agenda · Futuro y Meta · Revisión final de Sol

13 de octubre de 2026 · lectura focalizada contra FUTURE_META_PLAN.md.

## Resultado

Código aprobado por lectura, sin bloqueos materiales del alcance revisado. Color de fecha verificado en el snapshot final: error únicamente para nextDate anterior a today, onSurfaceVariant para futuros. Recompilación final de `:feature:calendar:compileDebugKotlin` y `:app:compileDebugKotlin` con salida 0, confirmada por el coordinador y registrada en FUTURE_META_IMPLEMENTATION.md tras ese ajuste; workaround temporal de Java aplicado y entorno restaurado. No se ha observado render del binario nuevo.

## Criterios comprobados

| Criterio | Evidencia de código |
| --- | --- |
| Pasado/hoy/futuro | Filtra slots antes de agrupar: pasado ninguno, hoy nextDate < today, futuro nextDate == selectedDate, siempre con video/fecha reales. Sección solo en Success con elegibles. |
| Semana | Usa todos los candidatos con fecha para los marcadores; no inventa recurrencias ni mueve atrasos a fechas futuras. |
| Estado y descarte | Persistencia derivada de fuente real, confirmación por slot/plataforma, followingToday y restauración conservados. |
| Campo HTTP | Diff de SyncDtos contra snapshot previo añade solamente contentId nullable con default null al final de UploadHistoryItemDto; conserva tipos/campos anteriores. |
| Identidad canónica | Agrupa IG/FB por contentId exacto/día seleccionado, pareja única con IDs/fechas válidos. No requiere nombres/lote/origen iguales cuando la central acredita el contenido. Republicaciones ambiguas quedan individuales y bloqueadas para fallback. |
| Fallback global | BatchFile se construye sobre indexed completo, incluyendo consumidos/bloqueados. Exige un IG/un FB global por lote/nombre antes de rechazar miembros consumidos. No fabrica una pareja restante al retirar evidencia. |
| Contradicciones/eventos | Diferencias no vacías de deviceId/source/contentId rechazan fallback; campos ausentes permitidos. Singles únicamente del día seleccionado, claves con identidades/ocurrencias y sin eventos seleccionados perdidos. |
| Presentación Meta | FlowRow de identificaciones completas IG/FB adyacentes cuando caben; envuelve bloques a ancho reducido. Una portada/título; hora común solo si coincide, diferencias atribuidas por plataforma. |
| Estado visual de candidatos | Rojo de error reservado para atrasados; fechas futuras usan texto secundario normal. |
| Portada/visibilidad | Descriptor exclusivo del miembro IG validado, resolver global intacto. Bounds y disposición incluyen/retiran todos los miembros con claves originales. |

Se inspeccionaron CalendarScreen, PublishedHistoryGroups, CalendarStudioPreview y la adición DTO. El DTO se comparó contra `recovery/android-agenda-future-meta-20261013/before`; `git diff --check` correcto salvo avisos de conversión CRLF/LF, sin errores. Sol no editó Kotlin ni FEATURES.

## Previews y límites

Fixtures declarados cubren pasado/hoy/futuro con candidato exacto, atraso que no cambia de fecha, pareja canónica con nombres diferentes y lote/origen ausentes, fallback de lote, contentIds distintos con mismo nombre/lote, republicaciones ambiguas, miembros sueltos y 320 dp/texto 1.3. Previews usan contenido productivo y descriptores sintéticos de portadas; no son evidencia de una imagen real renderizada.

Central ya expone contentId; eventos pueden seguir null si el productor no lo envió. Lab omite ese campo y solo conserva compatibilidad/fallback, sin cobertura canónica verificada. No prometer unir históricos sin identidad/lote ni usar fileName/título aislados para hacerlo.

Hay emulador disponible, pero este bloque no generó ni instaló APK. Revisión visual del nuevo resultado pendiente. No se certifica render ni prueba canónica de Lab. El informe queda estable para el respaldo final.
