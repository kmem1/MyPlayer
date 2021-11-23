package com.kmem.myplayer.feature_playlist.domain.model.filechooser

import com.kmem.myplayer.feature_playlist.presentation.fragments.FileChooserFragment
import java.io.Serializable

abstract class FileTreeComponent : Serializable {
    var parent: FileTreeComponent? = null
    var model: FileModel? = null
    var isSelected: Boolean = false
    var hasSelectedChildren = false
    var isInitialized = false
    open var isDirectory = false

    open fun initialize() {}
    open fun checkChildren() {}
    open fun childAt(index: Int): FileTreeComponent? {
        return null
    }

    open fun childrenList(): ArrayList<FileTreeComponent>? {
        return null
    }

    open fun changeSelected(value: Boolean) {}
    open fun updateChildren(value: Boolean) {}
}