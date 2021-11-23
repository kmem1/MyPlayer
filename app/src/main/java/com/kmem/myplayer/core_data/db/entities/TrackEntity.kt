package com.kmem.myplayer.core_data.db.entities

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import com.kmem.myplayer.core.domain.model.Track

@Entity(tableName = "track_in_playlist", primaryKeys = ["uri", "playlist_id"])
data class TrackEntity(
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

    companion object CREATOR : Parcelable.Creator<TrackEntity> {
        override fun createFromParcel(parcel: Parcel): TrackEntity {
            return TrackEntity(parcel)
        }

        override fun newArray(size: Int): Array<TrackEntity?> {
            return arrayOfNulls(size)
        }

        fun fromTrack(
            track: Track,
            positionInStack: Int = 0
        ): TrackEntity =
            TrackEntity(
                uri = track.uri,
                title = track.title,
                artist = track.artist,
                duration = track.duration,
                fileName = track.fileName,
                playlistId = track.playlistId,
                position = track.position,
                positionInStack = positionInStack
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as TrackEntity

        if (this.uri != other.uri || this.playlistId != other.playlistId) return false

        return true
    }
}

fun TrackEntity.toTrack(): Track =
    Track(
        uri = this.uri,
        title = this.title,
        artist = this.artist,
        duration = this.duration,
        fileName = this.fileName!!,
        playlistId = this.playlistId,
        position = this.position
    )
