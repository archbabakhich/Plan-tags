package de.lionsun.plantags.interfaces

import de.lionsun.plantags.models.Event

interface WeeklyCalendar {
    fun updateWeeklyCalendar(events: ArrayList<Event>)
}
