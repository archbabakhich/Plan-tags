package de.lionsun.plantags.interfaces

import android.content.Context
import de.lionsun.plantags.models.DayMonthly
import org.joda.time.DateTime

interface MonthlyCalendar {
    fun updateMonthlyCalendar(context: Context, month: String, days: ArrayList<DayMonthly>, checkedEvents: Boolean, currTargetDate: DateTime)
}
