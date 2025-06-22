package com.dark.neuroverse

import android.app.Application
import android.util.Log

class NeuroVerseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NeuroVerseApplication", "✅ Application started")
    }
}
