package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the **Cancel** action on a conversion notification (spec F8): it maps the tapped
 * job id back to [JobRepository.cancel]. Registered `exported=false`; only our own
 * notification `PendingIntent` triggers it.
 */
class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val deps = context.applicationContext as? ConversionDependencies ?: return
        deps.repository.cancel(jobId)
    }

    companion object {
        const val ACTION_CANCEL = "io.github.entewurzelauskuh.mp4tomp3.action.CANCEL"
        const val EXTRA_JOB_ID = "job_id"

        /** Intent targeting this receiver to cancel [jobId]. */
        fun intent(context: Context, jobId: String): Intent = Intent(context, CancelReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_JOB_ID, jobId)
        }
    }
}
