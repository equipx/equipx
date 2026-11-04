package com.equipx.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.equipx.app.data.ActivityTracker
import com.equipx.app.data.AssetContentRepository
import com.equipx.app.data.AuthRepository
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.DataStoreProgressRepository
import com.equipx.app.data.MockAuthRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.data.DataStoreUiPrefsRepository
import com.equipx.app.data.UiPrefsRepository
import com.equipx.app.data.equipXDataStore
import com.equipx.app.data.remote.ActivitySync
import com.equipx.app.data.remote.DataStoreOutboxStore
import com.equipx.app.data.remote.DataStoreSessionStore
import com.equipx.app.data.remote.OutboxActivitySink
import com.equipx.app.data.remote.RemoteContentIdSource
import com.equipx.app.data.remote.SessionManager
import com.equipx.app.data.remote.SupabaseAuthApi
import com.equipx.app.data.remote.SupabaseAuthRepository
import com.equipx.app.data.remote.SupabaseConfig
import com.equipx.app.data.remote.SyncScheduler
import com.equipx.app.data.remote.UrlConnectionTransport
import com.equipx.app.ml.Detector
import com.equipx.app.ml.DetectorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Manual DI container. Swap Mock* / Fake* implementations here. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val contentRepository: ContentRepository = AssetContentRepository(appContext.assets)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Supabase is used only when android/supabase.properties provided url + key at build time; otherwise mock mode. */
    private val supabaseConfig = SupabaseConfig.fromBuildConfig()
    private val transport = UrlConnectionTransport()
    private val sessionManager: SessionManager? = if (supabaseConfig.isConfigured) {
        SessionManager(SupabaseAuthApi(supabaseConfig, transport), DataStoreSessionStore(appContext.equipXDataStore))
    } else null
    private val outbox = DataStoreOutboxStore(appContext.equipXDataStore)

    /** Background uploader of local activity; null in mock mode. */
    val syncScheduler: SyncScheduler? = sessionManager?.let { sm ->
        SyncScheduler(
            ActivitySync(supabaseConfig, transport, sm, outbox, RemoteContentIdSource(supabaseConfig, transport, appContext.equipXDataStore)),
            appScope,
        ).also { sch ->
            sch.watchNetwork(appContext)
            // New sign-in (or app start with a saved session) => upload anything pending.
            appScope.launch { sm.session.distinctUntilChangedBy { it?.userId }.collect { if (it != null) sch.kick() } }
        }
    }

    val authRepository: AuthRepository = sessionManager?.let { sm ->
        SupabaseAuthRepository(SupabaseAuthApi(supabaseConfig, transport), sm, beforeLogout = { syncScheduler?.flush() })
    } ?: MockAuthRepository(appContext.equipXDataStore)
    val progressRepository: ProgressRepository = DataStoreProgressRepository(appContext.equipXDataStore)
    val uiPrefsRepository: UiPrefsRepository = DataStoreUiPrefsRepository(appContext.equipXDataStore)
    val activityTracker = ActivityTracker(
        progressRepository,
        contentRepository,
        sink = sessionManager?.let { OutboxActivitySink(outbox, it, contentRepository, { syncScheduler }) },
    )
    fun createDetector(): Detector = DetectorFactory.create(appContext)
}

class EquipXApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
