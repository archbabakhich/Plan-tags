package de.lionsun.plantags.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import de.lionsun.plantags.extensions.eventsDB
import de.lionsun.plantags.extensions.notifyEvent
import de.lionsun.plantags.extensions.scheduleNextEventReminder
import de.lionsun.plantags.extensions.updateListWidget
import de.lionsun.plantags.helpers.EVENT_ID
import de.lionsun.plantags.helpers.Formatter
import de.lionsun.plantags.helpers.REMINDER_NOTIFICATION
import org.fossify.commons.helpers.ensureBackgroundThread

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "simplecalendar:notificationreceiver")
        wakelock.acquire(3000)

        ensureBackgroundThread {
            handleIntent(context, intent)
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EVENT_ID, -1L)
        if (id != -1L) {
            context.updateListWidget()
            val event = context.eventsDB.getEventOrTaskWithId(id)
            if (
                event == null
                || event.getReminders().none { it.type == REMINDER_NOTIFICATION }
                || event.repetitionExceptions.contains(Formatter.getTodayCode())
            ) {
                return
            }

            if (event.isAttendeeInviteDeclined()) {
                return
            }

            if (!event.repetitionExceptions.contains(Formatter.getDayCodeFromTS(event.startTS))) {
                context.notifyEvent(event)
            }
            context.scheduleNextEventReminder(event, false)
        }
    }
}
