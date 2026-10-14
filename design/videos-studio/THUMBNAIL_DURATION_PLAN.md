# Videos · Duración en miniatura

13 de octubre de 2026 · plan mínimo de Sol para Luna.

- Quitar showOrigin y el cálculo/texto visible Local/Nube/PC de las filas; conservar esos filtros y origen accesible cuando aporte contexto. Quitar duración del FlowRow: se muestra una sola vez, dentro de la miniatura.
- Fuente existente: Local duracionSegundos (Int), Nube durationSeconds y LAN duracion_segundos (Double). Formatter privado de UI: m:ss, o h:mm:ss desde una hora; minutos/segundos con dos dígitos cuando correspondan. Ejemplos 42→0:42, 90.8→1:30, 3661→1:01:01. Truncar fracciones, no redondear.
- Null, negativos, NaN, infinito o valores que excedan conversión segura a segundos enteros no dibujan duración ni guiones. Cero real puede mostrar 0:00. No inferir datos ni cambiar DTO/VM.
- Overlay inferior dentro de thumb64, padding de 3–4 dp y fondo oscuro translúcido con texto blanco/tabular. Una línea sin ellipsis. Mover play decorativo arriba a la derecha para evitar colisión; toda la portada sigue como target independiente de reproducción de 64 dp.
- Tamaño inicial de duración 10 sp; medir texto contra ancho disponible de la miniatura y reducir localmente si es largo para que h:mm:ss quepa también en 320 dp/fontScale1.3. No limitar ancho del target ni mover duración fuera del cuadro. Mantener duración completa en la etiqueta accesible «Reproducir {nombre}, duración {valor}»; imagen/decoración no repiten el anuncio.
- Logos/color/gris/tachado, Próximo (cálculo/texto/tamaño/color), radios externos, separación16, menú48, gates, callbacks, confirmaciones, temas de detalle y Agenda intactos.

## Allowlist y recuperación

- LibraryScreen.kt y LibraryStudioPreview.kt, ambos en feature/library/src/main/kotlin/com/esseanalytics/android/feature/library.
- design/videos-studio/THUMBNAIL_DURATION_IMPLEMENTATION.md y THUMBNAIL_DURATION_REVIEW.md, más nota/changelog acotados de ../FEATURES.md. No NavHost, VM, DTO, repositorios, backend ni otros archivos de código.
- Revisar git status y preservar cambios locales. Snapshot CURRENT en C:/Users/Gessemberg/.codex/recovery/android-videos-thumbnail-duration-20261013/before; resultado/documentos a after tras revisión Sol. Sin reset/restauración.

## Validación

- Previews del mismo contenido productivo: tres orígenes, segundos/minutos/hora, fracciones, null e inválidos, 320 dp/texto1.3 y claro/oscuro. Sin Hilt/red.
- Sol verifica ausencia de origen/segundos externos, formato/fuente por origen, overlay único legible sin colisión play, semántica completa, targets64/48 y Próximo/logos sin cambios.
- Luna revisa diff contra before y git diff --check; compila :feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet con workaround Java temporal conocido si hace falta, restaurando entorno.
- Sin tests nuevos, assemble, APK, instalación, despliegue o push. Registrar compilación real y revisión visual del nuevo binario pendiente. Entregar para revisión Sol sin ampliar alcance.
