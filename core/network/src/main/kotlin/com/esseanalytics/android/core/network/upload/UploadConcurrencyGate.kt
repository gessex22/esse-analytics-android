package com.esseanalytics.android.core.network.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks whether the device's current default network is a validated Wi-Fi network. */
@Singleton
class UploadNetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val tracker = DefaultNetworkWifiTracker<Network>()
    val defaultNetworkIsWifi: StateFlow<Boolean> = tracker.defaultNetworkIsWifi

    init {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = tracker.onAvailable(network)
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val isValidatedWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                tracker.onCapabilitiesChanged(network, isValidatedWifi)
            }
            override fun onLost(network: Network) = tracker.onLost(network)
        }
        runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
            .onFailure { tracker.reset() }
    }
}

/** Ignores capability/lost callbacks unless they belong to the latest default route. */
internal class DefaultNetworkWifiTracker<N : Any> {
    private val lock = Any()
    private var defaultNetwork: N? = null
    private val _defaultNetworkIsWifi = MutableStateFlow(false)
    val defaultNetworkIsWifi: StateFlow<Boolean> = _defaultNetworkIsWifi.asStateFlow()

    fun onAvailable(network: N) = synchronized(lock) {
        defaultNetwork = network
        _defaultNetworkIsWifi.value = false
    }

    fun onCapabilitiesChanged(network: N, isValidatedWifi: Boolean) = synchronized(lock) {
        if (defaultNetwork == network) _defaultNetworkIsWifi.value = isValidatedWifi
    }

    fun onLost(network: N) = synchronized(lock) {
        if (defaultNetwork == network) {
            defaultNetwork = null
            _defaultNetworkIsWifi.value = false
        }
    }

    fun reset() = synchronized(lock) {
        defaultNetwork = null
        _defaultNetworkIsWifi.value = false
    }
}

@Singleton
class UploadConcurrencyGate @Inject constructor(networkMonitor: UploadNetworkMonitor) {
    private val admission = UploadAdmissionGate(networkMonitor.defaultNetworkIsWifi)

    suspend fun <T> withPermit(block: suspend () -> T): T = admission.withPermit(block)
}

/** Coroutine-only admission core, isolated so network capacity changes are deterministic in JVM tests. */
internal class UploadAdmissionGate(private val wifiState: StateFlow<Boolean>) {
    private val mutex = Mutex()
    private val _activeCount = MutableStateFlow(0)
    internal val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { _activeCount.value = (_activeCount.value - 1).coerceAtLeast(0) }
            }
        }
    }

    private suspend fun acquire() {
        while (true) {
            combine(wifiState, _activeCount) { isWifi, active -> isWifi to active }
                .first { (isWifi, active) -> active < capacity(isWifi) }

            val acquired = mutex.withLock {
                val limit = capacity(wifiState.value)
                if (_activeCount.value >= limit) false
                else {
                    _activeCount.value += 1
                    true
                }
            }
            if (acquired) return
        }
    }

    private fun capacity(isWifi: Boolean): Int = if (isWifi) WIFI_PARALLEL_LIMIT else CELLULAR_SERIAL_LIMIT

    private companion object {
        const val WIFI_PARALLEL_LIMIT = 3
        const val CELLULAR_SERIAL_LIMIT = 1
    }
}
