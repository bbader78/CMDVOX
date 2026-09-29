package com.example.cmdvox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cmdvox.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CommandRepository
    private val commands = mutableListOf<VoiceCommand>()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else toast(R.string.microphone_required)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        repository = CommandRepository(this); commands += repository.load()
        binding.listenButton.setOnClickListener { requestAndListen() }
        binding.stopButton.setOnClickListener { stopService(Intent(this, VoiceRecognitionService::class.java)); toast(R.string.stopped) }
        binding.mediaPermissionButton.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        binding.addButton.setOnClickListener { showAddDialog() }
        renderCommands()
    }

    private fun requestAndListen() {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) startListening() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startListening() {
        ContextCompat.startForegroundService(this, Intent(this, VoiceRecognitionService::class.java)); toast(R.string.listening_started)
    }

    private fun renderCommands() {
        binding.commandList.removeAllViews()
        commands.forEach { command ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_command, binding.commandList, false)
            row.findViewById<TextView>(R.id.phrase).text = "“${command.phrase}”"
            row.findViewById<TextView>(R.id.action).text = describe(command)
            row.findViewById<ImageButton>(R.id.delete).setOnClickListener { commands.remove(command); repository.save(commands); renderCommands() }
            binding.commandList.addView(row)
        }
    }

    private fun describe(command: VoiceCommand) = when (command.action) {
        Actions.SEEK -> getString(R.string.seek_description, command.value)
        Actions.OPEN_APP -> getString(R.string.open_app_description, command.value)
        Actions.OPEN_URL -> getString(R.string.open_url_description, command.value)
        else -> command.action.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_command, null)
        val spinner = view.findViewById<Spinner>(R.id.action_spinner)
        val labels = resources.getStringArray(R.array.action_labels)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        AlertDialog.Builder(this).setTitle(R.string.add_command).setView(view).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val phrase = view.findViewById<EditText>(R.id.phrase_input).text.toString().trim()
                val value = view.findViewById<EditText>(R.id.value_input).text.toString().trim()
                val actions = arrayOf(Actions.PLAY, Actions.PAUSE, Actions.NEXT, Actions.PREVIOUS, Actions.SEEK, Actions.OPEN_APP, Actions.OPEN_URL)
                if (phrase.isNotEmpty()) { commands += VoiceCommand(phrase = phrase, action = actions[spinner.selectedItemPosition], value = value); repository.save(commands); renderCommands() }
            }.show()
    }
    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
