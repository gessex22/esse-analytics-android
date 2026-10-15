# Selector de video: menos elementos simultáneos

La vista de selección de Subir presenta una única acción principal «Elegir desde
la galería», inmediatamente después de la cabecera. Se retiran el título «En este
teléfono» y las explicaciones repetidas. La carga de galería usa un indicador
dentro del mismo botón, con error visible debajo.

Nube, PC local e Importados pasan a un selector de fuente: solo se muestra una
lista a la vez. Nube y PC respetan los gates existentes; Importados permanece
disponible también vacío. Si solo existe esa fuente, se muestra «Tus videos».
La fuente elegida se conserva al volver del formulario y el listado vuelve al
inicio al cambiar de fuente. Si una fuente deja de estar disponible, se muestra
la primera disponible.

Los tres orígenes comparten filas con miniatura de 64 dp, nombre de hasta dos
líneas y un resumen de estado. Se ocultan únicamente extensiones reconocidas en
la presentación del nombre. «Próximo» tiene prioridad para destinos todavía
pendientes; el resto muestra los pendientes o «Sin destinos pendientes».
Los estados completos de publicado/descartado/pendiente se conservan en la
semántica accesible. Los archivos y las identidades originales no se modifican.

Los listados de las tres fuentes usan items lazy con claves estables. Los taps
se deshabilitan durante importaciones y conservan los callbacks de selección,
la protección por requestId y el flujo independiente de publicación en PC.
Actualización y reintento de estados canónicos aparecen en Importados.

Las animaciones se incorporaron al terminar la composición: cambio de color en
las pestañas (180 ms), entrada/salida/reubicación de filas (100–200 ms) y ajuste
de altura del área de galería (180 ms). Usan las animaciones nativas de Compose
y su escala de movimiento del sistema. El fondo permanece estático.

## Validación

- Compilación Kotlin de upload y app correcta.
- 50/50 pruebas existentes de upload correctas, sin fallos ni omisiones.
- Diff revisado y sin errores de whitespace.
- Previews actualizadas: nube, importados, oscuro, ancho y 320 dp/escala 1.3.
  Compilan; el render y la interacción con el binario actualizado en teléfono
  siguen pendientes.

Cambios visuales exclusivos de Android. Se conservan ViewModels, contratos HTTP,
gates y escenarios de Laboratorio; no se añaden simulaciones. Desktop/iOS no
requieren cambios de contrato. Se preservan los cambios locales previos del
formulario y los estados canónicos. No se generó APK ni se instaló o desplegó.
