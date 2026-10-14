# Videos · Duración en portada · Revisión final de Sol

13 de octubre de 2026 · revisión focalizada contra THUMBNAIL_DURATION_PLAN.md.

## Resultado

Código aprobado por lectura, sin bloqueos materiales del alcance revisado. La medición y el Text usan el mismo estilo explícito; el bucle también mide el último candidato. El coordinador confirmó compilación final de `:feature:library:compileDebugKotlin` y `:app:compileDebugKotlin` con salida 0 después de esas correcciones. `git diff --check` correcto; avisos CRLF/LF de archivos previos no implican errores del diff.

## Criterios verificados

| Criterio | Evidencia final |
| --- | --- |
| Origen y duración externos | showOrigin y cálculo/texto de origen retirados de filas; duración retirada del FlowRow. Filtros Local/Nube/PC conservados. |
| Fuentes y formato | Number? recibe Int Local y Double Nube/LAN. Trunca fracciones mediante toLong, produce m:ss/h:mm:ss y permite cero real 0:00. |
| Valores inválidos | Null, negativos, no finitos y mayores de Int.MAX_VALUE no producen etiqueta; límite coherente con la fuente local y sin saturación. |
| Overlay único | Inferior dentro de thumb64, negro translúcido/blanco, padding y ancho máximo propios. Play decorativo arriba, sin target independiente pequeño. |
| Medición coherente | LibraryDurationOverlayTextStyle compartido en medición y Text: Monospace, Bold, fontSize, lineHeight y letterSpacing0. Adapta tamaño a densidad/fontScale y 48 dp textuales; verifica candidatos hasta el mínimo de la búsqueda. Sin ellipsis. |
| Semántica y tacto | Etiqueta de reproducción incluye nombre y duración completa; overlay limpia anuncios duplicados. Miniatura sigue como target de 64 dp y menú de 48 dp. |
| Preservación | Próximo conserva cálculo/texto/tamaño/color. Logos, gris/slash, shapes first/last/single, separación16, callbacks/gates, confirmaciones, VM, NavHost y Agenda intactos en este diff. |

Se comparó LibraryScreen contra `recovery/android-videos-thumbnail-duration-20261013/before` y se leyó LibraryStudioPreview. Sol editó exclusivamente este informe; no modificó fuentes ni FEATURES.

## Previews y límite real

Previews del contenido productivo declaran tres orígenes, segundos/minutos/hora, fracciones, null/negativos/no finitos/desborde, claro/oscuro y 320 dp/texto1.3, sin Hilt/red. No se observaron esos renders; la medición garantiza el ajuste calculado, no acredita la legibilidad percibida de valores extremos reducidos.

No se generó ni instaló APK. Revisión visual del nuevo binario, lectura del overlay y colisión/recorte efectivos pendientes aunque exista emulador. El informe queda estable para respaldo after.
