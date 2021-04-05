package com.kmem.myplayer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kmem.myplayer.service.PlayerService

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.startService(Intent(context, PlayerService::class.java))
    }
}