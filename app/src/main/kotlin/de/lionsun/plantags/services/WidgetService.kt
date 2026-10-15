package de.lionsun.plantags.services

import android.content.Intent
import android.widget.RemoteViewsService
import de.lionsun.plantags.adapters.EventListWidgetAdapter

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = EventListWidgetAdapter(applicationContext, intent)
}
