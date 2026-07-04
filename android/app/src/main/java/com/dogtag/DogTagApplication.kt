package com.dogtag

import android.app.Application
import com.dogtag.data.DogTagRepository
import com.dogtag.data.SettingsRepository
import com.dogtag.notifications.createNotificationChannels
import org.osmdroid.config.Configuration

class DogTagApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var repository: DogTagRepository
        private set

    override fun onCreate() {
        super.onCreate()

        Configuration.getInstance().userAgentValue = packageName
        createNotificationChannels(this)

        settingsRepository = SettingsRepository(this)
        repository = DogTagRepository(settingsRepository)
    }
}
