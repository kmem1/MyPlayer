package com.kmem.myplayer.feature_playlist.domain.model.filechooser

import java.io.File
import java.io.Serializable

// file wrapper for custom name
data class FileModel(val name: String, val file: File) : Serializable