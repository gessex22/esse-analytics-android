package com.esseanalytics.android.feature.upload

import org.junit.Assert.*
import org.junit.Test

class SlideToPublishStateTest {
    @Test fun incompleteDragReturnsToStartWithoutPublishing() {
        val result = SlideToPublishState().slideBy(.8f, true).release(true)
        assertFalse(result.publish)
        assertEquals(0f, result.state.fraction, 0f)
        assertFalse(result.state.confirmed)
    }

    @Test fun finishingTheDragConfirmsOnceAndLocksRepeatedRelease() {
        val result = SlideToPublishState().slideBy(.95f, true).release(true)
        assertTrue(result.publish)
        assertEquals(1f, result.state.fraction, 0f)
        assertTrue(result.state.confirmed)
        assertFalse(result.state.release(true).publish)
        assertFalse(result.state.confirm(true).publish)
        assertEquals(result.state, result.state.slideBy(-1f, true))
    }

    @Test fun cancellationEvenAtTheEndDoesNotPublish() {
        val cancelled = SlideToPublishState().slideBy(1f, true).cancel()
        assertFalse(cancelled.release(true).publish)
        assertEquals(0f, cancelled.fraction, 0f)
    }

    @Test fun validationBecomingInvalidBeforeReleaseCancelsConfirmation() {
        val result = SlideToPublishState().slideBy(1f, true).release(false)
        assertFalse(result.publish)
        assertFalse(result.state.confirmed)
        assertEquals(0f, result.state.fraction, 0f)
        assertEquals(SlideToPublishState(), SlideToPublishState().slideBy(1f, false))
    }

    @Test fun inProgressPublicationStaysLockedUntilItFinishes() {
        val active = SlideToPublishState().publishingChanged(true)
        assertFalse(active.release(true).publish)
        assertFalse(active.confirm(true).publish)
        val ready = active.publishingChanged(false)
        assertEquals(SlideToPublishState(), ready)
        assertTrue(ready.slideBy(1f, true).release(true).publish)
    }

    @Test fun accessibilityConfirmationUsesTheSameValidationAndSingleUseLock() {
        val initial = SlideToPublishState()
        assertFalse(initial.confirm(false).publish)
        val accepted = initial.confirm(true)
        assertTrue(accepted.publish)
        assertFalse(accepted.state.confirm(true).publish)
        assertEquals(accepted.state, accepted.state.cancel())
    }

    @Test fun dragClampsBothDirectionsAndIgnoresInvalidCoordinates() {
        val initial = SlideToPublishState()
        assertEquals(initial, initial.slideBy(Float.NaN, true))
        assertEquals(initial, initial.slideBy(Float.POSITIVE_INFINITY, true))
        assertEquals(1f, initial.slideBy(4f, true).fraction, 0f)
        assertEquals(0f, initial.slideBy(-4f, true).fraction, 0f)
    }
}
