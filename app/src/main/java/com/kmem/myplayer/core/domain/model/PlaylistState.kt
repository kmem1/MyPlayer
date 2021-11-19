package com.kmem.myplayer.core.domain.model

import android.net.Uri
import androidx.room.ColumnInfo

data class PlaylistState(
    @ColumnInfo(name = "last_played_uri") val uri: Uri,
    @ColumnInfo(name = "last_played_position") val position: Int
)