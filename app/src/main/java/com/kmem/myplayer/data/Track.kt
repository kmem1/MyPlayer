package com.kmem.myplayer.data

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "track_in_playlist", primaryKeys = ["uri", "playlist_id"])
data class Track(
    val uri: Uri,
    @ColumnInfo(name = "playlist_id")
    val playlistId: Int,
    var position: Int,
    val title: String?,
    val artist: String?,
    val duration: Long,
    @ColumnInfo(name = "file_name")
    val fileName: String?,
    @ColumnInfo(name = "position_in_stack")
    var positionInStack: Int
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Uri::class.java.classLoader)!!,
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString(),
        parcel.readString(),
        parcel.readLong(),
        parcel.readString(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(uri, flags)
        parcel.writeInt(playlistId)
        parcel.writeInt(position)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeLong(duration)
        parcel.writeString(fileName)
        parcel.writeInt(positionInStack)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Track> {
        override fun createFromParcel(parcel: Parcel): Track {
            return Track(parcel)
        }

        override fun newArray(size: Int): Array<Track?> {
            return arrayOfNulls(size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Track

        if (this.uri != other.uri || this.playlistId != other.playlistId) return false

        return true
    }
}