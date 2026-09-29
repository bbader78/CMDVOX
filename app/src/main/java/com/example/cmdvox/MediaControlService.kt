package com.example.cmdvox

import android.media.session.MediaController
import android.service.notification.NotificationListenerService

class MediaControlService : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        requestRebind(android.content.ComponentName(this, MediaControlService::class.java))
    }

    companion object {
        @Volatile private var instance: MediaControlService? = null

        fun execute(action: String, seconds: Long): Boolean {
            val service = instance ?: return false
            val controller: MediaController = service.activeNotifications
                .mapNotNull { it.notification.extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession.Token::class.java) }
                .map { MediaController(service, it) }
                .firstOrNull { it.playbackState != null } ?: return false
            val controls = controller.transportControls
            when (action) {
                Actions.PLAY -> controls.play()
                Actions.PAUSE -> controls.pause()
                Actions.NEXT -> controls.skipToNext()
                Actions.PREVIOUS -> controls.skipToPrevious()
                Actions.SEEK -> controls.seekTo((controller.playbackState?.position ?: 0L) + seconds * 1000L)
            }
            return true
        }
    }
}
