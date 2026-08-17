package com.esseanalytics.android.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class LanPcAuthState { VERIFYING, AUTHORIZED, REJECTED }

data class DiscoveredLanPc(
    val name: String,
    val url: String,
    val authState: LanPcAuthState,
)

// Equivalente Android de LANPCDiscoveryStore (iOS) -- singleton compartido
// con conteo de referencias (docs/lan-library-auto-switch-design-2026-08-16.md,
// sección 4.2-4.3), a diferencia de LocalPcDiscovery.kt (feature:settings),
// que:
// (a) vive en un módulo que feature:library no puede depender (feature→
//     feature está prohibido en esta arquitectura) -- por eso este store
//     vive en core:network, mismo criterio que LabModeStatus.
// (b) no soporta 2 llamantes simultáneos: su start() empieza llamando a
//     stop(), así que Ajustes y Videos pidiendo descubrimiento a la vez se
//     pisan el callback.
// (c) solo guarda UNA PC (Pair<String,String>?, no lista) -- con 2+
//     instalaciones anunciando el mismo servicio (ver Opción C de Electron,
//     PC secundaria como cliente LAN de la primaria) la segunda pisa a la
//     primera en silencio.
// (d) nunca maneja onServiceLost -- una PC apagada queda "encontrada" para
//     siempre.
// (e) no verifica nada -- "Bonjour la vio" ya cuenta como usable, aunque sea
//     la PC de otro usuario en la misma red.
// Este store corrige los 5 puntos. LocalPcDiscovery.kt (Ajustes) se deja sin
// tocar -- sigue sirviendo para el selector manual todo-o-nada, donde una
// sola PC alcanza y el usuario confirma a mano.
@Singleton
class LanPcDiscoveryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val localBackendApiFactory: LocalBackendApiFactory,
) {
    private val _discovered = MutableStateFlow<List<DiscoveredLanPc>>(emptyList())
    val discovered: StateFlow<List<DiscoveredLanPc>> = _discovered.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.DiscoveryListener? = null
    private var activeObservers = 0

    // FIX 2026-08-17 (review externo, hallazgos reales P1 #2 y #5):
    //
    // #2 -- resolveService/verify() son asíncronos y pueden terminar DESPUÉS
    // de que stop()/onServiceLost ya limpiaron _discovered, resucitando una
    // PC que ya salió de la red (o escribiendo en el StateFlow de una
    // pantalla que ya no está mirando, si stop() bajó activeObservers a 0
    // mientras un resolve seguía en vuelo). `generation` se incrementa en
    // cada arranque/parada real de discovery (no en cada +1/-1 de
    // ref-count) -- todo callback async captura la generación vigente al
    // momento de lanzarse y la compara antes de tocar el StateFlow; si ya
    // no coincide, el resultado se descarta en vez de aplicarse.
    // `liveServiceNames` es el set de servicios que NSD todavía no reportó
    // como perdidos -- cubre el caso más angosto de un `verify()` que
    // termina DESPUÉS de que ese mismo servicio puntual (no todo discovery)
    // se perdió, misma generación.
    //
    // #5 -- onStartDiscoveryFailed (o una excepción síncrona de
    // discoverServices) dejaba `activeObservers` en 1 sin ningún listener
    // real registrado -- la siguiente pantalla que llamara start() sumaba a
    // 2 y el guard `activeObservers > 1` cancelaba cualquier reintento real
    // de discoverServices. Ahora cualquier camino de fallo resetea el
    // ref-count a 0 explícitamente.
    private var generation = 0
    private val liveServiceNames = mutableSetOf<String>()

    // NsdManager.resolveService no soporta resolves concurrentes -- un
    // segundo resolveService con otro ResolveListener mientras uno está en
    // vuelo tira IllegalArgumentException("listener already in use"). Se
    // serializan con una cola simple en vez de lanzar un resolve por cada
    // onServiceFound.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    @Synchronized
    fun start() {
        activeObservers++
        if (activeObservers > 1 || listener != null) return
        generation++
        val sessionGeneration = generation
        liveServiceNames.clear()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                synchronized(this@LanPcDiscoveryStore) {
                    if (generation != sessionGeneration) return
                    stopInternal()
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_esseanalytics")) return
                synchronized(this@LanPcDiscoveryStore) {
                    if (generation != sessionGeneration) return
                    liveServiceNames += serviceInfo.serviceName
                    resolveQueue.addLast(serviceInfo)
                    drainResolveQueue(sessionGeneration)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(this@LanPcDiscoveryStore) {
                    if (generation != sessionGeneration) return
                    liveServiceNames -= serviceInfo.serviceName
                    _discovered.value = _discovered.value.filterNot { it.name == serviceInfo.serviceName }
                }
            }
        }
        listener = discoveryListener
        val started = runCatching {
            nsdManager.discoverServices("_esseanalytics._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.isSuccess
        if (!started) {
            // discoverServices tiró antes de registrar nada -- no dejar el
            // ref-count vivo apuntando a un listener que nunca arrancó.
            listener = null
            activeObservers = 0
        }
    }

    @Synchronized
    fun stop() {
        if (activeObservers > 0) activeObservers--
        if (activeObservers > 0) return
        stopInternal()
    }

    private fun stopInternal() {
        generation++ // invalida cualquier callback/verify() en vuelo
        listener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        listener = null
        activeObservers = 0
        resolveQueue.clear()
        resolving = false
        liveServiceNames.clear()
        _discovered.value = emptyList()
    }

    @Synchronized
    private fun drainResolveQueue(sessionGeneration: Int) {
        if (generation != sessionGeneration || resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        nsdManager.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(this@LanPcDiscoveryStore) {
                        resolving = false
                        drainResolveQueue(sessionGeneration)
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    synchronized(this@LanPcDiscoveryStore) {
                        resolving = false
                        if (generation == sessionGeneration && host != null && serviceInfo.serviceName in liveServiceNames) {
                            val url = "http://$host:${serviceInfo.port}"
                            addOrUpdate(sessionGeneration, DiscoveredLanPc(serviceInfo.serviceName, url, LanPcAuthState.VERIFYING))
                            verify(sessionGeneration, serviceInfo.serviceName, url)
                        }
                        drainResolveQueue(sessionGeneration)
                    }
                }
            },
        )
    }

    @Synchronized
    private fun addOrUpdate(sessionGeneration: Int, pc: DiscoveredLanPc) {
        if (generation != sessionGeneration || pc.name !in liveServiceNames) return
        _discovered.value = _discovered.value.filterNot { it.name == pc.name } + pc
    }

    // "Bonjour/NSD la vio" no alcanza (sección 4.4 punto 2 y 4.5 punto 2 del
    // diseño): recién se promueve a AUTHORIZED si el propio catálogo (con el
    // JWT actual) responde 2xx -- eso ya filtra tanto una PC caída (error de
    // red) como la PC de otro usuario en la misma red (401/403 vía
    // requireOwnerOrNoOwnerSet en local-backend, Fase 0 del diseño). Un solo
    // request liviano (limit=1) alcanza, no hace falta un health-check aparte.
    private fun verify(sessionGeneration: Int, name: String, url: String) {
        scope.launch {
            val authorized = runCatching { localBackendApiFactory.create(url).listVideos(limit = 1) }.isSuccess
            addOrUpdate(sessionGeneration, DiscoveredLanPc(name, url, if (authorized) LanPcAuthState.AUTHORIZED else LanPcAuthState.REJECTED))
        }
    }
}
