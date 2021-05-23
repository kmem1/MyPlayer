package com.kmem.myplayer.data

import android.net.Uri
import androidx.room.ColumnInfo

data class PlaylistState(
    @ColumnInfo(name = "last_played_uri") val uri: Uri,
    @ColumnInfo(name = "last_played_position") val position: Int
)