# Más · Studio Android · 2026-10-15

La ruta «Más» ahora usa CreatorHomeTheme, el fondo estático de Studio y una
cabecera propia. Los destinos aparecen en una superficie continua con
divisorias, iconos y etiquetas; se retiraron las descripciones secundarias
para reducir ruido. Las filas conservan un mínimo táctil de 64 dp y semántica
de botón. El contenido desplaza bajo la navegación flotante con espacio
inferior e insets de sistema aplicados una sola vez.

Se conservan los destinos y permisos existentes: Sincronización → SYNC,
Estadísticas → STATS_DETAIL (con su retorno de detalle), Historial → HISTORY,
Usuarios solo para owner, Biblioteca remota solo con canUseCloudStorage, Gemas
y Ajustes. Videos sigue únicamente en la navegación inferior. No se agregan
accesos de perfil porque Ajustes ya está disponible como destino de esta lista.

La composición `MoreStudioContent` no depende de NavController, Hilt ni red.
Las previews incluyen claro, oscuro, 320 dp con fuente 1.5, usuario normal,
owner y acceso cloud. Son fixtures de código: no se renderizaron. Contratos,
ViewModels, gates, backend, Laboratorio, Desktop e iOS no cambian.

## Validación

- `:app:compileDebugKotlin`: correcto.
- `:app:lintDebug`: correcto, 0 errores y 3 advertencias previas de recursos
  del launcher (`ObsoleteSdkInt` y `MonochromeLauncherIcon`); ninguna apunta a
  los archivos de esta vista.
- `git diff --check`: correcto.
- La revisión visual en teléfono queda pendiente; no se generó ni instaló APK.
