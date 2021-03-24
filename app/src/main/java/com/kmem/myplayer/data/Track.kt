package com.kmem.myplayer.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class Track(
        var position: Int,
        val title: String,
        val artist: String,
        @PrimaryKey
        val uri: Uri,
        val duration: Long,
        @ColumnInfo(name = "file_name")
        val fileName: String)