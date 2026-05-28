package com.test.demospatialaudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.test.demospatialaudio.presentation.navigation.AppNavigation
import com.test.demospatialaudio.presentation.navigation.PendingAction
import com.test.demospatialaudio.presentation.theme.SpatialAudioTheme

class MainActivity : ComponentActivity() {

    private val pendingActionState = mutableStateOf<PendingAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingActionState.value = resolvePendingAction(intent)
        setContent {
            val pending by pendingActionState
            SpatialAudioTheme {
                AppNavigation(pendingAction = pending)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingActionState.value = resolvePendingAction(intent)
    }

    private fun resolvePendingAction(intent: Intent?): PendingAction? {
        val data: Uri = intent?.data ?: return null
        if (data.scheme != "atoms") return null
        return when (data.host) {
            "action" -> when (data.lastPathSegment) {
                "new" -> PendingAction.NewSpace
                "resume" -> PendingAction.Resume
                else -> null
            }
            else -> null
        }
    }
}
