package com.test.demospatialaudio

import android.app.Application
import com.test.demospatialaudio.audio.PlaybackController
import com.test.demospatialaudio.domain.AppDatabase
import com.test.demospatialaudio.domain.SeedDataLoader
import com.test.demospatialaudio.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DemoSpatialAudioApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var playback: PlaybackController
        private set
    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        playback = PlaybackController(this)
        preferences = AppPreferences(this)
        applicationScope.launch {
            val db = AppDatabase.getInstance(this@DemoSpatialAudioApp)
            SeedDataLoader.seedIfNeeded(db)
        }
    }
}
