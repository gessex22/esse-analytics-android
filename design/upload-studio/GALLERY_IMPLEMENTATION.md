# Subir · galería local y cuadrícula de Nube

14 de octubre de 2026

«En este teléfono» abre el selector del sistema para un solo video, también cuando no hay archivos locales ni acceso a Nube/PC. Los videos locales importados permanecen en «Ya importados». La selección manual activa `browsingList` antes de abrir/importar; el ViewModel entrega un resultado Gallery con identidad y archivo exactos, que la pantalla aplica y confirma. Success y Duplicate seleccionan el `VideoFile` devuelto; error queda visible y permite reintentar. El estado de selección pendiente impide que Room/autoselección u otro import lo reemplace.

Gallery usa `ImportUseCase.import(canPersist = true, allowOriginalDeletion = false)`. No se borra el original aunque falle el grant persistente. Un Duplicate que sigue apuntando al mismo `content://` conserva su permiso. La cancelación se propaga; una copia que ya quedó registrada en Room no se elimina durante cleanup. Nube y Gallery comparten un guard de importación.

Nube presenta tarjetas de miniatura cuadrada en las filas del `LazyColumn` existente: dos columnas a 320 dp, y tres/cuatro cuando el viewport permite tarjetas de al menos 138–160 dp. El scroll vertical no está anidado. Las tarjetas reutilizables muestran nombre de hasta dos líneas, logos publicados en color, pendientes en gris y descartados en gris con diagonal decorativa; Facebook solo aparece publicado. «Próximo» conserva la comparación y los colores previos. PC conserva su presentación y callback existentes.

Las previews puras muestran CTA Gallery vacío/carga/error, cuadrícula compacta con texto ampliado y ancho adaptable, badges, Próximo, fila incompleta e importación Nube activa. No ejecutan el selector, Hilt, red ni import real; el render del picker, los grants, duplicados y thumbnails reales quedan pendientes de observación en dispositivo.

ViewModels, repositorios, WorkManager y publicación permanecen con su contrato existente. `ImportUseCase` solo agrega la opción explícita de borrado y los arreglos acotados de grant duplicado, cancelación y cleanup/commit. No cambiaron endpoints HTTP ni escenarios Lab; no se añadieron pruebas.

Validación: `:feature:ingest:compileDebugKotlin :feature:upload:compileDebugKotlin :feature:library:compileDebugKotlin :app:compileDebugKotlin` terminó con exit 0. Se usó temporalmente `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\nonexistent-socket-directory`; la ruta se comprobó inexistente y la variable de proceso se restauró. Revisión de código Sol aprobada. No se generó ni instaló APK.
