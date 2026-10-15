package de.lionsun.plantags.extensions

import de.lionsun.plantags.helpers.Formatter
import de.lionsun.plantags.models.Event

fun Long.isTsOnProperDay(event: Event): Boolean {
    val dateTime = Formatter.getDateTimeFromTS(this)
    val power = 1 shl (dateTime.dayOfWeek - 1)
    return event.repeatRule and power != 0
}
