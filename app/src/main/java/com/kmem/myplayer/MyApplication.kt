package com.kmem.myplayer

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    companion object {
        private const val APP_PREFERENCES = "app_preferences"

        private const val APP_PREFERENCES_PLAYLIST_ID = "preferences_playlist_id"
        private const val APP_PREFERENCES_SHUFFLE = "preferences_shuffle"
        private const val APP_PREFERENCES_REPEAT = "preferences_repeat"
        private const val APP_PREFERENCES_DURATION_POSITION = "preferences_duration_position"
        private const val APP_PREFERENCES_TRACK_POSITION = "preferences_track_position"
        private const val APP_PREFERENCES_PLAYBACK_STATE = "preferences_playback_state"

        lateinit var currentInstance: MyApplication

        fun context(): Context {
            return currentInstance.applicationContext
        }

        fun getPlaybackStateFromPreferences(): Int {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            return sharedPref?.getInt(APP_PREFERENCES_PLAYBACK_STATE, 1) ?: 1
        }

        fun setPlaybackStateFromPreferences(state: Int) {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            with(sharedPref.edit()) {
                putInt(APP_PREFERENCES_PLAYBACK_STATE, state)
                apply()
            }
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

        fun getShuffleModeFromPreferences(): Boolean {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            return sharedPref?.getBoolean(APP_PREFERENCES_SHUFFLE, false) ?: false
        }

        fun setShuffleModeInPreferences(newValue: Boolean) {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            with(sharedPref.edit()) {
                putBoolean(APP_PREFERENCES_SHUFFLE, newValue)
                apply()
            }
        }

        fun getRepeatModeFromPreferences(): Boolean {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            return sharedPref?.getBoolean(APP_PREFERENCES_REPEAT, false) ?: false
        }

        fun setRepeatModeInPreferences(newValue: Boolean) {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            with(sharedPref.edit()) {
                putBoolean(APP_PREFERENCES_REPEAT, newValue)
                apply()
            }
        }

        fun getDurationPositionFromPreferences(): Int {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            return sharedPref?.getInt(APP_PREFERENCES_DURATION_POSITION, 0) ?: 0
        }

        fun setDurationPositionFromPreferences(newValue: Int) {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            with(sharedPref.edit()) {
                putInt(APP_PREFERENCES_DURATION_POSITION, newValue)
                apply()
            }
        }

        fun getTrackPositionFromPreferences(): Int {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            return sharedPref?.getInt(APP_PREFERENCES_TRACK_POSITION, 0) ?: 0
        }

        fun setTrackPositionFromPreferences(newValue: Int) {
            val sharedPref = currentInstance.getSharedPreferences(
                APP_PREFERENCES,
                Context.MODE_PRIVATE
            )

            with(sharedPref.edit()) {
                putInt(APP_PREFERENCES_TRACK_POSITION, newValue)
                apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        currentInstance = this
    }

}