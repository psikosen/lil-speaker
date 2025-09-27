package com.example.lilspeaker

import android.app.Application
import com.example.lilspeaker.core.logging.AppLogger

class LilSpeakerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(
            sourceClass = "LilSpeakerApp",
            function = "onCreate",
            systemSection = "app_start",
            message = "Application boot complete"
        )
    }
}
