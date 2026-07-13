package com.daex.android.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.daex.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns actual model-download I/O in a scope independent of any Activity/ViewModel, so a
 * mandatory first-run download survives the app being backgrounded or the screen turning off.
 * Started via [startModelDownload]/[startKokoroDownload]; progress is published through
 * [ModelDownloadState] rather than a bound-service callback, since a suspend call made
 * directly from a caller's coroutine would inherit that caller's cancellation (e.g. Activity
 * destruction) and defeat the point of running here.
 */
class ModelDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_DOWNLOAD_MODEL = "com.daex.android.action.DOWNLOAD_MODEL"
        private const val ACTION_DOWNLOAD_KOKORO = "com.daex.android.action.DOWNLOAD_KOKORO"
        private const val ACTION_CANCEL_MODEL = "com.daex.android.action.CANCEL_MODEL_DOWNLOAD"
        private const val ACTION_CANCEL_KOKORO = "com.daex.android.action.CANCEL_KOKORO_DOWNLOAD"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val EXTRA_REQUEST_ID = "request_id"

        /**
         * Starts (or redelivers to) the download service and returns a request id the caller
         * must filter on when awaiting [ModelDownloadState.generative] - a status already
         * sitting in that StateFlow from a prior request can otherwise race with this one and
         * be misread as this request's result before the service has even processed the intent.
         */
        fun startModelDownload(context: Context, modelId: String): String {
            val requestId = java.util.UUID.randomUUID().toString()
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(ACTION_DOWNLOAD_MODEL)
                .putExtra(EXTRA_MODEL_ID, modelId)
                .putExtra(EXTRA_REQUEST_ID, requestId)
            ContextCompat.startForegroundService(context, intent)
            return requestId
        }

        /** See [startModelDownload] for why callers must filter on the returned request id. */
        fun startKokoroDownload(context: Context): String {
            val requestId = java.util.UUID.randomUUID().toString()
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(ACTION_DOWNLOAD_KOKORO)
                .putExtra(EXTRA_REQUEST_ID, requestId)
            ContextCompat.startForegroundService(context, intent)
            return requestId
        }

        fun cancelModelDownload(context: Context) {
            context.startService(Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL_MODEL))
        }

        fun cancelKokoroDownload(context: Context) {
            context.startService(Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL_KOKORO))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelManager by lazy { ModelManager(applicationContext) }
    @Volatile private var generativeJob: Job? = null
    @Volatile private var kokoroJob: Job? = null
    private var lastNotifiedGenerativePercent = -1
    private var lastNotifiedKokoroPercent = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        when (intent?.action) {
            ACTION_DOWNLOAD_MODEL -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                val model = ModelBank.models.find { it.id == modelId }
                if (model != null && requestId != null) {
                    startGenerativeDownload(model, requestId)
                } else {
                    // Started via startForegroundService but there's nothing to actually
                    // download - must not leave the service started-but-never-foregrounded,
                    // or the system can kill the app for not promoting in time. maybeStop()
                    // only stops if no other lane is genuinely active, so this can't cut off
                    // an unrelated in-progress Kokoro download.
                    maybeStop()
                }
            }
            ACTION_DOWNLOAD_KOKORO -> {
                if (requestId != null) startKokoroDownloadInternal(requestId) else maybeStop()
            }
            ACTION_CANCEL_MODEL -> modelManager.cancelDownload()
            ACTION_CANCEL_KOKORO -> modelManager.cancelKokoroDownload()
        }
        return START_NOT_STICKY
    }

    private fun startGenerativeDownload(model: Model, requestId: String) {
        synchronized(this) {
            if (generativeJob?.isActive == true) {
                ModelDownloadState.updateGenerative(
                    GenerativeDownloadStatus(requestId, model.id, DownloadPhase.ERROR, error = "A model download is already in progress.")
                )
                return
            }
            ensureForeground(model.name)
            lastNotifiedGenerativePercent = -1
            generativeJob = serviceScope.launch {
                ModelDownloadState.updateGenerative(GenerativeDownloadStatus(requestId, model.id, DownloadPhase.DOWNLOADING, 0))
                try {
                    modelManager.downloadModel(model) { progress ->
                        ModelDownloadState.updateGenerative(
                            GenerativeDownloadStatus(requestId, model.id, DownloadPhase.DOWNLOADING, progress.percent)
                        )
                        maybeUpdateNotification(model.name, progress.percent, kokoro = false)
                    }
                    ModelDownloadState.updateGenerative(GenerativeDownloadStatus(requestId, model.id, DownloadPhase.COMPLETED, 100))
                } catch (e: Exception) {
                    val phase = if (e.message == "Download cancelled") DownloadPhase.CANCELLED else DownloadPhase.ERROR
                    ModelDownloadState.updateGenerative(GenerativeDownloadStatus(requestId, model.id, phase, error = e.message))
                } finally {
                    generativeJob = null
                    maybeStop()
                }
            }
        }
    }

    private fun startKokoroDownloadInternal(requestId: String) {
        synchronized(this) {
            if (kokoroJob?.isActive == true) {
                ModelDownloadState.updateKokoro(
                    KokoroDownloadStatus(requestId, DownloadPhase.ERROR, error = "A TTS download is already in progress.")
                )
                return
            }
            ensureForeground("Kokoro TTS Voice")
            lastNotifiedKokoroPercent = -1
            kokoroJob = serviceScope.launch {
                ModelDownloadState.updateKokoro(KokoroDownloadStatus(requestId, DownloadPhase.DOWNLOADING, 0))
                try {
                    modelManager.downloadKokoro { percent ->
                        ModelDownloadState.updateKokoro(KokoroDownloadStatus(requestId, DownloadPhase.DOWNLOADING, percent))
                        maybeUpdateNotification("Kokoro TTS Voice", percent, kokoro = true)
                    }
                    ModelDownloadState.updateKokoro(KokoroDownloadStatus(requestId, DownloadPhase.COMPLETED, 100))
                } catch (e: Exception) {
                    val phase = if (e.message == "Download cancelled") DownloadPhase.CANCELLED else DownloadPhase.ERROR
                    ModelDownloadState.updateKokoro(KokoroDownloadStatus(requestId, phase, error = e.message))
                } finally {
                    kokoroJob = null
                    maybeStop()
                }
            }
        }
    }

    private fun ensureForeground(label: String) {
        startForeground(NOTIFICATION_ID, buildNotification(label, 0))
    }

    @Synchronized
    private fun maybeStop() {
        val generativeActive = generativeJob?.isActive == true
        val kokoroActive = kokoroJob?.isActive == true
        if (!generativeActive && !kokoroActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun maybeUpdateNotification(label: String, percent: Int, kokoro: Boolean) {
        if (kokoro) {
            if (percent == lastNotifiedKokoroPercent) return
            lastNotifiedKokoroPercent = percent
        } else {
            if (percent == lastNotifiedGenerativePercent) return
            lastNotifiedGenerativePercent = percent
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(label, percent))
    }

    private fun buildNotification(label: String, percent: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle("Downloading $label")
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress for DAEX model downloads"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // API 34+: system-enforced dataSync execution budget exceeded. The .part files
        // already on disk (see ModelManager's resume support) let the next attempt resume
        // cleanly rather than restarting, so stopping here is safe.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
