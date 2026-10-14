# Videos · Detalle Studio · Revisión final de Sol

13 de octubre de 2026 · revisión estática contra `DETAIL_STUDIO_PLAN.md` y el snapshot final del checkout.

## Resultado

Sin bloqueos restantes en la allowlist. Sol revisó los ajustes de fullscreen, el ciclo de vida del player, la persistencia del scroll, el callback nullable de Nube y la reutilización del badge; confirmó que el cuerpo original del badge permanece idéntico y aprobó estáticamente el cambio final.

## Criterios verificados

| Criterio | Evidencia del código |
| --- | --- |
| Entrada al detalle | La miniatura ya no tiene acción ni overlay de play; cuerpo y miniatura comparten el click de detalle y su etiqueta accesible incluye nombre/duración disponible. El menú conserva `Reproducir` y su reproductor separado. |
| Ventana y tema | Un `Dialog` fullscreen con `decorFitsSystemWindows = false`, `CreatorHomeTheme`, backdrop, cabecera de cierre e insets safe drawing. `LinkEditorDialog` también usa Creator. |
| Slot del player | Un `AndroidView` compone en un único callsite estable en ambos modos. El botón de pantalla completa va en fila separada de 48 dp antes del host, no tapa los controles y mantiene contraste en detalle/fullscreen. |
| Estado al expandir | El mismo ExoPlayer, MediaItem, posición, intención play/pausa y `LazyListState` sobreviven al cambio de modo. El contenido inferior se oculta en fullscreen. Atrás retorna primero al detalle y después cierra. |
| URI, carga y lifecycle | URI Local usa `Uri.parse` para rutas `file` y `content`; Nube/PC usan `streamUrl` nullable. Ratio derivado de `VideoSize` con fallback 16:9, resize FIT y mensajes humanos para URI ausente/error. Listener y release viven con el player; el observer puede cambiar de owner sin liberar la sesión y pausa/reanuda respetando `playWhenReady`. |
| Cierre y URI cambiada | `DisposableEffect(player)` retira listener y libera una vez; el `AndroidView` desacopla su `PlayerView` en `onRelease`. Expandir/colapsar no libera ni prepara otra sesión. |
| Estados de plataforma | Nombre/edición y estado se acomodan en renglones separados con targets ≥48 dp. Usa `LibraryStudioPlatformBadge` sin alterar su dibujo: color publicado, gris pendiente, tachado descartado. Edición de link y toggles siguen conectados a callbacks existentes. |
| Acciones | Local mantiene Publicar/Galería; Nube muestra Publicar solo con callback real y conserva App/Galería; PC conserva Publicar con el control compartido. Estados y exclusión de guardados existentes permanecen. `RemoteLibraryScreen` conserva su callback de publicación. |
| Previews | Layout puro con fixtures Local/Nube/PC, claro/oscuro, 320 dp/fontScale 1.3, paisaje/vertical, inline/fullscreen, URI ausente/error, título largo, publicado sin enlace y estados Running/Saved/Failed. Sin Hilt, red ni ExoPlayer. |
| Alcance | No se modifican ViewModels, contratos, DTO, repositorios, NavHost, Agenda, dependencias, `feature:remotelibrary`, Desktop, iOS o backend. No se inventaron escenarios ni resultados de Laboratorio. |

## Verificación y límites

Compilación final de `:feature:library:compileDebugKotlin :app:compileDebugKotlin` exit 0 con el fallback temporal documentado en `DETAIL_STUDIO_IMPLEMENTATION.md`; `JAVA_TOOL_OPTIONS` fue restaurado y la ruta de fallback permaneció inexistente. `git diff --check` limpio.

La revisión es estática. No se generó ni instaló APK y no se observó render de este binario. El guardado Local de `content://` conserva su limitación anterior de `File(filePath)`, y la sesión no persiste ante recreación de Activity.
