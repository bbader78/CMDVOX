package com.example.cmdvox

import android.content.Context
import org.json.JSONArray

class CommandRepository(context: Context) {
    private val preferences = context.getSharedPreferences("cmd_vox", Context.MODE_PRIVATE)
    fun load(): MutableList<VoiceCommand> = decode(preferences.getString("commands", "[]") ?: "[]")
    fun save(commands: List<VoiceCommand>) = preferences.edit().putString("commands", encode(commands)).apply()
    fun export(commands: List<VoiceCommand>) = encode(commands)
    fun import(json: String): MutableList<VoiceCommand> = decode(json).also { save(it) }
    private fun encode(commands: List<VoiceCommand>) = JSONArray().also { a -> commands.forEach { a.put(it.toJson()) } }.toString(2)
    private fun decode(value: String): MutableList<VoiceCommand> {
        val a = JSONArray(value)
        return MutableList(a.length()) { VoiceCommand.fromJson(a.getJSONObject(it)) }
    }
}
