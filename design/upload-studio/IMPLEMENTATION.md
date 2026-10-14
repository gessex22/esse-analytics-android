# Subir / Publicar · implementación Android

13 de octubre de 2026

La pestaña usa CreatorHomeTheme y CreatorStudioBackdrop en el selector, el formulario, los diálogos y la rama PC. La cabecera propia, el perfil de 48 dp y el indicador real de refresh sustituyen la barra superior compartida. Nube, PC local y teléfono mantienen sus gates y estados; el formulario coloca el video seleccionado con su miniatura arriba, seguido por detalles, portada, plataformas y Publicar al final del scroll. El contenido PC compartido mantiene su reproducción y envío server-side.

Los controles reutilizables cubren campos, tarjeta/selector de portada, filas con logos/estados, recuperación de lote, CTA y accesibilidad de plataforma. Las previews puras incluyen el formulario vacío, error de fuente PC, PC claro/oscuro con resultados de ejemplo, fila de audiencia TikTok en estado privado, crosspost Facebook activado/desactivado, salida de Facebook, error de publicación largo, recuperación parcial/bloqueada y footer de publicación con selector real de portada en 320×400 y 800×400. No hay fixture de TikTok público ni de crosspost LAN. Son fixtures de desarrollo y no verifican el render en dispositivo.

Se conservaron ViewModels, repositorios, DTO, callbacks, gates, WorkManager, reintentos, cancelación, confirmación de estado ambiguo, crosspost de Facebook, captura diferida de portada y resultados LAN. No cambiaron contratos ni escenarios Lab y no se añadieron pruebas.

Validación: `:feature:upload:compileDebugKotlin :feature:library:compileDebugKotlin :app:compileDebugKotlin` terminó con exit 0. Se usó temporalmente `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\nonexistent-socket-directory`; la ruta se comprobó inexistente y la variable de proceso se restauró. Revisión de código de Sol aprobada. Render del binario pendiente; no se generó ni instaló APK.
