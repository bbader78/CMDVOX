package com.example.cmdvox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object CommandExecutor {
    fun execute(context: Context, command: VoiceCommand): Boolean = when (command.action) {
        Actions.PLAY, Actions.PAUSE, Actions.NEXT, Actions.PREVIOUS, Actions.SEEK -> {
            if (!MediaControlService.execute(command.action, command.value.toLongOrNull() ?: 0L)) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                false
            } else true
        }
        Actions.OPEN_APP -> context.packageManager.getLaunchIntentForPackage(command.value)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } ?: false
        Actions.OPEN_URL -> runCatching {
            val uri = Uri.parse(if (command.value.contains("://")) command.value else "https://${command.value}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false)
        else -> false
    }
}
