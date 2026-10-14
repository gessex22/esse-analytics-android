# Agenda · Fecha, semana y portadas · Revisión de Sol

12 de octubre de 2026 · revisión focalizada contra THUMBNAILS_PLAN.md.

## Resultado

Código aprobado por lectura tras las dos correcciones comunicadas. Fecha breve, centrado y portadas respetan el alcance de Agenda Simple. Compilación final calendar/app: exit 0. `git diff --check` correcto.

## Correcciones verificadas

| Hallazgo | Estado | Evidencia final |
| --- | --- | --- |
| Revisión de fotos borraba la visibilidad vigente | Resuelto | Solo cambiar de día limpia las entradas. La revisión reemite la instantánea visible; se guardan bounds de fila, se recalcula intersección al cambiar viewport y DisposableEffect retira filas desmontadas. |
| Refresh no reiniciaba imágenes con fuentes iguales | Resuelto | El descriptor incluye namespace opaco y generación. Refresh cambia la generación: sourceIndex vuelve a cero y la clave Coil cambia aunque las URLs sean iguales. Scroll/visibilidad no incrementan generación; las resoluciones guardadas se reutilizan al volver al día. |

## Comprobaciones de código

- Cabecera visual d de MMMM; fecha completa en accesibilidad. Semana centrada si cabe o desplazable con targets de 48 dp, manteniendo selección/Hoy y scroll por semana.
- Solo eventos publicados del día seleccionado cuyas filas intersectan el viewport solicitan portada. Sin barrido de los 60 eventos ni peticiones por cada recomposición.
- Identidad validada por plataforma exacta y platformId coincidente no vacío. fileName localiza file-stats, sin servir como prueba de identidad; no se asigna una imagen por título o linkedFileId.
- Prioridad local vinculada por remoteLibraryVideoId y archivo real → URL Nube mediante utilidad existente → thumbnail de la misma plataforma validada → placeholder. Coil avanza entre fuentes al fallar.
- File-stats deduplicado por sesión/nombre, caché de resultados positivos/negativos y Semaphore(2). Refresh invalida metadatos de visibles y permite reintento; falta/fallo de foto no se vuelve error de Agenda ni añade indicadores de carga.
- Cambio de sesión cancela consultas, vacía mapas/cachés y cambia namespace; sesión atómica se compara antes de publicar. Cambio de día cancela trabajo y generación rechaza respuestas obsoletas. CancellationException de resolución se propaga; finally limpia trabajo en curso.
- Claves Coil opacas, sin token ni URL en su nombre. Estado de portadas en memoria, separado de CalendarUiState. El usuario mantiene los datos/fotos disponibles mientras se actualizan metadatos.
- Dos superficies, agrupación de candidatos, descarte exacto por plataforma, controles flotantes y única CreatorLoadingBar conservados. No se cambian DTO, endpoints, backend, repositorios globales, paleta ni NavHost en este bloque.
- Coordinación de portadas en CalendarViewModel; no fue necesario crear PublishedThumbnailResolver. Previews del contenido productivo incluyen descriptor de desarrollo para fallback sin consulta de red.

Sol editó exclusivamente este informe; no modificó Kotlin, no ejecutó assemble ni generó/instaló APK.

## Validación pendiente y límites

No se observó un render de la nueva fuente ni sus imágenes reales. Hay emulador conectado, pero este bloque no genera ni instala APK; una captura de la versión anterior instalada no acredita estos cambios. La revisión visual del nuevo resultado sigue pendiente.
