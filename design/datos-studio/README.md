# Datos · implementación Studio

## Entrega del 11 de octubre de 2026

Plan: [PLAN.md](PLAN.md). Desarrollo delegado a GPT-6 Luna en el worktree
`codex/datos-studio-luna`; revisión e integración en `redesign/home-metrica-tactil`.

### Cambios

- Datos reutiliza Fraunces, DM Sans, la paleta Studio, el fondo animado y los
  controles flotantes de Inicio. La ruta desde Más conserva su botón Atrás.
- Filtro Comparadas con icono de capas y filtros con logos de YouTube, Instagram
  y TikTok. Solo el activo muestra su nombre, con expansión y contracción animadas.
  Gráfico con una
  barra por video que suma las métricas de las plataformas en Comparadas y
  muestra solo la plataforma elegida en los demás filtros. No hay leyenda ni
  nombres de plataformas dentro de la tarjeta. El mismo gráfico puede ampliarse.
- Resumen debajo del gráfico y antes de la lista: cifra seguida del icono a la
  derecha, sin texto visible. Sus celdas seleccionan la métrica y conservan los
  nombres accesibles.
- Tocar una miniatura marca el video, despliega su detalle y desplaza la lista
  hasta su fila. La misma interacción está disponible en el gráfico ampliado.
- Detalles con encabezados comunes, una fila por plataforma y cifras alineadas.
  La columna Comentarios tiene más ancho y encabezado en una línea; la presentación
  compacta usa filas visibles de 40 dp con centros separados 48 dp.
  En anchos reducidos o texto grande, cada plataforma usa cifras etiquetadas con
  más espacio. El enlace abre la publicación original y comunica los fallos.
- Miniaturas de lista de 64 dp y de gráfica de 36 dp, cuadradas, con geometría
  compartida con Inicio. Formato de cifras común y marcas de selección visibles.
- Márgenes laterales de Datos reducidos de 20 a 8 dp; gráfica con menor padding
  superior y lateral, sin los dos separadores que sumaban espacio antes del plot.
  Eje ajustado a la anchura de las cifras. Navegación inferior conservada.
- Actualización al deslizar con una única línea de carga de 2 dp. Un error de
  actualización conserva el contenido anterior del mismo filtro.
- Inicio transmite el identificador del video a Datos para desplegarlo y
  desplazar la lista hasta él. Si no está entre los resultados, se informa.
- El ViewModel identifica el filtro cargado, cancela peticiones anteriores y
  descarta respuestas obsoletas al cambiar de plataforma.
- Los resultados visitados se reutilizan durante 30 segundos dentro de esta
  vista. Al vencer se actualizan en segundo plano, manteniendo los datos de ese
  filtro. Deslizar para actualizar y reintentar fuerzan una nueva petición.
  La caché en memoria se descarta si cambia la sesión.
- Inicio y Datos reutilizan CreatorVideoMetricChart: mismo eje, barras animadas
  de 200 ms, valores centrados debajo de las miniaturas y selección por video.
  Inicio conserva su resumen y degradado, con márgenes laterales de 8 dp como Datos.
- El interior de ambas tarjetas tiene 12 dp a la izquierda y 8 dp a la derecha.
  Las cifras y sus iconos del resumen de Datos se centran juntos en cada celda.
- La primera carga de una plataforma muestra una estructura de tarjeta, resumen
  y filas pendientes, evitando que el área de contenido se reduzca a un mensaje.

### Alcance

Solo se integraron StatsScreen.kt, StatsViewModel.kt y EsseAnalyticsNavHost.kt.
Se conservaron los cambios locales anteriores en Inicio y las otras features.
Se mantiene la petición existente getGroupStats(limit = 10, platform, force = true),
su autenticación y el contrato HTTP. No se modificaron servidores ni otros clientes.
Los comentarios mostrados son cantidades; no se añadió la lectura de mensajes.

### Validación

Revisión del diff frente a la copia inicial y comprobación de hashes antes de
integrar. Compilación en el checkout compartido completada con código de salida 0:

```powershell
./gradlew.bat :feature:stats:compileDebugKotlin :app:compileDebugKotlin --console=plain --quiet
```

No se ejecutaron tests ni se generaron o instalaron APK. Se incluyeron previews
de desarrollo para claro, oscuro, 320 dp con texto grande y una fila expandida,
con ocho videos ilustrativos y valores cero. Los previews compilaron; no se
renderizaron ni se verificó visualmente en un dispositivo. Esa revisión sigue pendiente.

### Recuperación

Copias anteriores y posteriores a la integración:
`C:/Users/Gessemberg/.codex/recovery/android-datos-luna-20261011`.
El worktree de Luna se conserva con los cambios sin commit.

La revisión posterior del usuario (suma por video, resumen abajo, selección desde
miniaturas, filtros con logos y detalles compactos) se implementó directamente
en el checkout compartido. Sus copias están en
`C:/Users/Gessemberg/.codex/recovery/android-datos-total-20261011`.

La revisión de las once reglas generales está en [DESIGN_REVIEW.md](DESIGN_REVIEW.md).
Incluye las correcciones de miniaturas y detalle, comprobaciones de código y las
verificaciones visuales pendientes. Esta revisión tiene copias recuperables en
`C:/Users/Gessemberg/.codex/recovery/android-design-rules-20261011`.

Los ajustes posteriores de densidad, filtros activos y resumen con iconos están
respaldados en `C:/Users/Gessemberg/.codex/recovery/android-datos-density-20261011`.

Los cambios de caché de filtros, centrado y gráfica compartida están respaldados
en `C:/Users/Gessemberg/.codex/recovery/android-platform-smooth-20261011`.
Se verificó compilación y comportamiento por lectura de las rutas de estado;
la medición de latencia, FPS y frames perdidos en dispositivo sigue pendiente.

## Navegación y carga progresiva — 2026-10-11

- Cambio de pestaña sin fundido; pulsar la pestaña activa no altera el back stack.
- «Ver todos», notificaciones y videos desde Inicio reutilizan las pestañas.
  El video se entrega por SavedStateHandle con un identificador de solicitud;
  permite volver a seleccionar el mismo video sin recrear StatsViewModel.
- Inicio muestra barras/lista cuando llega group-stats. Calendario, historial,
  estadísticas individuales y fallback se completan después; una actualización
  mantiene los datos visibles. Se cancela una carga anterior al refrescar de nuevo
  y se descartan sus resultados si cambia la sesión.
- Fondo salvia y retícula estáticos con brushes cacheados y capa de dibujo propia.
  Se mantienen las animaciones de interacción y el indicador superior de carga.
- Las gráficas restauran el desplazamiento; entrar o volver no reproduce la
  animación de enfoque. Inicio recoge su estado respetando el ciclo de vida.

Contrato HTTP, auth, DTO y central/Laboratorio sin cambios: se modifica la
presentación local y la reutilización de destinos Android. Desktop e iOS siguen
consumiendo el mismo contrato. No se habilitaron simulaciones ni proveedores.
Compilación Kotlin de stats y app correcta; sin APK ni instalación. La validación
visual y la medición de frames en dispositivo siguen pendientes.
Copias antes/después: `C:/Users/Gessemberg/.codex/recovery/android-tab-navigation-20261011`.

Comprobación manual pendiente: alternar Inicio/Datos repetidamente, pulsar el tab
activo, volver desde «Ver todos» y abrir dos veces el mismo video; conservar el
scroll y el filtro al alternar tabs. Con red lenta, la pestaña debe responder y
mostrar carga mientras recibe datos; al refrescar, la lista permanece visible.
