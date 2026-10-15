package de.lionsun.plantags

import de.lionsun.plantags.extensions.hasDummyAlarm
import de.lionsun.plantags.jobs.AppStartupWorker
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.baseConfig

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()

        // Plan Tags ships with its own light palette; Material You colors stay an opt-in in the settings.
        if (baseConfig.appRunCount == 0) {
            baseConfig.isSystemThemeEnabled = false
        }

        if (!hasDummyAlarm()) {
            AppStartupWorker.start(this)
        }
    }
}
