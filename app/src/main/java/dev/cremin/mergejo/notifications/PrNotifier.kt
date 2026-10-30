package dev.cremin.mergejo.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.cremin.mergejo.MainActivity
import dev.cremin.mergejo.R
import dev.cremin.mergejo.domain.model.RenovatePr
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a notification per new Renovate PR, bundled under a group summary. Tapping a
 * PR's notification opens its detail screen; tapping the summary opens the list.
 */
@Singleton
class PrNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_NEW_PRS,
            context.getString(R.string.notification_channel_new_prs),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_new_prs_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyNew(prs: List<RenovatePr>) {
        if (prs.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        prs.forEach { pr -> manager.notify(pr.id.hashCode(), prNotification(pr)) }
        manager.notify(SUMMARY_ID, summaryNotification(prs))
    }

    private fun prNotification(pr: RenovatePr) = baseBuilder()
        .setContentTitle(pr.title)
        .setContentText(
            if (pr.isSecurity) context.getString(R.string.notification_security, pr.repoSlug) else pr.repoSlug,
        )
        .apply { pr.createdAt?.let { setWhen(it.toEpochMilliseconds()).setShowWhen(true) } }
        .setContentIntent(openIntent(prId = pr.id, requestCode = pr.id.hashCode()))
        .build()

    private fun summaryNotification(prs: List<RenovatePr>) = baseBuilder()
        .setContentTitle(context.resources.getQuantityString(R.plurals.notification_new_prs, prs.size, prs.size))
        .setStyle(
            NotificationCompat.InboxStyle().also { style ->
                prs.forEach { style.addLine("${it.repoSlug}: ${it.title}") }
            },
        )
        .setGroupSummary(true)
        .setContentIntent(openIntent(prId = null, requestCode = SUMMARY_ID))
        .build()

    private fun baseBuilder() = NotificationCompat.Builder(context, CHANNEL_NEW_PRS)
        .setSmallIcon(R.drawable.ic_stat_mergejo)
        .setColor(ContextCompat.getColor(context, R.color.brand_blue))
        .setGroup(GROUP_NEW_PRS)
        .setAutoCancel(true)

    private fun openIntent(prId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            prId?.let { putExtra(MainActivity.EXTRA_PR_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_NEW_PRS = "new_prs"
        const val GROUP_NEW_PRS = "dev.cremin.mergejo.NEW_PRS"
        const val SUMMARY_ID = 0
    }
}
