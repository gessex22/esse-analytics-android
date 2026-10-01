# EsseAnalytics — Android

Aplicación nativa Kotlin/Jetpack Compose para administrar, publicar y consultar
contenido sin depender del cliente Electron. Comparte contratos con el backend
central y con iOS, pero conserva Room, WorkManager y procesamiento local
propios.

## Estado vigente

**Aplicación funcional; ya no es un scaffold.** Incluye autenticación,
Dashboard, biblioteca local/LAN/nube, ingesta, publicación, calendario,
Matching, estadísticas, usuarios y ajustes. El procesamiento de medios ya tiene
implementaciones Android/Media3; la antigua decisión pendiente sobre FFmpeg no
es una tarea vigente.

El working tree contiene una evolución amplia todavía no cerrada: identidad y
sincronización causal, journal durable de publicación, Room schema 7, pruebas,
ajustes de servidor y correcciones de UI. Debe conservarse y validarse; no se
debe reconstruir desde cero a partir de planes antiguos.

## Abrir y validar

1. Abrir la raíz del repositorio en Android Studio.
2. Usar JBR/JDK 21 para Gradle.
3. Sincronizar el proyecto antes de interpretar errores de KSP.
4. Ejecutar `gradlew.bat testDebugUnitTest` y `gradlew.bat assembleDebug`.
5. Verificar los schemas Room generados, especialmente las versiones 6 y 7.

El entorno de agentes en Windows ha fallado anteriormente antes de compilar por
una restricción de loopback. Ese fallo no equivale a un error del código; la
validación definitiva puede requerir Android Studio fuera de ese entorno.

## Estructura

```text
app/                    navegación y composición de la aplicación
core/model              modelos de dominio
core/database           Room, repositorios, identidad y journals
core/network            Retrofit, OAuth, sincronización y flushers
core/datastore          sesión y preferencias
core/media              metadata, miniaturas, recorte y normalización Media3
core/designsystem       tema y componentes compartidos
feature/auth            autenticación
feature/library         biblioteca y detalle de videos
feature/remotelibrary   nube
feature/ingest          importación
feature/upload          publicación y reanudación
feature/calendar        agenda y próximo video
feature/sync            Matching
feature/stats           estadísticas
feature/settings        servidor, cuentas y preferencias
```

## Fuente de trabajo

Los pendientes de producto viven únicamente en
`content-automation-dashboard/docs/product-backlog.md`. El archivo
`docs/TODO-calendar-skip.md` queda como registro de una función ya implementada,
no como tarea.

## Convenciones

- Código y comentarios en español cuando sea razonable.
- Preservar identidad estable y causalidad; nunca deduplicar solo por nombre.
- Las publicaciones ambiguas no se reinician automáticamente.
- No hacer commit, push, merge, despliegue ni descarte de cambios sin la
  autorización correspondiente.
