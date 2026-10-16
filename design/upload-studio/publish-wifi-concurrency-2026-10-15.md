# Concurrencia de subidas Android según la red · 2026-10-15

`UploadWorker` mantiene una ejecución independiente por plataforma y pasa por
un gate de proceso antes de leer o copiar el archivo o iniciar el uploader.
Hasta tres workers admitidos en la red predeterminada cuando Android informa
Wi‑Fi con Internet validado; con celular, Ethernet, conectividad no validada o
estado desconocido se admite uno a la vez. El monitor usa
`registerDefaultNetworkCallback`: arranca de forma conservadora en secuencial,
solo acepta capabilities de la identidad de la red predeterminada actual y
descarta `onLost`/capabilities tardías de redes anteriores. No clasifica como
Wi‑Fi una red Wi‑Fi secundaria mientras la ruta predeterminada sea celular.
El tracker y el gate viven en `core:network` para que Subir desde el teléfono y
el envío a PC/LAN compartan el mismo límite, sin dependencia entre features.
Los archivos locales, importados y de Biblioteca remota usan el uploader del
teléfono y su conexión activa. En un envío PC/LAN, el gate solo regula cuántos
comandos Android admite en paralelo; los bytes los publica la PC por su propia
conexión de red. La ruta PC/LAN conserva los POST existentes por plataforma y
sus bodies; no introduce reintentos automáticos de comandos.

Los workers que esperan siguen siendo `PENDING` para la UI y `cancelBatch`,
aunque WorkManager ya los reporte como `RUNNING`; no reportan progreso de
subida ni resuelven/copian el archivo antes de obtener cupo. Cancelar mientras
esperan no entra al cuerpo de publicación. El gate no cancela ni reinicia
workers admitidos por el cambio de red; en transición a celular no admite otro
hasta que no quede ninguno activo. Volver a Wi‑Fi permite ocupar los tres
cupos al liberarse. WorkManager y sus constraints existentes aún pueden
detener o reintentar el trabajo.
Cancelación, excepción o retorno normal libera el cupo en `finally` no
cancelable. Un fallo de plataforma no cancela a sus workers hermanos. El
crosspost de Facebook permanece dentro del worker Instagram y ocupa ese mismo
cupo. La preferencia Solo Wi‑Fi conserva su constraint `UNMETERED`; esta
política no la relaja ni cambia el tipo de red requerido al encolar.

Sin cambios de HTTP: los reportes a `POST /api/upload-jobs` mantienen
`queued`/`uploading`/`processing`/`succeeded`/`failed`, `clientJobId`,
`operationId`, plataforma y origen. El éxito de cada plataforma conserva
`HistoryOutbox` y `record-publish`; YouTube mantiene su journal durable,
reintentos y tratamiento de resultados ambiguos. `PendingBatchStore`, la
selección Simple/Avanzado, nombres únicos y WorkManager backoff permanecen.
Central, Lab, Desktop e iOS no reciben una política local de red.

El gate envuelve tanto los uploaders reales como los mocks. La comprobación de
`LabModeStatus` se hace dentro del cupo, justo antes de entrar al uploader, y
mantiene su comprobación real de health.

## Validación

- `:core:network:testDebugUnitTest`: pasa; las 7 pruebas nuevas cubren
  capacidad 3/1, transiciones, estado inicial desconocido, callbacks obsoletos,
  excepciones y cancelación/liberación del cupo.
- `:feature:upload:testDebugUnitTest`: pasa con 51 pruebas, incluida la
  verificación de que un worker RUNNING que espera cupo permanece PENDING y
  cancelable.
- `:feature:upload:compileDebugKotlin`, `:feature:library:compileDebugKotlin`
  y `:app:compileDebugKotlin`: pasan. También pasan `:core:network:lintDebug`,
  `:feature:upload:lintDebug` y `:app:lintDebug`.
- `:feature:library:lintDebug` sigue fallando en `VideoDetailPlayback.kt`
  (cuatro errores de opt-in `UnsafeOptInUsageError`) y tiene una advertencia
  `UseKtx` en `VideoPlayerDialog.kt`; ambos archivos son ajenos a este cambio.
  No se corrigieron hallazgos fuera de alcance.
- Laboratorio: test HTTP aislado `publish_multi_platform` 1/1; verifica health
  de entorno Lab antes de mocks, éxito y fallos independientes, recuperación de
  Instagram, expiración/rechazo de reintento TikTok y un registro de historial
  por éxito. No llama proveedores reales. Esta prueba valida contratos y
  reportes, no la coordinación de red Android. El typecheck dirigido sobre
  `scenarios.ts` y el test nuevo también pasa.
- El typecheck completo del backend Lab conserva el error previo
  `metrics.test.ts:39` (`readonly platforms` frente a `Platform[]`); los nuevos
  archivos no agregan errores de tipos.

Fuentes Android para la detección de ruta default y el orden de capabilities:
[NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)
y [lectura del estado de red](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).

## Revisión final y límites de verificación

El agente principal revisó el gate de Luna, los callbacks de red, el mapeo de
WorkManager y la integración PC/LAN. La admisión ocurre antes de iniciar cada
comando o uploader; los resultados por plataforma se actualizan por separado
y la cancelación se propaga sin convertirla en un fallo de proveedor. Los
POST de PC conservan sus cuerpos originales y no tienen reintento automático
nuevo. No se detectaron bloqueos concretos adicionales en el diff revisado.

La comprobación de tipos dirigida a `scenarios.ts`, el nuevo test HTTP Lab y
sus dependencias pasa. El test aislado emite avisos previos de configuración
IPv6 de express-rate-limit; no afectan su resultado 1/1 y no se modificó ese
middleware. El escenario es `publish_multi_platform` y el test es
`content-automation-dashboard/lab-backend/src/controllers/publish-multi-platform.test.ts`.

Pendiente una prueba en teléfono con cambios reales Wi-Fi/celular y una
publicación delegada a la PC. Las pruebas JVM cubren la coordinación, pero no
acreditan una transferencia real ni las restricciones del fabricante en segundo
plano. No se generó/instaló APK ni se publicaron videos en proveedores reales.
