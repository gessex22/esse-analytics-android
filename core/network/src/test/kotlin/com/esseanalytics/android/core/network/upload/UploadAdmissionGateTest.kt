package com.esseanalytics.android.core.network.upload

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.joinAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadAdmissionGateTest {
    @Test
    fun defaultNetworkMustBeValidatedWifiBeforeParallelCapacityOpens() {
        val tracker = DefaultNetworkWifiTracker<String>()
        assertFalse(tracker.defaultNetworkIsWifi.value)

        tracker.onAvailable("wifi")
        assertFalse(tracker.defaultNetworkIsWifi.value)
        tracker.onCapabilitiesChanged("wifi", isValidatedWifi = false)
        assertFalse(tracker.defaultNetworkIsWifi.value)
        tracker.onCapabilitiesChanged("wifi", isValidatedWifi = true)
        assertTrue(tracker.defaultNetworkIsWifi.value)
    }

    @Test
    fun staleCallbacksFromPreviousDefaultNetworkCannotReopenWifiCapacity() {
        val tracker = DefaultNetworkWifiTracker<String>()
        tracker.onAvailable("wifi-old")
        tracker.onCapabilitiesChanged("wifi-old", isValidatedWifi = true)
        assertTrue(tracker.defaultNetworkIsWifi.value)

        tracker.onAvailable("cell")
        assertFalse(tracker.defaultNetworkIsWifi.value)
        tracker.onCapabilitiesChanged("wifi-old", isValidatedWifi = true)
        tracker.onLost("wifi-old")
        assertFalse(tracker.defaultNetworkIsWifi.value)

        tracker.onAvailable("wifi-new")
        assertFalse(tracker.defaultNetworkIsWifi.value)
        tracker.onCapabilitiesChanged("wifi-new", isValidatedWifi = true)
        tracker.onLost("cell")
        assertTrue(tracker.defaultNetworkIsWifi.value)
    }

    @Test
    fun wifiRunsThreeAtOnceAndAdmitsFourthAfterOneCompletes() = runTest {
        val gate = UploadAdmissionGate(MutableStateFlow(true))
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        val jobs = (1..4).map { id ->
            launch {
                gate.withPermit {
                    started.send(id)
                    release.receive()
                }
            }
        }

        runCurrent()
        repeat(3) { started.receive() }
        assertEquals(3, gate.activeCount.value)
        assertFalse(started.tryReceive().isSuccess)

        release.send(Unit)
        runCurrent()
        assertTrue(started.tryReceive().isSuccess)
        assertEquals(3, gate.activeCount.value)

        repeat(3) { release.send(Unit) }
        jobs.joinAll()
        assertEquals(0, gate.activeCount.value)
    }

    @Test
    fun nonWifiRunsOnlyOneAtATime() = runTest {
        val gate = UploadAdmissionGate(MutableStateFlow(false))
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        val jobs = (1..3).map { id ->
            launch {
                gate.withPermit {
                    started.send(id)
                    release.receive()
                }
            }
        }

        runCurrent()
        started.receive()
        assertEquals(1, gate.activeCount.value)
        assertFalse(started.tryReceive().isSuccess)
        release.send(Unit)
        runCurrent()
        started.receive()
        assertEquals(1, gate.activeCount.value)
        release.send(Unit)
        runCurrent()
        started.receive()
        release.send(Unit)
        jobs.joinAll()
        assertEquals(0, gate.activeCount.value)
    }

    @Test
    fun switchingFromWifiToCellularKeepsActiveWorkAndDrainsBeforeAdmittingAnother() = runTest {
        val wifi = MutableStateFlow(true)
        val gate = UploadAdmissionGate(wifi)
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        val jobs = (1..4).map { id ->
            launch {
                gate.withPermit {
                    started.send(id)
                    release.receive()
                }
            }
        }

        runCurrent()
        repeat(3) { started.receive() }
        wifi.value = false
        runCurrent()
        assertEquals(3, gate.activeCount.value)
        assertFalse(started.tryReceive().isSuccess)

        release.send(Unit)
        runCurrent()
        assertEquals(2, gate.activeCount.value)
        assertFalse(started.tryReceive().isSuccess)
        release.send(Unit)
        runCurrent()
        assertEquals(1, gate.activeCount.value)
        assertFalse(started.tryReceive().isSuccess)
        release.send(Unit)
        runCurrent()
        started.receive()
        assertEquals(1, gate.activeCount.value)
        release.send(Unit)
        jobs.joinAll()
    }

    @Test
    fun switchingFromNonWifiToWifiImmediatelyOpensThreeSlots() = runTest {
        val wifi = MutableStateFlow(false)
        val gate = UploadAdmissionGate(wifi)
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        val jobs = (1..4).map { id ->
            launch {
                gate.withPermit {
                    started.send(id)
                    release.receive()
                }
            }
        }

        runCurrent()
        started.receive()
        assertEquals(1, gate.activeCount.value)
        wifi.value = true
        runCurrent()
        repeat(2) { started.receive() }
        assertEquals(3, gate.activeCount.value)
        assertFalse(started.tryReceive().isSuccess)

        repeat(3) { release.send(Unit) }
        runCurrent()
        started.receive()
        release.send(Unit)
        jobs.joinAll()
    }

    @Test
    fun failureAndCancellationReleaseSlotsAndWaitingCancellationDoesNotLeakCapacity() = runTest {
        val gate = UploadAdmissionGate(MutableStateFlow(false))
        val started = Channel<String>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        supervisorScope {
            val failing = async {
                gate.withPermit {
                    started.send("failing")
                    error("expected failure")
                }
            }
            runCurrent()
            started.receive()
            assertTrue(runCatching { failing.await() }.isFailure)
        }
        assertEquals(0, gate.activeCount.value)

        val active = launch {
            gate.withPermit {
                started.send("active")
                release.receive()
            }
        }
        runCurrent()
        started.receive()
        val cancelledWhileWaiting = launch {
            gate.withPermit { started.send("must-not-start") }
        }
        runCurrent()
        cancelledWhileWaiting.cancelAndJoin()
        assertEquals(1, gate.activeCount.value)

        val cancelledWhileActive = launch {
            gate.withPermit {
                started.send("cancelled-active")
                awaitCancellation()
            }
        }
        release.send(Unit)
        runCurrent()
        started.receive()
        cancelledWhileActive.cancelAndJoin()
        active.join()
        assertEquals(0, gate.activeCount.value)

        val next = launch { gate.withPermit { started.send("next"); release.receive() } }
        runCurrent()
        started.receive()
        release.send(Unit)
        next.join()
        assertEquals(0, gate.activeCount.value)
    }

}
