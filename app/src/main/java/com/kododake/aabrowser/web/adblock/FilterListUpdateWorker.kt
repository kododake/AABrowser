package com.kododake.aabrowser.web.adblock

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Downloads the enabled filter-list subscriptions and recompiles the engine when any changed.
 *
 * Scheduled by WorkManager so refreshes respect Doze, battery and connectivity instead of running
 * on a raw thread at app start: a weekly periodic request (network + battery-not-low) plus an
 * on-demand one-off request for "Update filter lists now".
 */
class FilterListUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val result = RemoteFilterListManager.refreshBlocking(applicationContext, force)
        if (result.updated > 0) AdBlocker.reload(applicationContext)
        val output = workDataOf(
            KEY_UPDATED to result.updated,
            KEY_UNCHANGED to result.unchanged,
            KEY_FAILED to result.failed
        )
        val nothingSucceeded = result.failed > 0 && result.updated + result.unchanged == 0
        return if (nothingSucceeded && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success(output)
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "adblock-filter-update"
        private const val ON_DEMAND_WORK_NAME = "adblock-filter-update-now"
        private const val KEY_FORCE = "force"
        private const val KEY_UPDATED = "updated"
        private const val KEY_UNCHANGED = "unchanged"
        private const val KEY_FAILED = "failed"
        private const val MAX_ATTEMPTS = 3
        private const val REFRESH_INTERVAL_DAYS = 7L

        /** Idempotent: keeps an existing schedule so the next run is not pushed back on every launch. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<FilterListUpdateWorker>(REFRESH_INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * Runs a refresh as soon as the network allows and reports the outcome on the main thread.
         * If a refresh is already running the new one is chained after it (APPEND_OR_REPLACE), so
         * the request always runs and its own result is what the caller sees.
         */
        fun enqueueNow(
            context: Context,
            force: Boolean,
            callback: ((RemoteFilterListManager.UpdateResult) -> Unit)? = null
        ) {
            val appContext = context.applicationContext
            val request = OneTimeWorkRequestBuilder<FilterListUpdateWorker>()
                .setInputData(workDataOf(KEY_FORCE to force))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            val workManager = WorkManager.getInstance(appContext)
            workManager.enqueueUniqueWork(ON_DEMAND_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            if (callback == null) return
            // LiveData must be observed from the main thread.
            Handler(Looper.getMainLooper()).post {
                val liveData = workManager.getWorkInfoByIdLiveData(request.id)
                liveData.observeForever(object : Observer<WorkInfo?> {
                    override fun onChanged(value: WorkInfo?) {
                        val info = value ?: return
                        if (!info.state.isFinished) return
                        liveData.removeObserver(this)
                        val output = info.outputData
                        callback(
                            RemoteFilterListManager.UpdateResult(
                                updated = output.getInt(KEY_UPDATED, 0),
                                unchanged = output.getInt(KEY_UNCHANGED, 0),
                                failed = if (info.state == WorkInfo.State.SUCCEEDED) output.getInt(KEY_FAILED, 0) else 1
                            )
                        )
                    }
                })
            }
        }
    }
}
