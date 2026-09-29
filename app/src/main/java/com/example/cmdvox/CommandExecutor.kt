package com.example.cmdvox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper

class MacroExecutor(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())

    fun execute(command: VoiceCommand, finished: (Boolean) -> Unit = {}) = run(command.actions, 0, true, finished)

    private fun run(actions: List<CommandAction>, index: Int, success: Boolean, finished: (Boolean) -> Unit) {
        if (index >= actions.size) { finished(success); return }
        val action = actions[index]
        if (action.type == Actions.WAIT) {
            handler.postDelayed({ run(actions, index + 1, success, finished) }, action.value.toLongOrNull()?.coerceIn(0, 600_000) ?: 0)
            return
        }
        val result = executeOne(action)
        handler.post { run(actions, index + 1, success && result, finished) }
    }

    private fun executeOne(action: CommandAction): Boolean = when (action.type) {
        Actions.PLAY, Actions.PAUSE, Actions.TOGGLE, Actions.NEXT, Actions.PREVIOUS, Actions.SEEK ->
            MediaControlService.execute(action.type, action.value.toLongOrNull() ?: 0L)
        Actions.OPEN_APP -> context.packageManager.getLaunchIntentForPackage(action.value)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } ?: false
        Actions.OPEN_URL -> runCatching {
            val value = if (action.value.contains("://")) action.value else "https://${action.value}"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false)
        else -> false
    }
}
