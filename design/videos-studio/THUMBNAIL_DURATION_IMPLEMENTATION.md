# Videos · Duración en miniatura

13 de octubre de 2026.

## Resultado

Luna implementó el ajuste del listado: desaparecen las etiquetas visibles de origen Local/Nube/PC y la duración externa. Los filtros de origen se conservan.

La duración aparece una sola vez dentro de la miniatura de 64 dp, con fondo oscuro translúcido y texto blanco. Usa `m:ss` o `h:mm:ss`, truncando las fracciones. Local toma el Int existente; Nube y PC toman sus Double existentes. Null, negativos, valores no finitos o mayores de Int.MAX_VALUE no generan etiqueta. Cero válido muestra `0:00`.

El texto se mide y dibuja con el mismo estilo explícito, ajustando su tamaño al ancho disponible y a la escala de fuente. El play decorativo se ubica arriba; toda la miniatura mantiene su acción de reproducción. La etiqueta accesible conserva nombre y duración completa.

Próximo, logos/color/gris/tachado, contorno redondeado, separación, menú, acciones por fuente y confirmaciones permanecen intactos. Sin cambios de ViewModel, DTO, contratos, NavHost, Agenda, backend, Desktop o iOS.

## Validación y límites

Luna confirmó compilación final con salida 0 después de las correcciones de medición:

```powershell
.\gradlew.bat :feature:library:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
```

Se utilizó el workaround temporal de Java documentado en IMPLEMENTATION.md y se restauró el entorno en finally. Sol aprobó la lectura final en THUMBNAIL_DURATION_REVIEW.md. El coordinador revisó el diff y completó documentación y respaldo después de que Luna alcanzara su límite de uso; no modificó fuentes Kotlin en este cierre.

Las previews declaran tres orígenes, segundos/minutos/hora, fracciones, valores inválidos y 320 dp/texto 1.3. No acreditan un render observado. No se añadieron ni ejecutaron tests ni nuevas pruebas Lab; no se generó ni instaló APK. Revisión visual del nuevo binario pendiente.

Recuperación CURRENT antes/después: `C:/Users/Gessemberg/.codex/recovery/android-videos-thumbnail-duration-20261013`.
