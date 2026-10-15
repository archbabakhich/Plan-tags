package de.lionsun.plantags.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.lionsun.plantags.extensions.scheduleDummyAlarm

class DummyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.scheduleDummyAlarm()
    }
}
