package app.cellscope.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        if (!RecordingPreferences(context).recordingEnabled) return
        try {
            RecordingService.ensureRunning(context)
        } catch (error: RuntimeException) {
            // The next user launch will retry and record the resulting timeline gap.
            Log.e("CellScopeStartup", "Unable to start recorder after ${intent.action}", error)
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
