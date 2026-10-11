# Datos · Studio

## Objetivo

Convertir Datos en la continuación del Home: comparar el rendimiento de los videos entre plataformas y abrir el detalle de una publicación concreta. Implementación Android en Compose, encargada a un agente GPT-6 Luna y revisada antes de integrarla en `redesign/home-metrica-tactil`.

## Jerarquía de la pantalla

1. Título «Datos» dentro del contenido desplazable. Perfil y notificaciones flotan arriba a la derecha, como en Home. Una línea de carga verde de 2 dp bajo el área de estado.
2. Filtros compactos: icono de capas para «Comparadas» y logos de YouTube, Instagram y TikTok. El filtro activo se expande con su nombre junto al icono; al deseleccionarlo se contrae hasta dejar solo el icono. «Comparadas» conserva su significado actual: videos vinculados entre las tres plataformas. Los controles admiten desplazamiento horizontal, nombres accesibles y áreas táctiles de 48 dp.
3. Gráfico principal con una barra por video, del más antiguo al más reciente. En Comparadas, sumar las métricas de las plataformas presentes; en un filtro individual, mostrar solo esa plataforma. Sin logos ni nombres de plataformas dentro de la tarjeta. Los títulos permanecen en la lista; tocar una miniatura selecciona y despliega su fila, desplazando la lista hasta ella.
4. Después del gráfico y antes de la lista, resumen compacto con cada cifra y su icono a la derecha, sin etiquetas de texto visibles. Las tres celdas tienen el mismo ancho, conservan nombres accesibles y cambian la métrica mostrada en la gráfica y en la lista.
5. Lista de videos, del más reciente al más antiguo: miniatura real cuadrada de 64 dp como en Inicio, título de la publicación (archivo como respaldo), fecha, cifra de la métrica seleccionada y despliegue del detalle. Encabezados comunes de Vistas, Likes y Comentarios, una fila por plataforma y cifras alineadas. Dar más ancho a las cifras con una disposición alternativa para pantallas estrechas o texto grande. Conservar el área táctil del enlace y destacar la selección con borde, superficie salvia y marca visible.
6. Acción accesible para ampliar el gráfico, conservando la funcionalidad existente.

## Lenguaje visual

- Reutilizar `CreatorHomeTheme`, Fraunces, DM Sans, `CreatorStudioBackdrop`, `CreatorFloatingHomeActions`, `CreatorLoadingBar` y el selector compacto existente. El Home aprobado no se modifica.
- Papel y salvia en claro; verde bosque en oscuro. La tarjeta del gráfico tiene una superficie suave, borde fino y esquinas de la familia Studio. No introducir otro sistema de tipografía ni una tarjeta oscura aislada en modo claro.
- Inicio y Datos usan márgenes de 8 dp para tarjetas y lista. Las tarjetas tienen 12 dp de padding izquierdo, 8 dp derecho y 4 dp vertical, compartidos en CreatorVideoStyle. Eje, alineación de valores, miniaturas y animación de barras se reutilizan en CreatorVideoMetricChart. Soporte de ancho 320 dp y texto a escala 1.3. La navegación inferior conserva su diseño.
- Animaciones breves al seleccionar y desplegar; no vaciar ni volver a fundir toda la lista durante un refresh.
- Un único indicador de carga. Retirar la barra superior compartida solo en la pestaña principal de Datos y usar el header flotante propio. La ruta de detalle desde Más conserva su contenedor y botón Atrás, sin duplicar cabeceras ni áreas seguras.

## Entrada desde Home

- «Ver todos» abre Datos normalmente.
- Tocar un video transmite su `fileId` o su `fileName` como respaldo mediante un argumento codificado, sin incluir URLs de miniaturas ni credenciales.
- Datos localiza, selecciona, despliega y desplaza hasta ese video una vez cargado. Solo se realiza el desplazamiento inicial una vez por entrada, evitando que un refresh mueva al usuario.
- Si el video no está en los diez resultados del filtro, informar de que no aparece en esta selección. No seleccionar otro video silenciosamente ni inventar datos. No se añade un endpoint para resolver este caso.
- Conservar selección correcta de la pestaña, navegación inferior y rutas de detalle con el nuevo argumento opcional.

## Datos y estados

- Fuente existente: `StatsViewModel` y `SyncRepository.getGroupStats(limit = 10, platform, force = true)`. Conservar autenticación, miniaturas, caché, permisos, endpoints y las integraciones Desktop/iOS/Laboratorio.
- Usar `Long` para agregados y cálculos del gráfico; formatear valores compactos en español y exponer cantidades exactas a accesibilidad.
- Los puntos representan métricas actuales por video, no crecimiento diario ni un historial temporal. No ofrecer rangos de fechas que el contrato no soporta.
- Mantener carga inicial, error con reintento, vacío contextual y error de actualización con datos anteriores visibles.
- Al cambiar de plataforma, no presentar datos anteriores como si pertenecieran al nuevo filtro. Si es necesario, asociar el filtro cargado al estado Success y descartar respuestas de peticiones anteriores; no cambiar el contrato HTTP.
- Reutilizar durante 30 segundos los resultados visitados dentro de la vista y sesión actuales. Al vencer la caché, conservar el contenido correcto de ese filtro durante la actualización. La primera carga conserva la estructura visual mediante placeholders. Actualizar manualmente fuerza la consulta; no derivar listas individuales incompletas a partir de Comparadas.
- Las cantidades de comentarios siguen siendo métricas; la lectura de mensajes reales queda para una integración posterior.

## Archivos y límites

- Principal: `feature/stats/.../StatsScreen.kt`.
- Integración mínima en `app/.../EsseAnalyticsNavHost.kt`: callbacks, argumento del video, cabecera y colores de los iconos del sistema para Datos.
- `StatsViewModel.kt` solo puede modificarse para representar el filtro cargado y prevenir respuestas obsoletas entre filtros, si la UI lo requiere.
- Componentes nuevos y previews dentro de la familia Creator/Studio, si hacen falta. No editar Home, fuentes, paletas globales, otras features ni el backend.
- Desarrollo en el worktree separado `codex/datos-studio-luna`. La base incluye copias de los cambios locales existentes; estos no forman parte del trabajo nuevo a integrar.

## Revisión y entrega

1. Revisar el diff contra la copia inicial del worktree y confirmar que solo cambia lo descrito.
2. Compilar `:feature:stats:compileDebugKotlin` y `:app:compileDebugKotlin`. No añadir ni ejecutar tests, generar APK, instalar, publicar o hacer push.
3. Añadir previews con datos ilustrativos exclusivamente de desarrollo: claro, oscuro, 320 dp, texto grande y fila expandida. Separar la composición visual del ViewModel para que el preview no dependa de Hilt.
4. Revisar filtros, suma por plataforma, cero valores, selección desde Home y compatibilidad de rutas mediante lectura del código. Indicar que la revisión visual instalada sigue pendiente.
5. Entregar al agente principal los archivos exactos cambiados, explicación de decisiones, compilación y limitaciones. El agente principal revisa, integra y guarda una copia recuperable antes de cerrar.
