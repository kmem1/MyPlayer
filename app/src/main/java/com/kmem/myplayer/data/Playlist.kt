package com.kmem.myplayer.data

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "playlist_id")
    val playlistId: Int,
    @ColumnInfo(name = "playlist_name")
    val name: String,
    @ColumnInfo(name = "last_played_uri")
    var lastPlayedUri: Uri,
    @ColumnInfo(name = "last_played_position")
    var lastPlayedPosition: Int
)