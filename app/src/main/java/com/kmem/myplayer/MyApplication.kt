package com.kmem.myplayer

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    companion object {
        const val APP_PREFERENCES = "app_preferences"

        const val APP_PREFERENCES_PLAYLIST_ID = "preferences_playlist_id"

        lateinit var currentInstance: MyApplication

        fun getInstance(): MyApplication {
            return currentInstance
        }

        fun context(): Context {
            return currentInstance.applicationContext
        }

        fun getCurrentPlaylistIdFromPreferences(): Int {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            return sharedPref?.getInt(APP_PREFERENCES_PLAYLIST_ID, 1) ?: 1
        }

        fun setCurrentPlaylistIdInPreferences(newId: Int) {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            with(sharedPref.edit()) {
                putInt(APP_PREFERENCES_PLAYLIST_ID, newId)
                apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        currentInstance = this
    }

}