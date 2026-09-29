package com.example.cmdvox

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cmdvox.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CommandRepository
    private var commands = mutableListOf<VoiceCommand>()
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updatePermissions(); if (it[Manifest.permission.RECORD_AUDIO] == true) startListening() }
    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload() }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(repository.export(commands).toByteArray()) } } }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runCatching { contentResolver.openInputStream(it)!!.bufferedReader().use { r -> repository.import(r.readText()) } }.onSuccess { reload() }.onFailure { toast(R.string.import_error) } } }
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.hasExtra(VoiceRecognitionService.EXTRA_ACTIVE) == true) showActive(i.getBooleanExtra(VoiceRecognitionService.EXTRA_ACTIVE, false))
            i?.getStringExtra(VoiceRecognitionService.EXTRA_PHRASE)?.let { binding.lastPhrase.text = it.ifBlank { getString(R.string.none) } }
            i?.getStringExtra(VoiceRecognitionService.EXTRA_COMMAND)?.let { binding.lastCommand.text = it }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        repository = CommandRepository(this); reload()
        binding.listenButton.setOnClickListener { requestAndListen() }
        binding.stopButton.setOnClickListener { stopService(Intent(this, VoiceRecognitionService::class.java)); showActive(false) }
        binding.addButton.setOnClickListener { editor.launch(Intent(this, CommandEditorActivity::class.java)) }
        binding.settingsButton.setOnClickListener { showSettings() }
        binding.exportButton.setOnClickListener { exportFile.launch("cmd-vox-config.json") }
        binding.importButton.setOnClickListener { importFile.launch(arrayOf("application/json", "text/plain")) }
    }
    override fun onStart() { super.onStart(); ContextCompat.registerReceiver(this, statusReceiver, IntentFilter(VoiceRecognitionService.ACTION_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED); updatePermissions() }
    override fun onStop() { unregisterReceiver(statusReceiver); super.onStop() }
    private fun reload() { commands = runCatching { repository.load() }.getOrDefault(mutableListOf()); renderCommands() }
    private fun requestAndListen() {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) missing += Manifest.permission.RECORD_AUDIO
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) missing += Manifest.permission.POST_NOTIFICATIONS
        if (missing.isEmpty()) startListening() else permissions.launch(missing.toTypedArray())
    }
    private fun startListening() { ContextCompat.startForegroundService(this, Intent(this, VoiceRecognitionService::class.java)); showActive(true) }
    private fun showActive(active: Boolean) { binding.status.text = getString(if (active) R.string.active else R.string.stopped_status); binding.status.setTextColor(getColor(if (active) R.color.green else R.color.muted)) }
    private fun renderCommands() {
        binding.commandList.removeAllViews(); binding.empty.visibility = if (commands.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        commands.forEach { command ->
            val row = layoutInflater.inflate(R.layout.item_command, binding.commandList, false)
            row.findViewById<TextView>(R.id.command_name).text = command.name
            row.findViewById<TextView>(R.id.phrase).text = "“${command.trigger}”"
            row.findViewById<TextView>(R.id.action).text = resources.getQuantityString(R.plurals.action_count, command.actions.size, command.actions.size)
            row.findViewById<Switch>(R.id.enabled).apply { isChecked = command.enabled; setOnCheckedChangeListener { _, value -> command.enabled = value; repository.save(commands) } }
            row.findViewById<ImageButton>(R.id.edit).setOnClickListener { editor.launch(Intent(this, CommandEditorActivity::class.java).putExtra(CommandEditorActivity.EXTRA_ID, command.id)) }
            row.findViewById<ImageButton>(R.id.delete).setOnClickListener { AlertDialog.Builder(this).setMessage(R.string.confirm_delete).setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ -> commands.remove(command); repository.save(commands); renderCommands() }.show() }
            binding.commandList.addView(row)
        }
    }
    private fun updatePermissions() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        binding.permissionSummary.text = getString(R.string.permission_summary, if (mic) "✓" else "!", if (listener) "✓" else "!")
    }
    private fun showSettings() {
        val options = arrayOf(getString(R.string.microphone), getString(R.string.notifications), getString(R.string.notification_access), getString(R.string.accessibility))
        AlertDialog.Builder(this).setTitle(R.string.permissions).setItems(options) { _, which -> when(which) {
            0 -> permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)); 1 -> permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            2 -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); 3 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }}.show()
    }
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()
}
