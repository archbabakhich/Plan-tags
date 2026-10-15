package de.lionsun.plantags.interfaces

import android.util.SparseArray
import de.lionsun.plantags.models.DayYearly

interface YearlyCalendar {
    fun updateYearlyCalendar(events: SparseArray<ArrayList<DayYearly>>, hashCode: Int)
}
