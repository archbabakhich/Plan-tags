package de.lionsun.plantags.services

import android.content.Intent
import android.widget.RemoteViewsService
import de.lionsun.plantags.adapters.EventListWidgetAdapterEmpty

class WidgetServiceEmpty : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = EventListWidgetAdapterEmpty(applicationContext)
}
