package com.techgv.stash.core.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Posts a reminder [AlarmNotificationScheduler] scheduled, with the title and body from the plan, once per alarm, and
 * only while it is still timely (see [isTimely]).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val tag = intent.data?.schemeSpecificPart ?: return
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val fireAt = Instant.fromEpochMilliseconds(intent.getLongExtra(EXTRA_FIRE_AT, 0))
        if (!AlarmNotificationScheduler.recordDelivery(context, tag, fireAt)) return
        if (!isTimely(fireAt, Clock.System.now(), TimeZone.currentSystemDefault())) return
        val notifications = NotificationManagerCompat.from(context)
        // Covers the POST_NOTIFICATIONS permission (Android 13+) and notifications turned off for the app.
        if (!notifications.areNotificationsEnabled()) return
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stash_reminder)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(EXTRA_BODY)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(readerIntent(context, itemId))
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(tag, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the post.
        }
    }

    companion object {
        const val ACTION_REMIND = "com.techgv.stash.action.REMIND"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_FIRE_AT = "fireAt"
        const val CHANNEL_ID = "reading_reminders"
        const val NOTIFICATION_ID = 1

        /**
         * Whether a reminder planned for [fireAt] may still be posted at [now]: at most
         * [AlarmNotificationScheduler.LATE_TOLERANCE] late and before the local midnight that follows it, after which
         * its text ("your streak ends at midnight", "keeps your streak going") is no longer true.
         */
        fun isTimely(fireAt: Instant, now: Instant, zone: TimeZone): Boolean =
            now - fireAt <= AlarmNotificationScheduler.LATE_TOLERANCE &&
                now < fireAt.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

        /** Creates or renames the "Reading reminders" channel; safe to call any number of times. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reading_reminders_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        /** Opens `stash://reader/{itemId}` in this app (the URL form `DeepLinks` in `shared` reads). */
        fun readerIntent(context: Context, itemId: String): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse("stash://reader/$itemId"))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
