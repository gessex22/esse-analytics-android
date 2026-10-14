# Subir · Revisión final de Galería y Nube

14 de octubre de 2026 · Sol · baseline CURRENT `C:/Users/Gessemberg/.codex/recovery/android-upload-gallery-20261014/before`.

## Resultado

**Aprobado por revisión de código.** Sin bloqueos materiales pendientes. Se releyeron las fuentes finales, incluidos los dos guards de resultado pendiente en VM y la diagonal decorativa excluida del árbol accesible. Luna confirmó compilación definitiva de ingest/upload/library/app exit 0 después del último cambio, ruta Java de fallback inexistente y variable temporal restaurada. Sol comprobó git diff --check sin errores; aparecen avisos de CRLF.

La aprobación es estática: picker, importación, duplicado, fallback de copia, permisos y render no se ejecutaron ni observaron en dispositivo.

## Galería y entrega exacta

- CTA de mínimo 48 dp inmediatamente después de la cabecera, incluso sin archivos/entitlements Nube o PC. El retorno vacío anterior ya no la oculta. «Ya importados» es acceso secundario separado; conserva los archivos y selección existentes.
- PickVisualMedia con VideoOnly selecciona un video. Launcher fuera de ramas, picker/browsing/identidad de fuente guardados con rememberSaveable; URI null solo cierra la petición y no invoca importación ni selecciona un archivo. No se añaden permisos globales ni dependencias/manifest.
- Galería utiliza el ImportUseCase existente con canPersist=true y allowOriginalDeletion=false. Success entrega su file y Duplicate su existing exactos, incluso si ese archivo no figura en la lista filtrada de pendientes. No se busca el primer pendiente como sustituto ni se publica automáticamente.
- GalleryImportSelection(requestId,file) permanece observable en VM hasta ack por el mismo id. La UI lo consume en LaunchedEffect, selecciona ese archivo, limpia LAN y después confirma entrega. Se evita el callback que capturaba una composición descartada durante navegación/recreación.
- La autoselección mantiene prioridad pendingBatchFileId→initialFileId→primer pendiente al entrar normalmente; mientras hay import, resultado pendiente o error Galería no elige otro archivo por una actualización Room. Error queda accesible para reintentar. Browsing manual permanece guardado. Las fuentes bloquean selección hasta entregar/confirmar el resultado.
- Ambos entrypoints VM tienen guard síncrono busy || pendingGallerySelection != null antes de lanzar coroutine. La UI también incluye ready en busy; Galería y Nube no pueden importar simultáneamente ni reemplazar un resultado aún no entregado. Finally restablece loading/guard; cancelación se propaga sin callback/error ficticio. Importación Nube conserva callback legado y resultado/errores actuales, con exclusión compartida.

## Original, permisos y commit

- allowOriginalDeletion=true por defecto conserva la política de borrado de callers Share Sheet/SAF. El false de Galería corta la condición antes de leer settings: ni grant persistido ni fallo de persistencia/fallback de copia autorizan borrar el original.
- Duplicate no libera un grant que el archivo existente sigue usando cuando filePath==uri.toString(). Para un URI nuevo no reutilizado conserva release best-effort; copia temporal de duplicado se elimina. No cambia identidad/deduplicación/metadata.
- CancellationException tiene catch propio y rethrow. Cleanup de copia depende de !committed: no elimina el archivo después de registrarlo. ensureActive precede un bloque NonCancellable breve limitado a insert+marcar commit; copia, probe, miniatura y red no se convierten en trabajo no cancelable.
- Las correcciones de grant/cancel/cleanup afectan el importer común; son defensas aprobadas para el flujo nuevo. La política de borrado por defecto y los callers anteriores permanecen. importFromRemoteLibrary y merge remoto coinciden exactamente con el baseline.

## Cuadrícula de Nube

- Filas lazy agrupadas dentro de una sola LazyColumn; dos columnas a 320 dp y reflujo a tres/cuatro según ancho. No hay LazyVerticalGrid vertical anidado ni altura ilimitada. Claves de fila derivadas de ids y key por tarjeta; spacers mantienen ancho en la fila final incompleta.
- Surface de tarjeta con imagen de ancho completo/aspectRatio(1f), nombre de dos líneas y etiquetas debajo. Thumbnail helper autenticado existente sin reconstrucción/log de URLs. Loading pertenece al id importado y el gate compartido deshabilita taps mientras corresponde.
- Badges derivan de platforms/platformsDiscarded, con prioridad de publicado: color de marca, pendiente gris, descartado gris con diagonal decorativa no anunciada. Facebook solo aparece realmente publicado; no se crea un job/crosspost ficticio. Próximo conserva match por fileName, plataformas, texto y colores, con wrap permitido.
- Entitlement, carga/error/vacío y retry de Nube permanecen. PC no cambia de diseño ni publicación.

## Alcance, previews y límites

Hashes contra el backup confirmaron únicamente cinco fuentes cambiadas: UploadScreen, UploadViewModel, UploadStudioComponents, UploadStudioPreview e ImportUseCase. Los métodos de publicación/reintento/cancelación y resto del tail del VM, así como import/merge remotos del use case, coinciden exactamente con before. NavHost, PC, Workers/uploaders/transporte, DTO/repositorios y cambios previos se conservan. No cambian contratos HTTP, backend, otras plataformas ni escenarios Lab.

Tres previews nuevas usan presentadores productivos: CTA vacío/loading/error, Nube de dos columnas en 320 dp/fontScale1.3 y reflujo ancho. Fixtures de tres videos, última fila impar, estados reales declarados de plataforma, Próximo/import activo y thumbnails placeholder; sin Hilt/red/picker real. No existe una fixture oscura específica de la cuadrícula nueva ni un render observado de ella.

Pendientes de observación real: abrir/cancelar Photo Picker, grant persistente y fallback de copia con opción de borrar original activada, Duplicate con mismo URI, entrega tras navegación/recreación, errores, scroll/insets/touch y apariencia final. El resultado pendiente en VM no añade persistencia ante muerte del proceso ni conserva todos los drafts después del ack. Sin tests, APK, instalación, despliegue ni push en esta revisión.
