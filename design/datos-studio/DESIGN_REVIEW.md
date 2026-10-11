# Revisión de las reglas generales de diseño

11 de octubre de 2026 · rama `redesign/home-metrica-tactil`.

## Alcance y resultado

Revisión del código de Inicio y Datos con las once reglas proporcionadas por el
usuario. Las demás pantallas requieren su propia revisión. La comprobación visual
instalada en claro, oscuro, 320 dp y texto grande sigue pendiente.

Se corrigieron diferencias de geometría y formato de cifras, la disposición del
detalle de un video, señales de selección y la falta de respuesta al fallar un enlace.

| Regla | Evidencia y ajuste en Inicio / Datos | Estado de la revisión |
| --- | --- | --- |
| 1. Jerarquía visual | Gráfica antes del resumen y la lista. Título, posición y peso separan el contenido principal; cifras del detalle a 14 sp frente a encabezados de 12 sp. | Revisado en código; confirmar percepción en dispositivo. |
| 2. Una tarea por pantalla | Inicio resume la actividad; Datos permite examinar el rendimiento por video. Los datos de cada plataforma aparecen al desplegar el video. | Conservado. |
| 3. Consistencia | Miniaturas cuadradas de 64 dp en las listas y 36 dp en las gráficas. Renderer de gráfica y márgenes compartidos, Fraunces / DM Sans y un mismo formato español con k / M. | Corregido en estas dos vistas. |
| 4. Retícula y alineación | Inicio y Datos usan márgenes de 8 dp. El interior de tarjetas da 12 dp a la izquierda y 8 dp a la derecha. Encabezados y cifras del detalle usan los mismos anchos de columna; cifra e icono del resumen se centran juntos. | Corregido; pendiente render de textos largos. |
| 5. Proximidad | Logo, cifras y enlace pertenecen a la misma fila. Los encabezados de métricas aparecen una vez; se retiró la divisoria interior del detalle. | Corregido. |
| 6. Espacio en blanco | Padding y separadores de la gráfica reducidos; filas de tabla visibles de 40 dp, con centros separados 48 dp. La presentación se adapta al ancho y tamaño del texto. | Revisado en código; confirmar densidad visual. |
| 7. Un solo acento | El verde Studio identifica acciones y estados activos. Los colores de los logos representan identidades de plataformas. | Conservado en estas vistas; revisar las demás pantallas. |
| 8. Retroalimentación | Presionado, expansión animada, línea de carga, reintento y error con datos previos. Abrir un enlace ahora comunica el fallo en la fila. | Corregido el fallo silencioso; verificar interacción en dispositivo. |
| 9. Navegación predecible | Datos mantiene la pestaña seleccionada al recibir un video. El detalle desde Más conserva Atrás; seleccionar una miniatura lleva hasta su fila. | Revisado por lectura de rutas y callbacks. |
| 10. Prevención de errores | Estas vistas muestran datos y abren publicaciones; no añaden acciones destructivas. El cambio de filtro conserva la protección contra respuestas obsoletas. | Sin nueva acción destructiva que revisar aquí. |
| 11. Legibilidad primero | Detalle con etiquetas comunes y cifras en una línea, dígitos tabulares, disposición alternativa para texto grande y marcas de selección además del color. | Corregido en Datos; confirmar truncado y lectura visual. |

## Miniaturas y métricas

`CreatorVideoStyle` contiene las dimensiones y radios usados por las dos vistas.
La miniatura de una lista y la de una gráfica tienen tamaños distintos por función,
pero coinciden entre Inicio y Datos. El formato de cifras también se comparte.

El detalle normal es una tabla compacta: encabezados Vistas, Likes y Comentarios;
una fila por plataforma; enlace al extremo derecho. Los números comparten alineación
y dígitos tabulares. Comentarios dispone de más ancho y las etiquetas se mantienen
en una línea. En paneles menores de 280 dp o con escala de texto superior
a 1.2 se utiliza una cabecera de plataforma y un bloque de tres cifras etiquetadas,
para disponer de más ancho sin comprimir la letra. Por encima de escala 1.6 se
presenta cada métrica en una fila de ancho completo.

La revisión posterior compacta el resumen general: tres celdas iguales con cifra
e icono a la derecha. El filtro activo muestra nombre e icono mediante animación;
los inactivos conservan solo su icono. Comparadas usa capas como símbolo de agrupación.

## Cambio de plataforma y fluidez

La versión anterior forzaba una consulta en cada selección y retiraba la gráfica
y la lista durante la carga. StatsViewModel ahora conserva hasta cuatro resultados
en memoria, por filtro y sesión: reutilización inmediata durante 30 segundos;
actualización con contenido previo cuando vence; consulta forzada al actualizar
manualmente. Las respuestas canceladas o de una sesión distinta no se publican.

El primer acceso a un filtro conserva la estructura de la pantalla con placeholders.
Las barras comparten identidad por video y animan sus alturas en 200 ms; el estado
animado se lee al dibujar el Canvas, limitando el trabajo de cada frame a las barras.
No se ha medido la fluidez en dispositivo: quedan pendientes latencia y frames perdidos.

## Contraste calculado a partir de la paleta

Relaciones de luminancia relativas de colores opacos declarados en CreatorHomeTheme:

| Par | Relación |
| --- | --- |
| Claro: texto principal / superficie | 13.72:1 |
| Claro: texto secundario / superficie | 5.53:1 |
| Claro: texto secundario / contenedor seleccionado | 4.78:1 |
| Oscuro: texto principal / superficie | 13.34:1 |
| Oscuro: texto secundario / superficie | 8.21:1 |
| Oscuro: texto secundario / contenedor seleccionado | 5.19:1 |

Estos cálculos comprueban los pares de la paleta. Las transparencias, imágenes,
halos, estados de presionado y superficies finales requieren revisión renderizada.

## Validación y continuidad

- Compilación de `:feature:stats:compileDebugKotlin` y `:app:compileDebugKotlin`
  completada con código de salida 0.
- Diff revisado frente a las copias anteriores, preservando los cambios locales.
- Previews de desarrollo de claro, oscuro, ancho reducido y detalle expandido.
- Pendiente: renderizar y revisar las dos vistas en dispositivo, especialmente
  texto grande, títulos largos, cifras grandes y el gráfico ampliado en horizontal.
- Pendiente: aplicar esta misma revisión a Agenda, Videos, Subir y Más antes de
  afirmar que toda la app cumple las reglas de consistencia y navegación.

Copias recuperables en
`C:/Users/Gessemberg/.codex/recovery/android-design-rules-20261011`.
