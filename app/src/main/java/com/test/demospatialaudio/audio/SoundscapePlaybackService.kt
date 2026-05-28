package com.test.demospatialaudio.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.test.demospatialaudio.DemoSpatialAudioApp
import com.test.demospatialaudio.MainActivity
import com.test.demospatialaudio.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SoundscapePlaybackService : Service() {

    companion object {
        const val NOTIFICATION_ID = 17
        const val CHANNEL_ID = "soundscape_playback"

        const val ACTION_PAUSE = "com.test.demospatialaudio.PAUSE"
        const val ACTION_PLAY = "com.test.demospatialaudio.PLAY"
        const val ACTION_STOP = "com.test.demospatialaudio.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        observerJob = scope.launch {
            val controller = (application as? DemoSpatialAudioApp)?.playback
            controller?.state?.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> PlaybackEvents.emit(PlaybackEvents.Event.Pause)
            ACTION_PLAY -> PlaybackEvents.emit(PlaybackEvents.Event.Play)
            ACTION_STOP -> {
                PlaybackEvents.emit(PlaybackEvents.Event.Stop)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val state = (application as? DemoSpatialAudioApp)?.playback?.state?.value
        startForeground(NOTIFICATION_ID, buildNotification(state))
        return START_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(state: PlaybackController.State) {
        if (!state.isPlaying) {
            stopSelf()
            return
        }
        val notification = buildNotification(state)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: PlaybackController.State?): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseAction = NotificationCompat.Action(
            R.drawable.ic_notif_pause,
            "Pause",
            actionPendingIntent(ACTION_PAUSE)
        )
        val playAction = NotificationCompat.Action(
            R.drawable.ic_notif_play,
            "Play",
            actionPendingIntent(ACTION_PLAY)
        )
        val stopAction = NotificationCompat.Action(
            R.drawable.ic_notif_stop,
            "Stop",
            actionPendingIntent(ACTION_STOP)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_atom)
            .setContentTitle(state?.presetName ?: "Soundscape")
            .setContentText(if (state?.isPaused == true) "Paused" else "Drifting in space")
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setShowWhen(false)

        if (state?.isPaused == true) builder.addAction(playAction)
        else builder.addAction(pauseAction)
        builder.addAction(stopAction)

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1)
        )

        return builder.build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, SoundscapePlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_playback_description)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }
}
