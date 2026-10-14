# Subir Android · Plataformas compactas

14 de octubre de 2026

Se agruparon los selectores Local/Nube en una tarjeta compacta con controles accesibles de al menos 48 dp. Los logos solo usan color de marca ante publicación confirmada; la selección sigue expresada por el checkbox y los descartes conservan su diagonal y semántica. `FileList` muestra logos grises para la misma lista de plataformas pendientes.

Facebook y su resultado comparten una fila separada del target de Instagram. El feedback sale del mismo `WorkInfo` de Instagram y solo se muestra si su salida contiene URL o error de Facebook; no se infiere éxito desde Instagram. PC usa únicamente el estado de Facebook del DTO; sus filas conservan el resultado de publicación PC, TikTok, errores y compuertas existentes. No cambiaron selección, payloads, callbacks, WorkManager, ViewModels, contratos HTTP ni escenarios Lab.

Sol aprobó estáticamente las cuatro fuentes permitidas y la compilación terminó en exit 0 para `:feature:upload:compileDebugKotlin`, `:feature:library:compileDebugKotlin` y `:app:compileDebugKotlin`. No se ejecutaron pruebas nuevas ni se generó/instaló APK. Previews declaradas: Subir compacto 320 dp con escala 1.3, publicación/progreso/error/lote, Facebook deshabilitado y PC claro/oscuro. La cobertura no incluye una fixture explícita de diagonal descartada ni PC a 320 dp. Render e interacción en dispositivo quedan pendientes.
