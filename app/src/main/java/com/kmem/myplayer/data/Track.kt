package com.kmem.myplayer.data

import android.net.Uri
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * Класс, который содержит таблицу БД.
 */

@Entity(tableName = "track_in_playlist", primaryKeys = ["uri", "playlist_id"])
data class Track(
        val uri: Uri,
        @ColumnInfo(name = "playlist_id")
        val playlistId: Int,
        var position: Int,
        val title: String,
        val artist: String,
        val duration: Long,
        @ColumnInfo(name = "file_name")
        val fileName: String) : Serializable