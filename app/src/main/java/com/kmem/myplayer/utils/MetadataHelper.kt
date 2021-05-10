package com.kmem.myplayer.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 *  Вспомогающий класс, который отвечает за получение метаданных из mp3 файла.
 *  Аргументы:
 *      Контекст.
 *      Путь к файлу.
 */

class MetadataHelper(context: Context, val uri: Uri) {

    private val mmr: MediaMetadataRetriever = MediaMetadataRetriever()

    init {
        mmr.setDataSource(context, uri)
    }

    fun getAuthor(): String? {
        return mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
    }

    fun getTitle(): String? {
        return mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
    }

    fun getDuration(): Long {
        val durationString = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        return Integer.parseInt(durationString ?: "0").toLong()
    }

}