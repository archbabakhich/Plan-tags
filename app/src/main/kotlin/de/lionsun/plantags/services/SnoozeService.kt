package de.lionsun.plantags.services

import android.app.IntentService
import android.content.Intent
import de.lionsun.plantags.extensions.config
import de.lionsun.plantags.extensions.eventsDB
import de.lionsun.plantags.extensions.rescheduleReminder
import de.lionsun.plantags.helpers.EVENT_ID

class SnoozeService : IntentService("Snooze") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val eventId = intent.getLongExtra(EVENT_ID, 0L)
            val event = eventsDB.getEventOrTaskWithId(eventId)
            rescheduleReminder(event, config.snoozeTime)
        }
    }
}
