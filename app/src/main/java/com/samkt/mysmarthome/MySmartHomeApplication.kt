package com.samkt.mysmarthome

import android.app.Application
import timber.log.Timber

class MySmartHomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initTimber()
    }

    private fun initTimber() {
        Timber.plant(Timber.DebugTree())
    }
}
