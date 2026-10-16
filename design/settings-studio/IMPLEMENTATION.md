# Ajustes · Studio — 2026-10-15

## Presentación

- Cabecera propia con nombre de usuario real y Volver de 48 dp. Se retira el
  DetailScaffold de esta ruta para evitar dos cabeceras. Los iconos del sistema
  siguen el modo claro/oscuro de Studio, con insets superior e inferior propios.
- CreatorHomeTheme, Fraunces/DM Sans, fondo Studio estático y tres superficies
  continuas: Tus redes, Publicación y Preferencias. Sin numeración de secciones
  ni tarjetas independientes por red.
- YouTube, Instagram y TikTok conservan estado y acciones reales. No se añade
  Facebook como conexión independiente. Mientras el mapa inicial no ha llegado,
  se muestra Consultando y se deshabilita la acción. El estado se consulta al
  volver al primer plano, incluida la vuelta de OAuth.
- Modo de publicación, color de acento y servidor muestran el valor actual y
  despliegan sus opciones al tocar. Conserva confirmación Avanzado → Simple,
  selección de color persistida, WiFi y confirmación de cierre de sesión.
- El selector conserva el resumen del color elegido y la familia Studio sigue
  usando su paleta propia, como las otras vistas.
- Servidor conserva su componente, validación y prueba real antes de guardar.
  El borrador usa rememberSaveable y SaveableStateHolder para conservarlo al
  cerrar el desplegable o salir del área visible. NSD inicia al abrir el bloque
  y se libera al cerrarlo. Los presets usan FlowRow para envolver en 320 dp.
- Filas de redes pasan a disposición vertical en anchos reducidos o con texto
  grande; textos sin altura fija ni recorte. Grupos de selección con semántica
  de radio, switch en toda la fila, encabezados y estado expandido accesibles.
- Transiciones de 100–180 ms al desplegar o seleccionar. Sin animación continua.

## Contratos y paridad

No cambia SettingsViewModel, DTO, autenticación, central, Lab, Desktop ni iOS.
Se mantienen los contratos de PlatformAuthApi:

| Operación | Contrato conservado |
|---|---|
| Estado de red | GET /api/{youtube,instagram,tiktok}/auth/status, autenticado; consume connected. |
| Iniciar OAuth | GET /api/{youtube,instagram,tiktok}/auth/url, autenticado; client=android, installationId y deviceName; consume url y abre el navegador. |
| Desconectar | DELETE /api/{youtube,instagram,tiktok}/auth, autenticado; installationId, deviceName y source=android; vuelve a consultar estado. |
| Servidor | Validación local y GET {candidato}/api/health antes de persistir; error conserva el valor guardado. La app debe reiniciarse para aplicar el servidor, como antes. |
| Preferencias / sesión | SettingsStore para modo, WiFi y color; TokenStore.clear para salir. |

Se verificó por lectura que central y Lab ya sirven las rutas de conexión.
El preset Laboratorio sigue limitado a BuildConfig.DEBUG y el entorno se
confirma con health. No se habilitaron simulaciones, no se contactaron
proveedores ni se añadieron escenarios: esta entrega cambia presentación y
reutiliza los contratos y estados existentes.

El ViewModel existente sigue representando fallos de consulta como desconectado
y refrescando tras desconectar, sin exponer el error de DELETE. No se amplía
ese contrato en este rediseño. La UI sí comunica fallo al obtener la URL OAuth
o al abrir el navegador y evita abrir varias conexiones simultáneamente.

## Validación

Previews sin ViewModel/red para claro, oscuro, 320 dp con fuente 1.5, opciones
abiertas, consulta inicial, apertura OAuth y error. No acreditan render observado.

- Compilación de `:feature:settings:compileDebugKotlin` y
  `:app:compileDebugKotlin` correcta, incluido el código de previews.
- `:feature:settings:testDebugUnitTest`: 14 pruebas existentes, 0 fallos,
  0 errores y 0 omitidas. Son pruebas de reglas de servidor, no de interacción UI.
- `:feature:settings:lintDebug`: correcto, sin incidencias (0 errores y
  0 advertencias). El primer intento offline carecía de lint-gradle en caché;
  la ejecución con resolución de dependencias completó correctamente.
- Diff y espacios finales revisados. Sin APK, instalación, publicación ni push.

Para repetir:

```powershell
.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin :feature:settings:testDebugUnitTest --offline --no-daemon --console=plain
.\gradlew.bat :feature:settings:lintDebug --no-configuration-cache --no-daemon --console=plain
```

En este host se usó el workaround temporal existente para sockets Java
(`-Djdk.net.unixdomain.tmpdir` hacia una ruta temporal inexistente), restaurando
JAVA_TOOL_OPTIONS al terminar cada ejecución. No cambia archivos del proyecto.

Comprobación visual en el nuevo binario pendiente: scroll por encima de la
cápsula inferior; Volver desde Inicio/Más; 320 dp y fuente 1.5; abrir/cerrar
servidor conservando el borrador; diálogos Simple y cierre de sesión; OAuth y
regreso del navegador, con una cuenta de prueba cuando se autorice esa prueba.
