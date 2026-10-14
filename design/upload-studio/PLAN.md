# Subir / Publicar · Studio Android

13 de octubre de 2026 · Sol para implementación y posterior revisión de Luna.

## Objetivo y alcance

Rediseñar la pestaña completa: selector de origen, formulario de publicación local/importado, progreso/resumen/modales y contenido PC inyectado desde NavHost. Paleta Creator, fondo Studio, cabecera propia y tarjetas compactas redondeadas. Mantener datos, capacidades, rutas y operaciones existentes. AGENTS Android y ../LAB_FEATURE_PROMPT.md leídos: esta iteración no cambia HTTP, auth, estados compartidos ni escenarios Lab.

## Implementación visual, en tres fases de la misma entrega

1. **Contenedor y selector.** CreatorHomeTheme debe cubrir toda UploadScreen, incluidos estado vacío, modales y rama PC. CreatorStudioBackdrop fijo detrás del contenido. Cabecera «Subir» con tipografía CreatorSerif, perfil de 48 dp y una CreatorLoadingBar alimentada por isRefreshing real que ya observa NavHost. No añadir un botón de actualizar: esta pantalla no tiene hoy ese callback; mantener Reintentar de Nube/PC. Cabecera/títulos en flujo desplazable para no consumir altura del formulario; controles flotantes con clearance superior reservado para no cubrir inputs/taps. Mantener fuentes separadas «Nube», «PC local» y «En este teléfono», los strips lazy y sus claves; miniaturas/fallback/nombre/Faltan/Próximo preservados. Filas locales compactas, tarjetas de origen de radios 16–18 dp, padding 12–16 dp, separación 8–12 dp. No nuevos filtros, ordenación, paginación ni contadores derivados.
2. **Formulario y estados.** Reemplazar composición Swiss/Bento de UploadScreen por helpers Creator locales: tarjeta/superficie, sección, botón primario/secundario y estados. Mantener un único verticalScroll para el formulario, sus remember por file.id y efectos; no convertirlo a lazy ni mover propietarios de draft/captura al ciclo de vida de items. Selección de archivo arriba con «Elegir otro video», bloque de progreso/resumen real, Detalles, Portada, Plataformas/Facebook y Publicar al final. Inputs y checkboxes conservarán sus gates actuales; labels visibles con logos, nunca solo iconos. Estado de plataforma y mensajes pueden envolver para 320 dp/texto grande. Publicar, cancelación/reintento/confirmaciones y perfil tienen targets mínimos 48 dp. Nada fijo encima del botón Publicar ni de la portada.
3. **PC y previews.** Estilizar únicamente presentación de LocalPcPublishContent y sus filas/opciones en LocalPcPublishSheet.kt con Creator y tarjetas equivalentes. Mantener el contenedor Dialog actual y el puente inyectado, sin dependencia nueva feature→feature. El contenido compartido también se ve desde Videos: esa adaptación visual debe ser coherente sin cambiar sus callbacks. Conservar miniatura tocable y overlay play de PC (el retiro previo de play se limitó al listado Videos). Extraer presentadores puros mínimos para que previews usen exactamente las tarjetas/fields/estados productivos, conservando collectors/VM/capturas/remember en sus wrappers.

## Contratos de interacción que no cambian

- Autoselección: pendingBatchFileId precede initialFileId y luego files.firstOrNull; browsingList impide reautoselección al elegir manualmente; selectedFile y selectedLan conservan exclusión y ownership. Lote se muestra solo si fileId coincide con el archivo actual. Éxito COMPLETED abre modal; cerrar llama dismissBatch, limpia selección y conserva redirección a Inicio.
- Nube: gate canUseCloudStorage, carga/error/vacío reales, un importingRemoteId; ningún segundo tap/import mientras haya importación. importFromRemote selecciona formulario solo si entrega VideoFile; mensaje/clearRemoteImportError permanecen. Sin bytes/Nube no implica publicación directa.
- PC: gate canSeeLanLibrary, error antes que conexión/baseUrl nula/vacío/lista, watcher ref-counted start/stop y retries intactos. Se conserva par video/baseUrl y publicación server-side inyectada; nunca convertir LAN en VideoFile ni WorkManager. resetLanPublishState sigue ligado a elegir otro video, junto con el callback original.
- Local: selectedPlatforms inicial excluye publicadas/descartadas; PlatformCard mantiene observeWork(fileId,platform), checked/alreadyDone, habilitación y outputs reales. Mantener Facebook como crosspost de Instagram y sus gates/output propios, nunca nuevo job/plataforma independiente.
- Publicación: file, selectedPlatforms, title, description, offset ms y crossPostFacebook llegan sin cambios a publish/retryFailedInBatch. Mantener isPublishing, cancelBatch, summary de fracaso/parcial/cancelación, hasRetriable que excluye blocked, confirmNotPublished y el diálogo humano específico. No auto-reintento ni simplificación de confirmaciones.
- Salida local: BackHandler y «Elegir otro video» mantienen showExitConfirmation cuando hay lote activo, mismo texto/confirmar/cancelar. No añadir nuevos gates a perfil, navegación o PC.
- Portada: durationSec null/<=0 sigue sin picker. preview solo se captura al onValueChangeFinished, con el mismo committedOffsetSec y claves filePath/offset; mover slider no decodifica frames. Bitmap/thumbnailPath/fallback/loading y ms conservados.
- PC: title/description/selectedPlatforms, isSubmitting, TikTok privado/público, Facebook condicionado a Instagram, publishLan y LanPublishResult InProgress/Success/Failure sin cambios. No nueva pausa/reanudación/cancelación de PC ni porcentajes ficticios.
- Mostrar solo progreso/errores existentes. Sin nuevos valores, estados, contadores, éxito Facebook inferido de Instagram ni links ficticios. El modal de éxito conserva contenido/callback actuales.

## Navegación, espacio y teclado

- En NavHost añadir únicamente matchesDestination(Routes.UPLOAD) a hideTopBar y a la condición de iconos del sistema Creator. El helper existente cubre `upload?fileId={fileId}`: no reemplazar la ruta ni la resolución de argumentos. Pasar username, onProfileClick hacia SETTINGS e isAnyRefreshing; conservar onPublishedAllSuccess y lanPublishContent exactamente.
- El Scaffold exterior tiene insets cero y navegación flotante real. La pestaña nueva consume status/nav/IME una vez y reserva holgura desplazable de al menos 124 dp para la cápsula más borde, siguiendo Videos; inputs deben poder subir al abrir teclado. Aplicar esa protección a selector/formulario/PC sin dos scrolls ni doble suma de nav/IME. Dentro del diálogo PC, respetar padding de su Scaffold y no duplicar barras.
- A 320 dp/fontScale1.3 y paisaje, títulos, botones, labels, errores y acciones pueden envolver; sin alturas fijas de tarjeta/resultado/formulario. Todo el formulario y la última acción Publicar deben poder desplazarse por encima de cápsula/teclado.

## Allowlist y recuperación

- feature/upload/src/main/kotlin/com/esseanalytics/android/feature/upload/UploadScreen.kt.
- Nuevos UploadStudioComponents.kt (helpers/presentadores puros, sin VM) y UploadStudioPreview.kt en ese mismo directorio; extraer solo lo necesario, sin nueva arquitectura de estado.
- feature/library/src/main/kotlin/com/esseanalytics/android/feature/library/LocalPcPublishSheet.kt: presentación y presentador puro para fixtures PC; no lógica, player ni I/O.
- app/src/main/kotlin/com/esseanalytics/android/app/EsseAnalyticsNavHost.kt: únicamente integración UPLOAD indicada.
- design/upload-studio/PLAN.md, IMPLEMENTATION.md y DESIGN_REVIEW.md; nota/matriz/changelog de ../FEATURES.md al cierre por coordinación, preservando historia. No FEATURES durante planificación.
- Fuera de alcance: UploadViewModel, PublishBatchState, Workers/uploaders/transportes, DTO/repos/auth/backend, Gradle/dependencias, core/designsystem, otras pantallas/plataformas y reproductores. No tests nuevos ni modificaciones de tests existentes.
- Antes de editar: git status y backup CURRENT `C:/Users/Gessemberg/.codex/recovery/android-upload-studio-20261013/before` con allowlist y FEATURES, preservando todo trabajo previo. Resultado/documentación a after tras revisión. No reset/restaurar desde HEAD.

## Criterios de cierre

- Previews puras sin Hilt/red/captura/WorkManager real: vacío; selector Local/Nube/PC con gates, carga/error/importación; formulario 320 dp/fontScale1.3 y oscuro; progreso PENDING/UPLOADING/SUCCESS/FAILED/CANCELLED; parcial/blocked con confirmación; estado Facebook separado; PC privado/público/error; título largo y acciones finales en paisaje corto. Fixtures de desarrollo y misma composición productiva, sin porcentajes o datos de cuenta inventados en producción.
- Sol revisa diff contra before, callbacks/effects/gates exactos, clave queryfileId, tema de modales/PC, targets, cobertura inferior e independencia de lógica. Declarar previews no acredita render. Registrar limitación visual/runtime con precisión.
- Luna ejecuta git diff --check y compila `:feature:upload:compileDebugKotlin :feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet` con workaround temporal JAVA_TOOL_OPTIONS documentado en Videos IMPLEMENTATION.md, ruta inexistente y restauración en finally.
- Completar las tres fases antes de cierre. Sin tests, APK, instalación, despliegue, publicación ni push; no afirmar pruebas Lab nuevas.
