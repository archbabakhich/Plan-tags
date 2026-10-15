package de.lionsun.plantags.extensions

import de.lionsun.plantags.helpers.MONTH
import de.lionsun.plantags.helpers.WEEK
import de.lionsun.plantags.helpers.YEAR

fun Int.isXWeeklyRepetition() = this != 0 && this % WEEK == 0

fun Int.isXMonthlyRepetition() = this != 0 && this % MONTH == 0

fun Int.isXYearlyRepetition() = this != 0 && this % YEAR == 0
