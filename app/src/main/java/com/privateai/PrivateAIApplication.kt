package com.privateai

import android.app.Application
import com.privateai.di.AppContainer

class PrivateAIApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.privateai.util.AppLogger.init(this)
        appContainer = AppContainer(this)
    }
}
