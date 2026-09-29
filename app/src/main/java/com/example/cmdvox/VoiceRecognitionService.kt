package com.example.cmdvox

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

class VoiceRecognitionService : Service(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var stopping = false
    private var lastCommandId: String? = null
    private var lastExecution = 0L
    private val restart = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val stop = Intent(this, VoiceRecognitionService::class.java).setAction(ACTION_STOP)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(getString(R.string.listening_title))
            .setContentText(getString(R.string.listening_description)).setOngoing(true)
            .addAction(0, getString(R.string.stop), PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_IMMUTABLE)).build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        sendStatus(true)
        listen()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopping = true; stopSelf() }
        return START_NOT_STICKY
    }

    private fun listen() {
        if (stopping) return
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
    }

    private fun process(results: Bundle?) {
        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val commands = CommandRepository(this).load()
        val spoken = heard.firstOrNull().orEmpty()
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).putExtra(EXTRA_PHRASE, spoken))
        val match = heard.asSequence().mapNotNull { CommandMatcher.find(it, commands) }.firstOrNull()
        val now = android.os.SystemClock.elapsedRealtime()
        if (match != null && (match.id != lastCommandId || now - lastExecution > 1800)) {
            lastCommandId = match.id; lastExecution = now
            MacroExecutor(this).execute(match)
            sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).putExtra(EXTRA_COMMAND, match.name))
        }
    }

    override fun onResults(results: Bundle?) { process(results); listen() }
    override fun onError(error: Int) {
        val destructive = error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        restart.postDelayed({ if (!stopping) { if (destructive) recreateRecognizer(); listen() } }, if (destructive) 1200 else 450)
    }
    private fun recreateRecognizer() { recognizer?.destroy(); recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) } }
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { stopping = true; restart.removeCallbacksAndMessages(null); recognizer?.cancel(); recognizer?.destroy(); recognizer = null; sendStatus(false); super.onDestroy() }
    private fun sendStatus(active: Boolean) = sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).putExtra(EXTRA_ACTIVE, active))

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }
    companion object {
        const val ACTION_STOP = "com.example.cmdvox.STOP"; const val ACTION_UPDATE = "com.example.cmdvox.UPDATE"
        const val EXTRA_ACTIVE = "active"; const val EXTRA_PHRASE = "phrase"; const val EXTRA_COMMAND = "command"
        private const val CHANNEL = "voice"; private const val NOTIFICATION_ID = 7
    }
}
