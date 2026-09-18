# EsseAnalytics — Android

App nativa (Kotlin) independiente del escritorio — mismo rol que cumple
`local-backend` en la PC, pero corriendo enteramente en el teléfono. Repo
separado del monorepo principal (`content-automation-dashboard`) a propósito.

## Estado: scaffold de Fase 0

Este repo fue generado como scaffold — compila la ESTRUCTURA (módulos,
convention plugins, DI, navegación entre pantallas placeholder), pero **la
lógica real de cada feature todavía no está** (eso es Fase 1 en adelante). Lo
que sí está terminado y no es placeholder:

- Login real contra la central (`feature:auth`) — `POST /api/auth/login`,
  guardado seguro del JWT, manejo de 401 sin refresh token.
- Capa de datos Room completa (`core:database`) — en particular
  `FileRepository.addPlatform`/`resolveOthersAsDiscarded`, portado con
  cuidado desde `local-backend/src/db/file.repo.ts` del monorepo principal.
- Contrato de API central completo (`core:network`) para lo que necesita el
  MVP — Retrofit + DTOs + interceptor/authenticator.
- Deep link de OAuth (`essenalytics://oauth-callback`) registrado en el
  Manifest — la mitad central de este flujo (`?client=android`) ya está
  pusheada en `content-automation-dashboard` (`backend/`).

## Cómo abrir el proyecto

1. Android Studio (versión reciente, con soporte AGP 9.3.1 / Kotlin 2.2.10).
2. **El wrapper de Gradle está completo y commiteado** (`gradle/wrapper/gradle-wrapper.jar`
   + `.properties`, `gradlew`, `gradlew.bat`), apuntando a Gradle 9.5.0. El
   daemon de Gradle exige JDK 21 de JetBrains Runtime — lo define
   `gradle/gradle-daemon-jvm.properties` (`toolchainVendor=jetbrains`,
   `toolchainVersion=21`). Aunque `settings.gradle.kts` trae el plugin
   `foojay-resolver-convention` para que Gradle pueda auto-provisionar ese
   JDK, en CI (ver `.github/workflows/ci.yml`) igual hace falta instalarlo
   explícito con `actions/setup-java` — una corrida real mostró que el
   auto-provisioning solo no alcanza y falla sin encontrar el JBR 21. Si el
   sync local falla por lo mismo, configurá manualmente un Gradle JDK/JBR 21
   en Android Studio (Settings → Build Tools → Gradle) en vez de asumir que
   se resuelve solo.
3. Sync: `./gradlew --stacktrace` (o abrir la carpeta en Android Studio y
   dejar que el IDE dispare el sync). Build: `./gradlew assembleDebug`. Tests
   unitarios: `./gradlew testDebugUnitTest`. Es MUY probable que el primer
   sync tire algún error de versión — las versiones en
   `gradle/libs.versions.toml` se eligieron por estabilidad/documentación (no
   por ser lo último del todo, ver el comentario ahí arriba), pero no se
   pudieron compilar/verificar desde este entorno (ver `CLAUDE.md` del hub
   sobre por qué Android no se puede compilar desde una sesión de Claude Code
   en Windows). Dejate llevar por el Quick Fix / Upgrade Assistant del IDE si
   pide ajustar algo.

## Decisión pendiente antes de Fase 1: motor de video

`core:media` (`VideoProcessors.kt`) define las interfaces
(`ThumbnailGenerator`, `TrimProcessor`, `NormalizeProcessor`, `MediaProber`)
con los comandos ffmpeg EXACTOS a replicar (documentados en el KDoc de cada
una, portados de `local-backend/src/services/thumbnail.service.ts` y
`video-normalize.service.ts`), pero sin implementación real todavía — **a
propósito**. FFmpegKit (arthenica), la librería que se pensaba usar según el
plan original, está retirada desde abril 2025: repo archivado, binarios
sacados de Maven Central, sin sucesor claro. Antes de escribir la
implementación real hay que decidir entre:

- Un fork mantenido (revisar el estado actual — cambia rápido).
- Compilar los binarios de ffmpeg propios vía NDK.
- Alguna alternativa más nueva (ver el ecosistema de Kotlin Multiplatform,
  que a la fecha de este scaffold estaba armando su propio `ffmpeg-kt`).

No bloquea Fase 0 (nada de la app depende de esto todavía), pero sí bloquea
cualquier trabajo real de ingesta/miniaturas/recorte en Fase 1.

## Estructura

Ver el plan completo (`essenalytics-plan.md`, guardado del lado del monorepo
principal si seguís teniendo acceso a esa sesión) para el detalle de cada
módulo, el contrato de API, y las fases. Resumen rápido:

```
app/                    NavHost, Application, MainActivity
core/model               modelos de dominio (VideoFile, PlatformVideo, User, Platform)
core/common               utilidades chicas (AppResult)
core/database              Room — FileRepository con la lógica de modo Simple/Avanzado
core/network                 Retrofit — contrato completo con la central
core/datastore                 JWT (EncryptedSharedPreferences) + settings (DataStore)
core/media                       interfaces de procesamiento de video (SIN implementación — ver arriba)
core/designsystem                  tema Compose, colores de marca (mismos que el frontend web)
feature/auth                        login — completo
feature/library, ingest, upload,     placeholders — Fase 1
  calendar, sync, stats, users, gems
```

## Convenciones

- Comentarios y nombres en español, mismo criterio que el resto del proyecto
  EsseAnalytics (ver `CLAUDE.md` del monorepo principal).
- Cuando un archivo porta lógica de negocio del monorepo principal (no solo
  boilerplate), el comentario dice explícitamente de qué archivo/línea viene
  — para poder comparar si esa lógica cambia en desktop más adelante.
