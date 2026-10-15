package de.lionsun.plantags.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.lionsun.plantags.extensions.config
import de.lionsun.plantags.extensions.recheckCalDAVCalendars
import de.lionsun.plantags.extensions.refreshCalDAVCalendars
import de.lionsun.plantags.extensions.updateWidgets

class CalDAVSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.config.caldavSync) {
            context.refreshCalDAVCalendars(context.config.caldavSyncedCalendarIds, false)
        }

        context.recheckCalDAVCalendars(true) {
            context.updateWidgets()
        }
    }
}
