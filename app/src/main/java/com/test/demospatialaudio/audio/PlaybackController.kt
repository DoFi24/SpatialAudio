package com.test.demospatialaudio.audio

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaybackController(private val app: Application) {

    val engine = SpatialAudioEngine(app)
    val animationDriver = AtomAnimationDriver(engine)
    val headTracking = HeadTrackingService(app, engine)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    data class State(
        val presetId: String? = null,
        val presetName: String? = null,
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false
    )

    fun bindPreset(presetId: String, presetName: String) {
        val current = _state.value
        if (current.presetId != presetId) {
            _state.value = State(presetId = presetId, presetName = presetName, isPlaying = false)
        } else {
            _state.value = current.copy(presetName = presetName)
        }
    }

    fun onPlaybackStarted() {
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
        startService()
    }

    fun onPlaybackPaused(paused: Boolean) {
        _state.value = _state.value.copy(isPaused = paused)
        startService()
    }

    fun onPlaybackStopped() {
        _state.value = _state.value.copy(isPlaying = false, isPaused = false)
        stopService()
    }

    fun isBoundTo(presetId: String): Boolean = _state.value.presetId == presetId

    private fun startService() {
        val intent = Intent(app, SoundscapePlaybackService::class.java)
        ContextCompat.startForegroundService(app, intent)
    }

    private fun stopService() {
        app.stopService(Intent(app, SoundscapePlaybackService::class.java))
    }
}
