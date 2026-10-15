package de.lionsun.plantags.helpers

import android.content.Context
import de.lionsun.plantags.extensions.eventsHelper
import de.lionsun.plantags.interfaces.WeeklyCalendar
import de.lionsun.plantags.models.Event
import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.helpers.WEEK_SECONDS

class WeeklyCalendarImpl(val callback: WeeklyCalendar, val context: Context) {
    var mEvents = ArrayList<Event>()

    fun updateWeeklyCalendar(weekStartTS: Long) {
        val endTS = weekStartTS + 2 * WEEK_SECONDS
        context.eventsHelper.getEvents(weekStartTS - DAY_SECONDS, endTS) {
            mEvents = it
            callback.updateWeeklyCalendar(it)
        }
    }
}
