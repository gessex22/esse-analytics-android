# Home · Studio

La propuesta actual está implementada en Compose. `index.html`, `styles.css` y `prototype.js` conservan el primer prototipo; no representan esta última iteración.

## Dirección visual

- Marca y sello «e» al inicio del contenido desplazable. Campana circular y cápsula de perfil con avatar/nombre flotan superpuestas arriba a la derecha, como el header de `frontend/src/App.tsx` en Electron. El Home pasa por debajo de esos controles; no hay una franja de cabecera que reserve espacio fijo.
- Las acciones flotantes respetan el área segura de Android y conservan sombras, borde y respuesta al pulsar. Usan una superficie con opacidad alta para mantener contraste sobre el contenido. En pantallas pequeñas o con texto grande el perfil muestra solo el avatar, conservando su nombre accesible.
- Un solo indicador de carga en el Home: línea verde agua de 2 dp justo debajo del área de estado, superpuesta al contenido. Durante el arrastre indica cuánto falta para activar la actualización; durante la carga inicial y el refresh se anima sin mostrar un porcentaje de descarga. Entra y sale con fundido. Se retiraron el indicador circular de PullToRefresh y el círculo junto al perfil; la carga inicial mantiene únicamente el texto «Cargando tus videos…».
- Fondo de papel con retícula de puntos y halos de verde bosque y crema. El verde se repite detrás de la zona superior y junto a la navegación; los halos se desplazan lentamente sin recomponer la lista de videos.
- Tarjeta de métricas adaptada al tema: salvia suave en claro y bosque en oscuro. Conserva el contorno asimétrico de 28/16 dp y conecta sus colores con el fondo y la navegación.
- Selector compacto en la esquina superior de la tarjeta: iconos de 16 dp en controles visibles de 34 dp, dentro de una cápsula de 38 dp de alto. Las áreas táctiles siguen siendo de 48 dp y conservan nombres accesibles. No hay una barra ancha con etiquetas debajo del gráfico.
- El gráfico muestra valores, barras y miniaturas reales. Se retiraron los títulos bajo las miniaturas y el bloque de detalle del video seleccionado dentro de la tarjeta. Al tocar una barra se resaltan su miniatura y la fila correspondiente; el título del video aparece solo en la lista.
- Las filas de video tienen una superficie de papel, cambio suave de color al seleccionarse, respuesta al pulsar y animación de posición al actualizarse la lista.
- Se mantienen «Videos recientes» y «Por video · del más antiguo al más reciente». No se recuperan rótulos decorativos ni numeración de secciones.
- Debajo de los videos, «Comentarios» resume las cantidades reales por plataforma y aclara que pertenecen a los videos de esta lista. El contrato actual no devuelve el texto de los mensajes.
- La navegación inferior muestra solo iconos, incluidos «Más» y «+», conservando nombres accesibles. El botón de creación tiene un degradado de verde agua a bosque; la opción activa recibe un halo suave. Se conservan las rutas y las animaciones al pulsar.

## Tipografía incorporada

- **Fraunces**: marca, cifra principal y títulos. Ejes ópticos configurados para 32 pt, suavidad 50 y WONK 1.
- **DM Sans**: interfaz, datos secundarios, títulos de video y controles. Tamaño óptico de 14 pt.
- Pesos 400/500/600/700/800 registrados en Compose. Los archivos originales de fuentes variables están en `core/designsystem/src/main/res/font`, y las licencias OFL en `core/designsystem/src/main/assets/font-licenses`.
- Las fuentes se incluyen en la app; no necesitan conexión ni dependen de las fuentes del fabricante. El proyecto exige Android API 26 como mínimo, compatible con fuentes variables.

Fuentes originales: [Fraunces](https://github.com/google/fonts/tree/main/ofl/fraunces) y [DM Sans](https://github.com/google/fonts/tree/main/ofl/dmsans).

## Colores

| Uso | Claro | Oscuro |
| --- | --- | --- |
| Fondo | `#EAF1ED` | `#0F1B18` |
| Superficie | `#FFFEFA` | `#1A2826` |
| Texto principal | `#17312F` | `#E7F2EF` |
| Texto secundario | `#546D68` | `#B0C2BD` |
| Acento | `#147C71` | `#77D4C2` |
| Tarjeta principal | `#CFE6DA` → `#E4EFE7` | `#24483D` → `#192F28` |

## Datos e interacciones

Dashboard conserva el mismo ViewModel y las mismas consultas. Se agregan las métricas de los videos retornados por `group-stats`, incluido el video de respaldo cuando corresponde. El selector cambia cifra, barras y métricas de las filas. Las miniaturas usan las URLs ya disponibles en la app, con un marcador de video mientras cargan o cuando no existen. El resumen de comentarios agrega `comments` de esos mismos videos por plataforma, con cantidades exactas; no depende de la métrica seleccionada.

El gráfico representa acumulados por video, ordenados del más antiguo al más reciente. No se inventan series diarias, porcentajes de crecimiento ni métricas de backend. Navegación, perfil, notificaciones, estadísticas y recarga por gesto conservan sus conexiones existentes.

## Archivos y revisión

- `CreatorTypography.kt`: fuentes y tipografía local del Home.
- `CreatorHomeTheme.kt`: paletas, tipografía y color de contenido.
- `CreatorStudioBackdrop.kt`: textura y atmósfera animada.
- `CreatorLoadingBar.kt`: indicador único, con progreso del gesto y animación de carga indeterminada.
- `CreatorHome.kt`: componentes visuales e interacciones, incluidos `CreatorHomeBrand` y `CreatorFloatingHomeActions`.
- `DashboardScreen.kt`: datos reales, lista y acciones superpuestas.
- `CreatorStudioPreview.kt`: previews de Android Studio para claro, oscuro, texto grande, ancho de 320 dp, contenido desplazado bajo las acciones y barra de actualización. Los ejemplos usan datos ilustrativos solo dentro de Preview.

Validación: compilación Kotlin de `:feature:stats` y `:app`, revisión del diff y comprobación numérica del contraste del gráfico en ambas paletas. La revisión visual en emulador y celular queda pendiente de instalar esta versión.

## Logos de plataformas en recientes — 2026-10-15

En Videos recientes, las plataformas pendientes se representan únicamente con
su logo gris neutro, ajustado para claro y oscuro. Se elimina el texto visible
«[plataforma] pendiente» y se conserva el nombre y estado en accesibilidad.
Las plataformas publicadas mantienen su color y se reutiliza el mismo badge
Studio de 22 dp con logo de 14 dp. No cambia la selección de plataformas que
la fila ya mostraba, ni se añade Facebook como pendiente por inferencia.
Datos, consultas, métricas y navegación permanecen intactos.

Luna inició el cambio y el agente principal lo terminó al agotarse su límite
de uso. Compilación `:feature:stats:compileDebugKotlin :app:compileDebugKotlin`
correcta y diff revisado sin errores. Revisión visual del resultado en teléfono
pendiente; sin APK ni instalación.
