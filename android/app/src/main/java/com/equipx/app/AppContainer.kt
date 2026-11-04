package com.equipx.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.equipx.app.data.AssetContentRepository
import com.equipx.app.data.AuthRepository
import com.equipx.app.data.ContentRepository
import com.equipx.app.data.DataStoreProgressRepository
import com.equipx.app.data.MockAuthRepository
import com.equipx.app.data.ProgressRepository
import com.equipx.app.data.equipXDataStore
import com.equipx.app.ml.Detector
import com.equipx.app.ml.FakeDetector

/** Manual DI container. Swap Mock* / Fake* implementations here. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val contentRepository: ContentRepository = AssetContentRepository(appContext.assets)
    val authRepository: AuthRepository = MockAuthRepository(appContext.equipXDataStore)
    val progressRepository: ProgressRepository = DataStoreProgressRepository(appContext.equipXDataStore)
    fun createDetector(): Detector = FakeDetector()
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
