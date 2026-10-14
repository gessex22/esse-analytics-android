# Agenda · días futuros y fila Meta única

Fecha: 2026-10-13 · Android, rama `redesign/home-metrica-tactil`.

## Cambios

- `Por publicar` filtra cada slot antes de agrupar: para hoy muestra exclusivamente `nextDate < today`; en fechas pasadas no muestra candidatos; en fechas futuras muestra únicamente slots cuya fecha coincide con el día seleccionado. La semana marca todas las fechas reales de candidatos con `nextVideo` y `nextDate`.
- La selección de hoy sigue la fecha local tras el refresh; fechas elegidas dentro de la semana se conservan. Solo aparece la sección de pendientes si hay grupos elegibles.
- El DTO `UploadHistoryItemDto` ahora declara `contentId: String? = null` como último campo. Central ya entrega ese campo en el historial; el valor nullable mantiene compatibilidad con respuestas anteriores y con Lab, que puede omitirlo.
- El historial agrupa Instagram y Facebook primero por `contentId` idéntico y día local cuando hay exactamente un evento válido de cada plataforma. Si falta esa identidad, aplica fallback únicamente a un lote `operationId` + `fileName` único en todo el historial cargado. IDs de contenido distintos, metadatos contradictorios y ambigüedades no se fusionan. Los eventos restantes siguen como filas individuales.
- La pareja mantiene ambos eventos para visibilidad y disposición, muestra el título y la fuente de miniatura ya validada de Instagram, logos y nombres adyacentes, y la hora común o ambas horas atribuidas en una sola línea adaptable.

## Alcance y límites

Solo se modificaron `CalendarScreen.kt`, `CalendarStudioPreview.kt`, `PublishedHistoryGroups.kt`, el campo final de `UploadHistoryItemDto`, este documento y la fila/changelog de Calendario en `../FEATURES.md`. No cambiaron ViewModel, resolver de miniaturas, NavHost, uploaders, contratos de backend, Desktop, iOS ni escenarios Lab. No se añadieron pruebas ni dependencias.

Central ya expone `contentId` en el historial, pero la publicación Android actual no garantiza que ese campo esté poblado en cada evento. Los fixtures muestran el comportamiento canónico y el fallback sin afirmar que se haya validado una rama canónica nueva en Lab. Los históricos que no tienen `contentId` ni un lote/archivo único permanecen separados; no hay identidad suficiente para unirlos con seguridad.

Las previews cubren atraso hoy y candidato mañana con el mismo archivo, pasado con solo historial, futuro con candidato exacto, slots sin fecha, pareja canónica sin metadatos de lote, fallback por lote, IDs de contenido contradictorios, republicaciones ambiguas, miembro suelto, horas distintas, 320 dp y texto 1.3. Las URI sintéticas de portada apuntan a archivos inexistentes y solo representan el descriptor/fallback; no acreditan una imagen real renderizada.

## Validación

Compilación ejecutada desde la raíz de `essenalytics-android`:

```powershell
.\gradlew.bat :feature:calendar:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain --quiet
```

Resultado: exit 0 tras todos los cambios Kotlin, incluido el color neutral para candidatos futuros y el color de error reservado para slots atrasados. En Windows se aplicó temporalmente el workaround de Java ya documentado; la variable del proceso se restauró en `finally`, y el directorio de socket configurado permaneció inexistente:

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

La revisión de código de Sol aprobó el snapshot final. El emulador está disponible, pero no se generó ni instaló APK en este bloque: el render visual de este binario queda pendiente. No se ejecutaron tests, ni se instaló, desplegó o publicó nada.
