package com.example.cmdvox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class CommandEditorActivity : AppCompatActivity() {
    private lateinit var repository: CommandRepository
    private lateinit var name: EditText; private lateinit var trigger: EditText; private lateinit var list: LinearLayout
    private val actions = mutableListOf<CommandAction>()
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { trigger.setText(it) } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setContentView(R.layout.activity_command_editor); repository = CommandRepository(this)
        name = findViewById(R.id.name_input); trigger = findViewById(R.id.trigger_input); list = findViewById(R.id.action_list)
        intent.getStringExtra(EXTRA_ID)?.let { id -> repository.load().find { it.id == id }?.let { name.setText(it.name); trigger.setText(it.trigger); actions += it.actions } }
        findViewById<View>(R.id.sample_button).setOnClickListener { sample() }; findViewById<View>(R.id.add_action).setOnClickListener { chooseAction() }
        findViewById<View>(R.id.cancel_button).setOnClickListener { finish() }; findViewById<View>(R.id.save_button).setOnClickListener { save() }; render()
    }
    private fun sample() { speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())) }
    private fun chooseAction() { AlertDialog.Builder(this).setTitle(R.string.choose_action).setItems(resources.getStringArray(R.array.action_labels)) { _, i -> configure(CommandAction(Actions.all[i])) }.show() }
    private fun configure(action: CommandAction, replace: Int = -1) {
        when(action.type) {
            Actions.SEEK -> valueDialog(action, replace, R.string.seek_ms_hint, "10000", true)
            Actions.WAIT -> valueDialog(action, replace, R.string.wait_ms_hint, "1500", true)
            Actions.OPEN_URL -> valueDialog(action, replace, R.string.url_hint, action.value, false)
            Actions.OPEN_APP -> chooseApp(replace)
            else -> put(action, replace)
        }
    }
    private fun valueDialog(action: CommandAction, replace: Int, hint: Int, initial: String, number: Boolean) {
        val input = EditText(this); input.hint = getString(hint); input.setText(initial); input.setPadding(48, 16, 48, 16); if(number) input.inputType = 2 or 4096
        AlertDialog.Builder(this).setTitle(R.string.action_value).setView(input).setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.ok) { _, _ -> put(action.copy(value=input.text.toString().trim()), replace) }.show()
    }
    private fun chooseApp(replace: Int) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0).sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.choose_app).setAdapter(ArrayAdapter(this, R.layout.item_app, R.id.app_name, labels)) { _, i -> put(CommandAction(Actions.OPEN_APP, apps[i].activityInfo.packageName), replace) }.show()
    }
    private fun put(action: CommandAction, replace: Int) { if (replace >= 0) actions[replace] = action else actions += action; render() }
    private fun render() { list.removeAllViews(); actions.forEachIndexed { i, action ->
        val row = layoutInflater.inflate(R.layout.item_action, list, false); row.findViewById<TextView>(R.id.action_text).text = describe(action)
        row.findViewById<View>(R.id.action_text).setOnClickListener { configure(action, i) }
        row.findViewById<View>(R.id.up).setOnClickListener { if(i>0) { java.util.Collections.swap(actions,i,i-1); render() } }
        row.findViewById<View>(R.id.down).setOnClickListener { if(i<actions.lastIndex) { java.util.Collections.swap(actions,i,i+1); render() } }
        row.findViewById<View>(R.id.remove).setOnClickListener { actions.removeAt(i); render() }; list.addView(row)
    } }
    private fun describe(a: CommandAction): String { val label = resources.getStringArray(R.array.action_labels)[Actions.all.indexOf(a.type)]; return if(a.value.isBlank()) label else "$label · ${if(a.type == Actions.OPEN_APP) appLabel(a.value) else a.value}" }
    private fun appLabel(pkg: String) = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    private fun save() { if(name.text.isBlank() || trigger.text.isBlank() || actions.isEmpty()) { Toast.makeText(this, R.string.complete_fields, Toast.LENGTH_LONG).show(); return }
        val all = repository.load(); val id = intent.getStringExtra(EXTRA_ID); val existing = all.indexOfFirst { it.id == id }; val command = VoiceCommand(id ?: java.util.UUID.randomUUID().toString(), name.text.toString().trim(), trigger.text.toString().trim(), existing.takeIf { it>=0 }?.let { all[it].enabled } ?: true, actions)
        if(existing >= 0) all[existing]=command else all += command; repository.save(all); setResult(RESULT_OK); finish()
    }
    companion object { const val EXTRA_ID = "command_id" }
}
