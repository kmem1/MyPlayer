package com.kmem.myplayer

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    companion object {
        const val APP_PREFERENCES = "app_preferences"

        const val APP_PREFERENCES_PLAYLIST_ID = "preferences_playlist_id"

        var currentInstance: MyApplication? = null

        fun getInstance(): MyApplication {
            return currentInstance!!
        }

        fun context(): Context {
            return currentInstance!!.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()

        currentInstance = this
    }

}