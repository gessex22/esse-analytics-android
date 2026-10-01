# Completado: descartar "próximo video" desde el Calendario

**Estado corregido el 2026-09-28.** La función fue implementada en el commit
`bcc13db` (`feat(calendar): rediseno de Calendario, paridad visual con iOS`).
Este archivo queda como referencia histórica del contrato y no es un TODO.

## Contrato

```http
POST /api/sync/calendar-config/:platform/skip-next
Body: { "fileId": "<ObjectId de Mongo>" }
Response: { "ok": true, "nextVideoId": "<ObjectId o null>" }
```

El descarte es permanente únicamente para la plataforma seleccionada. El video
sigue disponible para las demás plataformas.

## Implementación existente

- `core/network/.../api/SyncApi.kt` expone `skipNextCalendarVideo`.
- `feature/calendar/.../CalendarViewModel.kt` implementa `discardNext` y
  recarga el calendario.
- `feature/calendar/.../CalendarScreen.kt` presenta la acción y su confirmación
  explícita.

El PATCH administrativo `updateCalendarConfig` sigue siendo un contrato
separado y no sustituye esta acción móvil.
