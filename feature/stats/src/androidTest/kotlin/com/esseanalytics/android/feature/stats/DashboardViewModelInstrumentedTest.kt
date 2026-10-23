package com.esseanalytics.android.feature.stats

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.esseanalytics.android.core.database.EsseAnalyticsDatabase
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.CausalFlusher
import com.esseanalytics.android.core.network.CausalIdentityResolver
import com.esseanalytics.android.core.network.CausalPlatformOutbox
import com.esseanalytics.android.core.network.HistoryOutbox
import com.esseanalytics.android.core.network.PlatformUpdateOutbox
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.DashboardFollowersDto
import com.esseanalytics.android.core.network.dto.DashboardSummaryDto
import com.esseanalytics.android.core.network.dto.DashboardWeekDto
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardViewModelInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val database = Room.inMemoryDatabaseBuilder(context, EsseAnalyticsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val apiCalls = Channel<Continuation<DashboardSummaryDto>>(Channel.UNLIMITED)
    private val api = Proxy.newProxyInstance(SyncApi::class.java.classLoader, arrayOf(SyncApi::class.java)) { _, method, args ->
        when (method.name) {
            "getDashboardSummary" -> {
                @Suppress("UNCHECKED_CAST")
                apiCalls.trySend(args!!.last() as Continuation<DashboardSummaryDto>).getOrThrow()
                COROUTINE_SUSPENDED
            }
            "toString" -> "Controlled dashboard API"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args?.firstOrNull() === this
            else -> error("Unexpected SyncApi call in Dashboard test: ${method.name}")
        }
    } as SyncApi
    private val tokenStore = TokenStore(context).also { it.clear() }
    private val settingsStore = SettingsStore(context)
    private val refreshTracker = RefreshActivityTracker()
    private val viewModelStore = ViewModelStore()
    private val viewModel = ViewModelProvider(viewModelStore, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(
            syncRepository = SyncRepository(api),
            tokenStore = tokenStore,
            settingsStore = settingsStore,
            refreshTracker = refreshTracker,
            historyOutbox = HistoryOutbox(
                syncApi = api,
                dao = database.pendingHistoryEventDao(),
                causalDao = database.causalPlatformDao(),
                tokenStore = tokenStore,
                settingsStore = settingsStore,
            ),
            platformUpdateOutbox = PlatformUpdateOutbox(api, database.pendingPlatformUpdateDao()),
            causalPlatformOutbox = CausalPlatformOutbox(
                flusher = CausalFlusher(
                    dao = database.causalPlatformDao(),
                    syncApi = api,
                    identityResolver = object : CausalIdentityResolver {
                        override suspend fun resolveContentId(
                            userKey: String,
                            remoteLibraryVideoId: String?,
                            clientFileId: String?,
                            fileName: String?,
                        ): String? = null
                    },
                    json = Json { ignoreUnknownKeys = true },
                ),
                tokenStore = tokenStore,
                settingsStore = settingsStore,
            ),
        ) as T
    })[DashboardViewModel::class.java]

    @After
    fun closeResources() {
        viewModelStore.clear()
        apiCalls.close()
        database.close()
    }

    @Test
    fun `newer dashboard refresh wins when older response arrives afterward`() = runBlocking {
        val requestA = withTimeout(5_000) { apiCalls.receive() }
        viewModel.refresh()
        val requestB = withTimeout(5_000) { apiCalls.receive() }

        requestB.resume(summary("newer-B"))
        withTimeout(5_000) {
            viewModel.uiState.first { it is DashboardUiState.Success && it.data.generatedAt == "newer-B" }
        }
        requestA.resume(summary("stale-A"))
        withContext(Dispatchers.Main) { } // drain the resumed stale request before asserting

        val finalState = viewModel.uiState.value as DashboardUiState.Success
        assertEquals("newer-B", finalState.data.generatedAt)
        assertEquals(emptySet<String>(), refreshTracker.activeKeys.value)
    }

    @Test
    fun `refresh failure preserves last dashboard data and exposes a refresh error`() = runBlocking {
        val initialRequest = withTimeout(5_000) { apiCalls.receive() }
        initialRequest.resume(summary("last-good"))
        withTimeout(5_000) {
            viewModel.uiState.first { it is DashboardUiState.Success && it.data.generatedAt == "last-good" }
        }

        viewModel.refresh()
        val failedRequest = withTimeout(5_000) { apiCalls.receive() }
        failedRequest.resumeWithException(IOException("fixture network failure"))

        withTimeout(5_000) {
            viewModel.uiState.first {
                it is DashboardUiState.Success && !it.isRefreshing && it.refreshError != null
            }
        }
        val state = viewModel.uiState.value as DashboardUiState.Success
        assertEquals("last-good", state.data.generatedAt)
        assertTrue(state.refreshError!!.contains("No se pudo conectar"))
    }

    private fun summary(label: String) = DashboardSummaryDto(
        generatedAt = label,
        timeZone = "UTC",
        followers = DashboardFollowersDto(total = null, partial = true, platforms = emptyList()),
        week = DashboardWeekDto(
            start = "2026-10-19T00:00:00Z",
            publications = 0,
            views = null,
            viewsDeltaPercent = null,
        ),
        pending = null,
        latest = emptyList(),
    )
}
