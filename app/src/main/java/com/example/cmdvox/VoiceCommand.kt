package com.example.cmdvox

import org.json.JSONArray
import org.json.JSONObject

data class VoiceCommand(
    val id: Long = System.currentTimeMillis(),
    val phrase: String,
    val action: String,
    val value: String = ""
) {
    fun toJson() = JSONObject().put("id", id).put("phrase", phrase)
        .put("action", action).put("value", value)

    companion object {
        fun fromJson(json: JSONObject) = VoiceCommand(
            json.getLong("id"), json.getString("phrase"),
            json.getString("action"), json.optString("value")
        )
    }
}

class CommandRepository(context: android.content.Context) {
    private val preferences = context.getSharedPreferences("cmd_vox", android.content.Context.MODE_PRIVATE)

    fun load(): MutableList<VoiceCommand> {
        val stored = preferences.getString("commands", null) ?: return defaults().toMutableList()
        return runCatching {
            val array = JSONArray(stored)
            MutableList(array.length()) { VoiceCommand.fromJson(array.getJSONObject(it)) }
        }.getOrElse { defaults().toMutableList() }
    }

    fun save(commands: List<VoiceCommand>) {
        val array = JSONArray()
        commands.forEach { array.put(it.toJson()) }
        preferences.edit().putString("commands", array.toString()).apply()
    }

    private fun defaults() = listOf(
        VoiceCommand(1, "riproduci", Actions.PLAY), VoiceCommand(2, "pausa", Actions.PAUSE),
        VoiceCommand(3, "prossima", Actions.NEXT), VoiceCommand(4, "precedente", Actions.PREVIOUS),
        VoiceCommand(10, "avanti 10 secondi", Actions.SEEK, "10"),
        VoiceCommand(30, "avanti 30 secondi", Actions.SEEK, "30"),
        VoiceCommand(60, "avanti 60 secondi", Actions.SEEK, "60")
    )
}

object Actions {
    const val PLAY = "PLAY"
    const val PAUSE = "PAUSE"
    const val NEXT = "NEXT"
    const val PREVIOUS = "PREVIOUS"
    const val SEEK = "SEEK"
    const val OPEN_APP = "OPEN_APP"
    const val OPEN_URL = "OPEN_URL"
}
