# Agenda: candidatos atrasados y cruce Instagram/Facebook

Fecha: 2026-10-13 · Android, rama `redesign/home-metrica-tactil`.

## Comportamiento

- `Por publicar` solo aparece al consultar hoy, cuando hay al menos un grupo real cuyo slot tiene `nextVideo` y `nextDate < today`. El filtro se aplica a cada slot antes de agrupar por `fileId`, para no arrastrar plataformas futuras o sin fecha. Los grupos atrasados múltiples se conservan y ordenan por su fecha vencida. No hay superficie ni estado vacío de pendientes cuando no hay candidatos elegibles.
- La selección sigue hoy al avanzar la fecha local por refresh o regreso a primer plano. Elegir hoy vuelve a activar ese seguimiento; una selección explícita dentro de la semana se conserva y una fecha que queda fuera se corrige a hoy.
- El historial presenta una pareja Instagram/Facebook en una fila solo cuando operationId, fileName, deviceId y source coinciden exactamente y no están vacíos; cada evento aporta id y platformId no vacíos, distintos IDs de evento, timestamps parseables del mismo día local seleccionado, y el conjunto cargado del lote contiene exactamente un evento Instagram y uno Facebook. Los duplicados ambiguos y cualquier evidencia incompleta permanecen en filas individuales.
- La pareja conserva ambos eventos. Instagram representa el título y la miniatura, usando exclusivamente las fuentes ya validadas de ese evento en el ViewModel. Las filas visibles notifican a la capa de miniaturas la visibilidad de cada miembro; al desmontarse retiran ambos. El resolver global y el contrato de portada no cambian.
- Cada plataforma muestra su logo junto al nombre. Las filas mantienen una hora local real por evento, incluso cuando las horas de una pareja difieren. Bloques verticales permiten que el metadato use el ancho disponible en 320 dp y con texto ampliado.
- Las previews cubren slot atrasado frente a futuro con `fileId` compartido, varios atrasados, sin candidatos, pareja estricta con horas distintas, pares ambiguos o con identidad vacía, una fecha anterior dentro de la semana y selección de otro día. Las URI locales de miniaturas son intencionalmente inexistentes: demuestran el descriptor y fallback, no la carga de una portada real.

## Alcance y contratos

Cambios limitados a `CalendarScreen.kt`, `CalendarStudioPreview.kt`, el helper puro local `PublishedHistoryGroups.kt`, este documento y la fila/changelog de Calendario en `../FEATURES.md`. Se reutilizan los campos ya cargados de `UploadHistoryItemDto` y los flujos existentes de calendario/descartar. No cambian CalendarViewModel, DTO, endpoints, uploaders, NavHost, backend, Desktop, iOS ni escenarios de Laboratorio. No se añadieron dependencias ni pruebas; no se ejecutaron pruebas Lab.

## Validación

Desde la raíz de `essenalytics-android`:

```powershell
.\gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
```

Resultado tras los ajustes finales de metadatos y previews: exit 0. En este host Windows se usó temporalmente la propiedad de Java que fuerza el fallback TCP de la tubería. El directorio debe seguir inexistente y `JAVA_TOOL_OPTIONS` se restaura en `finally`:

```powershell
$socketFallback = 'C:\Users\Gessemberg\.codex\recovery\android-agenda-studio-20261012\nonexistent-socket-directory'
if (Test-Path -LiteralPath $socketFallback) { throw 'La ruta debe permanecer inexistente.' }
$hadJavaToolOptions = Test-Path Env:JAVA_TOOL_OPTIONS
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$socketFallback"
    .\gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }
} finally {
    if ($hadJavaToolOptions) { $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions }
    else { Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
}
```

La compilación no ejecuta tests ni produce APK. El emulador está disponible; la revisión visual del nuevo binario sigue pendiente porque no se generó ni instaló uno en este bloque. Sin instalación, despliegue ni publicación.