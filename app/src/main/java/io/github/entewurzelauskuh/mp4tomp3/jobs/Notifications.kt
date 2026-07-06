package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.entewurzelauskuh.mp4tomp3.R

/**
 * Builds the conversion notifications (spec §6.4): one ongoing progress notification while the
 * queue drains (filename, determinate progress, a Cancel action) and a short summary when the
 * whole queue finishes. All text comes from `strings.xml`.
 */
object Notifications {
    const val CHANNEL_ID = "conversions"
    const val ONGOING_ID = 1001
    private const val SUMMARY_ID = 1002

    /** Create the single notification channel (idempotent). Safe to call on every start. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW, // no sound/heads-up for progress
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** The ongoing foreground notification for [job]; determinate progress while Running. */
    fun buildProgress(context: Context, job: ConversionJob): Notification {
        val builder = baseBuilder(context)
            .setContentTitle(context.getString(R.string.notification_converting, job.displayName))
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_cancel),
                cancelIntent(context, job.id),
            )
        when (val state = job.state) {
            is JobState.Running -> builder.setProgress(100, state.progressPercent, false)
            else -> builder.setProgress(0, 0, true) // Queued/other -> indeterminate "preparing"
        }
        return builder.build()
    }

    /** A minimal foreground notification used before the first job is picked up. */
    fun buildPreparing(context: Context): Notification = baseBuilder(context)
        .setContentTitle(context.getString(R.string.notification_preparing))
        .setOngoing(true)
        .setProgress(0, 0, true)
        .build()

    /** Post the "N files converted" summary (dismissible). No-op if notifications are denied. */
    fun postSummary(context: Context, convertedCount: Int) {
        if (convertedCount <= 0) return
        val text = context.resources.getQuantityString(
            R.plurals.notification_summary,
            convertedCount,
            convertedCount,
        )
        val summary = baseBuilder(context)
            .setContentTitle(text)
            .setAutoCancel(true)
            .build()
        // Uses NotificationManager directly; silently ignored if POST_NOTIFICATIONS is denied.
        context.getSystemService(NotificationManager::class.java).notify(SUMMARY_ID, summary)
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentIntent(openAppIntent(context))
        .setSilent(true)

    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun cancelIntent(context: Context, jobId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        jobId.hashCode(),
        CancelReceiver.intent(context, jobId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
