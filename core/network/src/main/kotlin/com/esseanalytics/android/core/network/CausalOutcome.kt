package com.esseanalytics.android.core.network

// Resultado de intentar enviar una fila del outbox causal. Lo produce
// classifyCausalResponse a partir del status HTTP (o la ausencia de respuesta,
// = corte de red) -- la semántica la lleva el código, no el cuerpo. El flusher
// (CausalPlatformOutbox) actúa: Applied/Stale/Terminal sacan la fila y avanzan
// la cadena; InProgress/Retry la dejan y frenan la cadena para no enviar
// descendientes con una base que quedaría vieja.
sealed interface CausalOutcome {
    // 200: aplicado. `version` = nueva revisión autoritativa (avanza el store).
    data class Applied(val version: Long?) : CausalOutcome

    // 202: la central lo tomó pero todavía no lo resolvió -- queda pendiente,
    // se reintenta más tarde (no se pisa la fila).
    data object InProgress : CausalOutcome

    // 409: baseVersion vieja. La central trae su revisión actual (`version`);
    // se corrige el store y se termina el conflicto (la fila sale, no se
    // reintenta con la base vieja). El próximo cambio de la cadena se rebasa
    // contra la revisión corregida.
    data class Stale(val version: Long?) : CausalOutcome

    // 422 (operation_mismatch / mismatch) u otro 4xx permanente: rechazo
    // terminal, la fila sale sin reintentar (reintentarla sería ruido eterno).
    data object Terminal : CausalOutcome

    // 401 (token vencido -> se resuelve con re-login) / 429 (rate limit) / 5xx /
    // red: reintentable, la fila queda pendiente con attempts+1.
    data object Retry : CausalOutcome
}

// Clasificación pura del par (status, version). `code == null` = no hubo
// respuesta HTTP (excepción de red/timeout). Mismo criterio que
// HistoryOutbox/PlatformUpdateOutbox para los 4xx permanentes (todo 4xx que no
// sea 401/429 es rechazo), extendido con la semántica causal de 409/422.
fun classifyCausalResponse(code: Int?, version: Long?): CausalOutcome = when {
    code == null -> CausalOutcome.Retry
    code == 200 -> CausalOutcome.Applied(version)
    code == 202 -> CausalOutcome.InProgress
    code == 409 -> CausalOutcome.Stale(version)
    code == 422 -> CausalOutcome.Terminal
    code == 401 || code == 429 -> CausalOutcome.Retry
    code in 500..599 -> CausalOutcome.Retry
    code in 400..499 -> CausalOutcome.Terminal
    else -> CausalOutcome.Retry
}
