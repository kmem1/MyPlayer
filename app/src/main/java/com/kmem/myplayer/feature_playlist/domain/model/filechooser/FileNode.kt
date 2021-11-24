package com.kmem.myplayer.feature_playlist.domain.model.filechooser

import java.io.Serializable

class FileNode(parent: FileTreeComponent, model: FileModel) : FileTreeComponent(),
        Serializable {
    init {
        this.parent = parent
        this.model = model
    }

    override fun changeSelected(value: Boolean) {
        isSelected = value
        parent?.checkChildren()
    }
}