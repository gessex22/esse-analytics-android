# Pendiente: descartar "próximo video" desde el Calendario (paridad con iOS)

## Contexto

La central (`content-automation-dashboard/backend`) tiene un endpoint nuevo
para descartar el "próximo" video de una plataforma directo desde mobile, sin
necesitar el rol `todopoderoso` que exige el PATCH administrativo:

```
POST /api/sync/calendar-config/:platform/skip-next
Body: { "fileId": "<ObjectId de Mongo, el mismo que trae nextVideo.fileId>" }
Response: { "ok": true, "nextVideoId": "<ObjectId o null>" }
```

Marca el archivo como descartado (`platforms_discarded`) SOLO para esa
plataforma -- es un descarte permanente, no un reordenamiento. El video sigue
disponible para las otras plataformas.

iOS ya lo implementa (commit `c378c0c` -- "feat: discard next calendar video
from ios", en `Esse-Analytics/Core/Network/SyncAPI.swift` +
`Esse-Analytics/Features/Calendar/CalendarView.swift`). Android todavía no
tiene nada: `CalendarScreen.kt`/`CalendarViewModel.kt` son de solo lectura, y
`SyncApi.kt` solo tiene el PATCH admin viejo (`updateCalendarConfig`), que
además no lo llama nadie (0 call sites) -- no usar ese, usar el POST nuevo.

## Qué falta

1. **`core/network/.../api/SyncApi.kt`**: agregar
   ```kotlin
   @POST("api/sync/calendar-config/{platform}/skip-next")
   suspend fun skipNextCalendarVideo(
       @Path("platform") platform: String,
       @Body body: Map<String, String>, // { "fileId": ... }
   )
   ```
   (`NextVideoDto.fileId` en `SyncDtos.kt` ya existe, no hace falta tocar los DTOs.)

2. **`feature/calendar/.../CalendarViewModel.kt`**: función que llame al
   endpoint de arriba con `config.nextVideo?.fileId`, y recargue el calendario
   después (mismo patrón que `discardNext` en el `CalendarView.swift` de iOS).

3. **`feature/calendar/.../CalendarScreen.kt`**: botón/acción de descarte por
   tarjeta, con confirmación explícita -- el copy tiene que dejar claro que es
   permanente para esa plataforma, mismo criterio que el diálogo de iOS
   ("¿Descartar este próximo video?" / "Se descartará [título] solo para
   [plataforma]"). No mezclarlo con ningún reordenamiento de cola.

## No tocar

- El PATCH admin (`updateCalendarConfig` en `SyncApi.kt`) -- sigue existiendo
  para el escritorio (`todopoderoso` únicamente), no es lo que hay que usar acá.
- No hace falta pantalla de Ajustes ni edición manual del override -- eso
  quedó explícitamente para más adelante también del lado de iOS.
