package com.example.cmdvox

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID

data class CommandAction(val type: String, val value: String = "") {
    fun toJson() = JSONObject().put("type", type).put("value", value)
    companion object { fun fromJson(o: JSONObject) = CommandAction(o.getString("type"), o.optString("value")) }
}

data class VoiceCommand(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var trigger: String,
    var enabled: Boolean = true,
    val actions: MutableList<CommandAction> = mutableListOf()
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("trigger", trigger)
        .put("enabled", enabled).put("actions", JSONArray().also { a -> actions.forEach { a.put(it.toJson()) } })
    companion object {
        fun fromJson(o: JSONObject): VoiceCommand {
            // Also imports the early CMD VOX schema.
            if (!o.has("actions")) return VoiceCommand(o.optString("id", UUID.randomUUID().toString()),
                o.optString("phrase"), o.optString("phrase"), true,
                mutableListOf(CommandAction(o.optString("action"), o.optString("value"))))
            val array = o.getJSONArray("actions")
            return VoiceCommand(o.optString("id", UUID.randomUUID().toString()), o.optString("name"),
                o.getString("trigger"), o.optBoolean("enabled", true),
                MutableList(array.length()) { CommandAction.fromJson(array.getJSONObject(it)) })
        }
    }
}

object Actions {
    const val PLAY = "PLAY"; const val PAUSE = "PAUSE"; const val TOGGLE = "TOGGLE"
    const val NEXT = "NEXT"; const val PREVIOUS = "PREVIOUS"; const val SEEK = "SEEK"
    const val OPEN_APP = "OPEN_APP"; const val OPEN_URL = "OPEN_URL"; const val WAIT = "WAIT"
    val all = arrayOf(PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS, SEEK, OPEN_APP, OPEN_URL, WAIT)
}

object CommandMatcher {
    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ").trim()
    fun find(spoken: String, commands: List<VoiceCommand>): VoiceCommand? {
        val normalized = normalize(spoken)
        return commands.filter { it.enabled && normalize(it.trigger) == normalized }.maxByOrNull { it.trigger.length }
    }
}
