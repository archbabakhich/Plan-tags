package de.lionsun.plantags.interfaces

import de.lionsun.plantags.models.CalendarEntity

interface DeleteCalendarsListener {
    fun deleteCalendars(calendars: ArrayList<CalendarEntity>, deleteEvents: Boolean): Boolean
}
