package dev.cremin.mergejo.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.cremin.mergejo.data.auth.AuthManager
import dev.cremin.mergejo.data.preferences.SettingsRepository
import dev.cremin.mergejo.data.repository.RenovateRepository
import dev.cremin.mergejo.domain.NewPrDetector

/**
 * The periodic background check: reloads open Renovate PRs and notifies about any
 * that weren't there on earlier runs. Scheduled by [PrPollScheduler].
 */
@HiltWorker
class PrPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authManager: AuthManager,
    private val repository: RenovateRepository,
    private val settings: SettingsRepository,
    private val notifier: PrNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!authManager.isAuthorized.value) {
            // Signed out: forget what we'd seen so the next sign-in starts a fresh baseline.
            settings.clearNotifiedPrIds()
            return Result.success()
        }

        val prs = repository.refresh().getOrElse {
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }

        val outcome = NewPrDetector.detect(settings.notifiedPrIds(), prs)
        settings.setNotifiedPrIds(outcome.seen)
        notifier.notifyNew(outcome.newPrs)
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
