package dev.cremin.mergejo.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.cremin.mergejo.domain.model.PollInterval
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Starts, reschedules or stops the periodic [PrPollWorker] to match a [PollInterval]. */
@Singleton
class PrPollScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun apply(interval: PollInterval) {
        val workManager = WorkManager.getInstance(context)
        if (interval == PollInterval.OFF) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<PrPollWorker>(interval.minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "pr_poll"
    }
}
