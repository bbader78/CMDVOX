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

        fun execute(action: String, offsetMs: Long): Boolean {
            val service = instance ?: return false
            val controller: MediaController = service.activeNotifications
                .mapNotNull { it.notification.extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession.Token::class.java) }
                .map { MediaController(service, it) }
                .firstOrNull { it.playbackState != null } ?: return false
            val controls = controller.transportControls
            when (action) {
                Actions.PLAY -> controls.play()
                Actions.PAUSE -> controls.pause()
                Actions.TOGGLE -> if (controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                Actions.NEXT -> controls.skipToNext()
                Actions.PREVIOUS -> controls.skipToPrevious()
                Actions.SEEK -> {
                    val current = controller.playbackState?.position ?: return false
                    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    var target = (current + offsetMs).coerceAtLeast(0L)
                    if (duration > 0) target = target.coerceAtMost(duration)
                    controls.seekTo(target)
                }
            }
            return true
        }
    }
}
