package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that drains the conversion queue (spec §6.4, F9). It owns no draining
 * logic — that lives in the unit-tested [QueueDrainer]; this class only supplies the FGS
 * lifecycle, notification updates, and dependency lookup.
 *
 * Dependencies come from `application as ConversionDependencies` (Android instantiates the
 * service, so there is no constructor injection); Phase 6's `App` implements that interface.
 * The work runs on [Dispatchers.Default] because [AudioConverter.convert] is blocking.
 */
class ConversionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val draining = AtomicBoolean(false)

    private val deps: ConversionDependencies by lazy { application as ConversionDependencies }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannel(this)
        // Must call startForeground promptly after startForegroundService(). If the platform
        // refuses (ForegroundServiceStartNotAllowedException / FGS start timeout), don't crash —
        // stop and let a later enqueue try again.
        try {
            startForegroundTyped(Notifications.buildPreparing(this))
        } catch (t: Throwable) {
            Log.w(TAG, "Could not start foreground service", t)
            stopSelf()
            return START_NOT_STICKY
        }

        if (draining.compareAndSet(false, true)) {
            scope.launch { runDrain() }
        }
        return START_NOT_STICKY
    }

    private suspend fun runDrain() {
        val repository = deps.repository
        val convertedBefore = doneCount()
        val drainer = QueueDrainer(
            repository = repository,
            converter = deps.converter,
            sinkProvider = deps.sinkProvider,
            settings = deps.settings,
            context = applicationContext,
            onProgress = { job -> updateOngoing(job) },
        )
        // Whether THIS drain is responsible for stopping the service (false if it hands off to
        // another drain that re-acquired the guard).
        var stopService = true
        try {
            while (true) {
                drainer.drainQueue()
                // Release the guard, then re-check. A job enqueued in the gap between the last
                // nextQueued() and clearing the flag would otherwise be orphaned: whoever wins
                // the CAS drains it. If we win, loop again; if nobody enqueued, we finish.
                draining.set(false)
                if (repository.nextQueued() == null) break
                if (!draining.compareAndSet(false, true)) {
                    // Another onStartCommand re-acquired the guard and will drain + stop.
                    stopService = false
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Queue drain failed", t)
            draining.set(false) // we still held the guard; release so the queue isn't wedged
        } finally {
            if (stopService) {
                val converted = (doneCount() - convertedBefore).coerceAtLeast(0)
                Notifications.postSummary(this, converted)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateOngoing(job: ConversionJob) {
        // Refresh the ongoing FGS notification with the current job's progress.
        getSystemService(android.app.NotificationManager::class.java)
            .notify(Notifications.ONGOING_ID, Notifications.buildProgress(this, job))
    }

    private fun doneCount(): Int = deps.repository.jobs.value.count { it.state is JobState.Done }

    private fun startForegroundTyped(notification: android.app.Notification) {
        // mediaProcessing (with its 6-hour budget) exists for transcoding but only from API 35;
        // 31–34 use dataSync. Both types are declared in the manifest.
        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, Notifications.ONGOING_ID, notification, type)
    }

    override fun onDestroy() {
        // The blocking converter only stops cooperatively (it polls isCancellationRequested), so
        // cancelling the scope alone can't interrupt an in-flight conversion. Ask any running job
        // to cancel so it aborts promptly instead of writing after the service is gone.
        runCatching {
            deps.repository.jobs.value
                .filter { it.state is JobState.Running }
                .forEach { deps.repository.cancel(it.id) }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ConversionService"

        /** Start (or nudge) the service to drain the queue. Call after [JobRepository.enqueue]. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ConversionService::class.java),
            )
        }
    }
}
