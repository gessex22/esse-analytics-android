# Slide to publish

La acción final del formulario Subir es ahora una barra **slide to publish**
(patrón **slide to confirm / swipe to confirm**). El usuario arrastra el tirador
hasta el extremo para confirmar la publicación. Texto y logos de las plataformas
elegidas comparten la misma barra, sin una fila adicional de destinos. Facebook
se incluye cuando se activa el crosspost de Instagram; sigue viajando como
opción de Instagram y no como un trabajo separado.

El tirador circular de 60 dp tiene una flecha que respeta RTL. Durante el
arrastre sigue el dedo; al soltar antes del 94% vuelve al inicio en 220 ms.
Llegar al extremo no publica hasta soltar. Cancelar el gesto no confirma.
Una confirmación aceptada bloquea nuevos disparos, muestra «Preparando…» y
produce respuesta háptica. El estado real de publicación muestra «Publicando…»
con indicador indeterminado; el desplazamiento del tirador representa la
confirmación, no el progreso de transferencia.

`SlideToPublishState` separa esa lógica de las coordenadas. El control cancela
arrastres parciales al cambiar destinos, validación o tamaño, y se reinicia al
cambiar de video. Conserva el bloqueo si ya confirmó. El resultado terminal del
lote también desbloquea el control, incluso si el trabajo terminó antes de que
Compose pintara un estado activo.

El ViewModel devuelve si aceptó iniciar el pedido; un rechazo local devuelve
el tirador al inicio. No acepta un nuevo pedido mientras ya hay un lote activo.
El cuerpo HTTP, metadata, WorkManager, restricciones de cuenta, journal,
reintentos y confirmación de publicación ambigua conservan sus contratos.
Solo se invoca el callback de publicación existente tras confirmar.

TalkBack dispone de una acción explícita «Confirmar publicación» y lee los
destinos y el estado. Enter/Espacio proporcionan la misma alternativa con
teclado. Ambas usan la validación y el bloqueo del gesto. Un toque ordinario
en la barra no activa la publicación. El alto se adapta a fuentes grandes y
se mantienen las holguras para navegación/teclado del formulario anterior.

## Validación

- `:feature:upload:testDebugUnitTest`: 46/46 correctas, incluidas 7 pruebas nuevas
  de arrastre incompleto, cancelación en el extremo, invalidación antes de soltar,
  confirmación única, publicación activa, alternativa accesible y coordenadas inválidas.
- `:app:compileDebugKotlin`: correcto.
- Diff revisado y `git diff --check` correcto.
- Previews Compose adicionales: cuatro destinos en 320 dp, estado publicando,
  y validación pendiente con escala de fuente 1.5. Compilan; no se han renderizado
  en dispositivo.

No se añadieron endpoints ni escenarios HTTP: es una confirmación local antes
del contrato de publicación ya probado en el Laboratorio. Desktop/iOS y los
formularios independientes PC/LAN y cola remota no se modificaron. No se generó
ni instaló APK. Pendiente: revisión física del gesto, vibración, lector de pantalla,
RTL y comportamiento con el binario actualizado en teléfono.
