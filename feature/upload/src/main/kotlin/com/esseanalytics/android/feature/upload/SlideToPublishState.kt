package com.esseanalytics.android.feature.upload

/** Confirmation state, independent of pointer coordinates and upload progress. */
internal data class SlideToPublishState(
    val fraction: Float = 0f,
    val confirmed: Boolean = false,
    val publishingObserved: Boolean = false,
) {
    fun slideBy(delta: Float, enabled: Boolean): SlideToPublishState =
        if (!enabled || confirmed || !delta.isFinite()) this
        else copy(fraction = (fraction + delta).coerceIn(0f, 1f))

    fun release(enabled: Boolean): SlideToPublishConfirmation =
        if (fraction >= .94f) confirm(enabled) else SlideToPublishConfirmation(cancel(), false)

    fun confirm(enabled: Boolean): SlideToPublishConfirmation =
        if (!enabled || confirmed) SlideToPublishConfirmation(cancel(), false)
        else SlideToPublishConfirmation(copy(fraction = 1f, confirmed = true), true)

    fun cancel(): SlideToPublishState = if (confirmed) this else copy(fraction = 0f)

    fun publishingChanged(publishing: Boolean): SlideToPublishState = when {
        publishing -> copy(fraction = 1f, confirmed = true, publishingObserved = true)
        publishingObserved -> SlideToPublishState()
        else -> this
    }
}

internal data class SlideToPublishConfirmation(val state: SlideToPublishState, val publish: Boolean)
