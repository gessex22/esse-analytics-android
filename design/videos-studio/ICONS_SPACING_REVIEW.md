# Videos · Logos y contorno · Revisión final de Sol

13 de octubre de 2026 · revisión focalizada contra ICONS_SPACING_PLAN.md.

## Resultado

Código aprobado por lectura, sin bloqueos materiales del alcance revisado. El coordinador confirmó compilación final de `:feature:library:compileDebugKotlin` y `:app:compileDebugKotlin` con salida 0 después de todos los ajustes, con workaround temporal de Java y entorno restaurado. `git diff --check` correcto; avisos CRLF/LF de archivos anteriores no implican errores del diff.

## Criterios verificados

| Criterio | Evidencia final |
| --- | --- |
| Separación superior | Spacer de 16 dp después de toolbar/error visible y antes de la primera fila; solo cuando hay videos, sin gaps entre filas ni contorno vacío. |
| Virtualización | itemsIndexed y keys por origen conservados; una fila lazy por video, sin Column que componga toda la biblioteca. |
| Contorno externo | isFirst/isLast derivados del índice; radio 18 dp arriba/abajo según corresponda y cuatro esquinas en única fila. clip(rowShape) antes de background(surface). |
| Divisorias y papel | Divisoria dentro del contenedor de superficie de filas intermedias, alineada al texto. Fondo continuo, sin tarjetas separadas ni divisoria aislada sobre backdrop. |
| Solo logos | Iconos de 20 dp en cajas de 24 dp, sin letras/siglas/estado visible en badge. Publicado usa color de marca; pendiente/descartado usan onSurfaceVariant. |
| Descartado | drawWithContent únicamente en estado DISCARDED: respaldo de superficie de 4 dp y diagonal neutral de 2 dp con cap redondo dentro de la caja. Sin rojo ni slash de publicados/pendientes. |
| Accesibilidad | Semántica de nombre completo + estado, logo sin descripción duplicada; badge informativo sin click. |
| Grupo y Facebook | Row indivisible de logos dentro de FlowRow, gaps de 6 dp. Facebook solo si consta publicado; helper privado/color real conservados. |
| Próximo intacto | Mismo nextFor por mapa/nombre y mismo Text «Próximo · {sigla}», tamaño 11 sp, semibold y platformColor que el snapshot anterior. |
| Funciones conservadas | Play en toda portada de 64 dp, menú de 48 dp, callbacks/gates/confirmaciones y helpers internal compartidos sin cambios. NavHost, VM, Agenda y contratos ajenos a esta ronda. |

Se inspeccionaron LibraryScreen y LibraryStudioPreview y se comparó el diff contra `recovery/android-videos-icons-spacing-20261013/before`. Sol editó únicamente este informe; no modificó fuentes ni FEATURES.

## Previews y límite visual

Previews del contenido productivo declaran tres estados simultáneos, Facebook publicado, una fila/varias filas, claro/oscuro, vacío, error PC y 320 dp/texto 1.3. Sin Hilt/red. La estructura y semántica se verificaron por lectura, no mediante render.

No se generó ni instaló APK en este bloque. Hay emulador disponible, pero no se observó el nuevo binario renderizado. Quedan pendientes percepción de las curvas, continuidad del papel, contraste/raya de logos y tacto en dispositivo. El informe queda estable para respaldo after.
