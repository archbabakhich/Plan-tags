package de.lionsun.plantags.extensions

import de.lionsun.plantags.models.MonthViewEvent

fun MonthViewEvent.shouldStrikeThrough() = isTaskCompleted || isAttendeeInviteDeclined || isEventCanceled
