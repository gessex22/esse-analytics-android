# Videos · Logos, espacio y contorno

13 de octubre de 2026 · plan corto de Sol para Luna.

## Cambios decididos

1. **Separación de 16 dp** entre toolbar/error LAN y comienzo de la lista con videos. Aplicarla después del último bloque superior visible, sin añadir espacio entre cada fila. Mantener los vacíos actuales y no dibujar un contorno de lista vacía.
2. **Contorno externo redondeado real**, radio de 18 dp: primera fila solo esquinas superiores; última solo inferiores; única fila las cuatro; filas intermedias sin radios. Clip del contenedor **antes de background**, para que el papel no pinte el rectángulo fuera de las curvas. No tarjeta independiente por fila.
3. **Plataformas solo logos**, de 20 dp dentro de cajas de 24 dp. Publicado: color de marca existente. Pendiente/no publicado: gris neutral legible `onSurfaceVariant` en ambos temas, sin transparencia que lo vuelva invisible. Descartado: mismo logo gris con raya diagonal gris de 2 dp. Sin letras, siglas, etiquetas visibles de estado ni puntos adicionales.

Próximo permanece exactamente como hoy: cálculo del map, texto «Próximo · {sigla}», tamaño/color y contenido sin cambios. Duración/origen/título tampoco son parte de esta simplificación.

## Implementación visual y semántica

- Conservar itemsIndexed y una fila lazy por video, keys y callbacks. Pasar `isFirst/isLast` o Shape derivada del índice a la presentación; no envolver todos los videos en una Column.
- El item puede tener contenedor clip+background con divisor y cuerpo: las divisorias se dibujan **dentro del mismo papel** antes de las filas intermedias, alineadas con el texto. Eliminar el divisor aislado sobre CreatorStudioBackdrop que rompe la continuidad. Sin bordes en cada fila ni espacios internos que separen tarjetas.
- Mantener targets de reproducción de 64 dp, menú de 48 dp y el click independiente del cuerpo. Radios/clip no deben recortar controles ni impedir sus taps.
- Agrupar los tres logos publicables y Facebook real, si existe, en una Row indivisible de cajas de 24 dp con gap de 6 dp, como un bloque del FlowRow de metadatos. Así permanecen juntos a 320 dp; el conjunto puede pasar completo a la línea siguiente junto a duración/origen. No reducir logos para encajarlos.
- Descartado: dibujar slash en la caja del logo después de su contenido. Bajo la raya de 2 dp puede ir un respaldo fino de 4 dp con el color real de superficie para separarla del icono; sin rojo ni blanco fijo. Extremos dentro de la caja y cap redondo, sin invadir el logo vecino. Una diagonal uniforme es suficiente; no nuevo icono/texto de estado.
- Caja informativa no clickable. Semántica completa «YouTube: publicado», «Instagram: pendiente», «TikTok: descartado», etc.; logo decorativo sin descripción duplicada. La raya no es el único dato accesible, y nombres/estados se conservan aunque no haya texto visible.
- Facebook únicamente si el ítem lo declara realmente publicado. Conservar helpers privados Studio para su logo/color y **no cambiar** PlatformBadgeState ni platformColor/platformShortLabel/platformIcon internal usados por detalle.
- Mantener vacío/error, gates animados, selección de fuente, reproducción/editar/eliminar/confirmación, scopes de sheets, header/insets, carga real y VM actuales. Agenda, NavHost y contratos intactos.

## Alcance y recuperación

Git status tiene cambios locales anteriores; preservar sin reset/restauración. Copiar antes los archivos de esta ronda a `C:/Users/Gessemberg/.codex/recovery/android-videos-icons-spacing-20261013/before` y resultado/documentos a after cuando Sol cierre la revisión.

Allowlist:

- `feature/library/src/main/kotlin/com/esseanalytics/android/feature/library/LibraryScreen.kt`: espacio superior, Shape de contorno/divisor y badges privados de lista.
- `feature/library/src/main/kotlin/com/esseanalytics/android/feature/library/LibraryStudioPreview.kt`: fixtures del contenido productivo.
- `design/videos-studio/ICONS_SPACING_IMPLEMENTATION.md`, `ICONS_SPACING_REVIEW.md` y nota/changelog acotados de `../FEATURES.md`.

No modificar otros archivos de código, fuentes/paletas/dependencias ni reimplementar funciones de detalle.

## Verificación y entrega

Previews: tres estados simultáneos, Facebook publicado real, única fila y varias filas, claro/oscuro, vacío, error PC y 320 dp/texto 1.3. Sin Hilt/red; variantes llaman la misma presentación productiva. No certificar render de una imagen/preview que no se observó.

Sol revisa: espacio de 16 dp sin separar filas, clip antes del papel y radios first/last/single, divisorias dentro de la superficie, logos sin texto visible y raya solo descartados, semántica completa, grupo de logos indivisible, Próximo exacto y targets/callbacks conservados.

Luna revisa diff contra before y `git diff --check`; compila `:feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet` con el workaround Java temporal conocido si el host lo necesita, restaurando entorno. No tests nuevos, assemble, APK, instalación, despliegue o push. Registrar compilación real y render del nuevo binario pendiente; entregar para revisión Sol.
