package de.lionsun.plantags.extensions

import de.lionsun.plantags.models.ListEvent

fun ListEvent.shouldStrikeThrough() = isTaskCompleted || isAttendeeInviteDeclined || isEventCanceled
