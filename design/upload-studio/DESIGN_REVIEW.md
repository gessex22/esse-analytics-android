# Subir / Publicar Studio · Revisión final

13 de octubre de 2026 · Sol · comparación contra `C:/Users/Gessemberg/.codex/recovery/android-upload-studio-20261013/before`.

## Resultado

**Aprobado por revisión de código.** No quedan bloqueos materiales de implementación. Se releyeron las fuentes finales y el footer de preview con el CoverPicker productivo. Luna confirmó compilación final `:feature:upload:compileDebugKotlin :feature:library:compileDebugKotlin :app:compileDebugKotlin` exit 0 después del último ajuste de fuentes, con fallback Java temporal, ruta inexistente y entorno restaurado. Sol verificó git diff --check sin errores, con avisos de CRLF.

Esta es una aprobación estática. No se observó render ni interacción en dispositivo; no acredita publicación, importación, teclado, reproducción LAN o recuperación real de trabajos.

## Datos, callbacks y estados conservados

- UploadScreen conserva collectors, autoselección pendingBatchFileId→initialFileId→primer pendiente, browsingList, exclusión Local/PC y lote ligado al fileId actual. La envoltura Creator no cambia las claves de remember/effects. Éxito total conserva modal, dismissBatch, limpieza de selección y redirección a Inicio.
- Nube mantiene entitlement, carga/error/vacío, importación exclusiva por importingRemoteId, importFromRemote y selección solo al devolver archivo válido. PC mantiene autorización, orden de error/conexión/vacío/lista, par video/baseUrl, watcher ref-counted y retries; se publica mediante el contenido inyectado, sin conversión a VideoFile ni WorkManager.
- Campos, plataformas elegidas y offset conservan ownership por file.id. Publicar/reintentar reciben los mismos argumentos; cancelBatch y dismissBatch no cambian. isPublishing, ya publicado, título vacío y selección vacía conservan sus gates.
- Retry normal continúa excluyendo blocked y la salida ambigua mantiene su confirmación humana específica y callback. Back/Elegir otro video conservan la confirmación de salida durante publicación. No se añade auto-reintento ni se altera navegación por perfil.
- PlatformCard mantiene observeWork(fileId,platform); el presentador puro reproduce prioridades y labels existentes de ya publicado, RUNNING, ENQUEUED, SUCCEEDED y FAILED. El val estable observedWorkInfo evita smart cast de la propiedad delegada. Facebook mantiene crosspost condicionado a Instagram y sus outputs independientes; no se convierte en job propio ni se infiere éxito por Instagram.
- La portada conserva duración positiva, preview/committedOffsetSec por filePath y captura solo al terminar el slider. Cambiar valor no dispara decodificación. El slot puro conserva frame/thumbnail/fallback/loading y offset en ms.
- PC conserva isSubmitting, preselección, título/descripción, TikTok público/privado, Facebook condicionado a Instagram, reset al elegir otro, publishLan y LanPublishResult. Fields, audiencia y botón compartidos son presentadores; el gate de botón sigue siendo !isSubmitting && selectedPlatforms.isNotEmpty(). Reproducción y play LAN permanecen.

## Presentación y navegación

- Creator cubre selector, formulario, vacío, modales y PC; el mismo Dialog/Scaffold PC ahora recibe Creator también en TopAppBar. Contenido con radios de 18 dp, padding de 14/16 dp y separación de 8/12 dp. Header/perfil de 48 dp y loading global real sustituyen la cabecera compartida, sin nuevo refresh.
- Formulario mantiene un único verticalScroll, campos y captura fuera del ciclo de vida de items lazy. Selector conserva strips lazy/claves y fuentes separadas. Labels y logos de plataformas permanecen visibles; botones/checkboxes relevantes tienen targets mínimos de 48 dp.
- Estados FAILED de lote muestran plataforma arriba y error debajo, sin ellipsis/altura fija, evitando que el mensaje largo consuma el ancho del nombre. El resto de stages/progreso no cambia.
- La pestaña aplica safeDrawing completo y protección de IME; PC embebida recibe los insets del padre. Holgura desplazable de 124 dp protege la última acción ante la cápsula flotante. El diálogo PC mantiene padding de Scaffold y usa holgura local de 16 dp, sin añadir reserva de cápsula inexistente en su ventana. La lectura de estos modifiers no demuestra su resultado en un teclado/dispositivo real.
- NavHost cambia únicamente dos condiciones UPLOAD (cabecera/iconos) y los parámetros de perfil/loading/PC. matchesDestination conserva queryfileId; argumentos, redirección de éxito y puente LAN permanecen. No se cambian rutas de otras pestañas.

## Alcance y evidencia

La comparación de hashes del baseline mostró cambios solo en UploadScreen, LocalPcPublishSheet y EsseAnalyticsNavHost, más los nuevos Components/Preview autorizados. Los 15 archivos de `protected-before.csv` coinciden: ViewModels, batch, Worker, uploaders, transportes y LibraryViewModel intactos. Cambios previos de Agenda/Videos/NavHost se conservaron. No hay cambio de contratos, dependencias, backend, Desktop/iOS ni pruebas Lab nuevas.

## Cobertura real de previews y límites

UploadStudioPreview declara nueve previews: claro/oscuro, 320 dp con texto grande, vacío, footer 800×400 y 320×400 con fields/CoverPicker/CTA reales, fuentes de estado, progreso/recuperación parcial/blocked y error largo. Usa presentadores productivos, datos de desarrollo, frame placeholder y callbacks vacíos; no instancia Hilt, WorkManager ni captura/red. Facebook usa sus composables reales, sin una fila de publicación independiente ficticia.

LocalPcPublishSheet declara dos previews claro/oscuro con fields, filas de resultado, audiencia privada y CTA compartidos. La matriz PC todavía no tiene fixtures dedicadas a audiencia pública, crosspost ni final de formulario en paisaje. La preview de fuentes de Upload muestra loading de Nube, error PC y fila Local; no cubre las tarjetas/strips con videos reales de Nube/PC. Estas son limitaciones de cobertura declarada, no comportamientos observados ni regresiones identificadas en código. Las fixtures ensamblan componentes y no validan todos los gates del contenedor con un ViewModel real.

Render, scroll/insets/IME/touch, continuidad de estados y publicación/importación/reproducción efectiva quedan pendientes de revisión en dispositivo. Sin tests nuevos, APK, instalación, despliegue ni push en esta revisión.
