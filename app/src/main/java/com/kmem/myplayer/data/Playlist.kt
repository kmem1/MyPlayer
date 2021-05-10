package com.kmem.myplayer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: Int,
    @ColumnInfo(name = "playlist_name")
    val playlistName: String
)