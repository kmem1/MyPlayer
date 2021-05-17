package com.kmem.myplayer

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    companion object {
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