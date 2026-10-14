# Refinamiento del formulario de publicación

Implementación y revisión realizadas directamente en este chat, sin delegación.

El formulario mantiene CreatorHomeTheme, sus colores claro/oscuro y la cabecera
Subir. La composición usa una zona central desplazable y un pie de publicación
fijo que reserva 96 dp para la navegación flotante. Al abrir el teclado se oculta
el pie y se amplía el margen inferior del contenido para poder editar sin sumar
otro bloque sobre el teclado.

- Video seleccionado: fila compacta con miniatura, nombre, duración y «Cambiar».
  Solo se ocultan extensiones reconocidas de video; los nombres con otros puntos
  se conservan. La ruta, el registro y el título editable no cambian.
- Contenido: título y descripción agrupados con campos redondeados y bordes suaves.
- Portada: fila plegable con el tiempo del fotograma y «Editar». Abrirla conserva
  el slider y la captura diferida; plegada no añade otra previsualización grande.
- Plataformas: tarjetas individuales, nombre visible, logo con su color cuando
  está seleccionada, checkbox y un resumen concreto («Público · No es para niños»,
  «Reel y feed», privacidad TikTok). Las seleccionadas tienen borde verde discreto.
- Ajustes: panel inferior nativo con cabecera de la plataforma, cierre accesible
  y «Listo». Los selectores muestran etiqueta y valor en una sola fila; comentarios,
  interacciones y contenido comercial usan switches. Los cambios se aplican a los
  mismos bindings existentes y se conservan al cerrar el panel. Se cierra si el
  formulario pasa a estar deshabilitado por una publicación en curso.
- Publicar: barra «Desliza para publicar» con los logos de los destinos integrados,
  incluido Facebook cuando se activa desde Instagram. Los mensajes de validación
  permanecen visibles junto a la acción. Ver SLIDE-TO-PUBLISH-2026-10-14.md.
- Resultado: aviso de menor tamaño, encabezado neutral para una cancelación,
  nombre de cada plataforma, reintento como acción secundaria y X para cerrar.
  Los errores conservan su color y sus detalles. No se modifica el journal ni el
  comportamiento del cierre, cancelación, confirmación ambigua o reintento.

## Alcance y contratos

Cambios de composición y presentación del formulario Subir. Las funciones de
selección, configuración, permisos de TikTok, metadata, WorkManager y publicación
son las mismas. Se conserva el crosspost real de Facebook. No se agregaron rutas,
campos HTTP, proveedores ni escenarios simulados. Los formularios PC/LAN y la cola
remota mantienen su composición independiente.

## Validación

- `:feature:upload:compileDebugKotlin :app:compileDebugKotlin`: correcto.
- `:feature:upload:testDebugUnitTest`: 39/39 correctas.
- Diff revisado y `git diff --check` correcto.
- `UploadRefinedPreview.kt` contiene fixtures Compose de desarrollo: claro,
  oscuro, cancelación, 320×480, 800×400 y contenido del panel TikTok con una
  restricción de dúos. Compilan; no sustituyen una revisión visual en dispositivo.

Se usó el workaround de Java ya documentado: variable temporal de proceso con
`jdk.net.unixdomain.tmpdir` apuntando a una ruta comprobada inexistente. No se
alteró la configuración permanente. No se generó APK, instalador ni despliegue.

Pendiente: render nativo y revisión en teléfono con el binario actualizado,
incluyendo teclado, navegación flotante, apertura/cierre del panel y estado de
publicación. Desktop/iOS y el contrato del Laboratorio permanecen iguales.
