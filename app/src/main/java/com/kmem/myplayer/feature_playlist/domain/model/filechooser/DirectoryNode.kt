package com.kmem.myplayer.feature_playlist.domain.model.filechooser

import com.kmem.myplayer.feature_playlist.presentation.fragments.FileChooserFragment
import java.io.File
import java.io.Serializable

class DirectoryNode(parent: FileTreeComponent?, model: FileModel?) : FileTreeComponent(),
        Serializable {
    private var children = ArrayList<FileTreeComponent>()
    var childModels = ArrayList<FileModel>() // models for lately initializing
    override var isDirectory = true

    init {
        this.parent = parent
        this.model = model
    }

    override fun initialize() {
        if (isInitialized)
            return
        var node: FileTreeComponent // temporary variable

        model?.file?.listFiles()?.forEach {
            if (isAcceptable(it)) {
                childModels.add(FileModel(it.name, it))
            }
        }

        for (childModel in childModels) {
            node = if (childModel.file.isDirectory) {
                DirectoryNode(this, childModel)
            } else {
                FileNode(this, childModel)
            }
            node.isSelected = this.isSelected
            children.add(node)
        }
        isInitialized = true
    }

    override fun changeSelected(value: Boolean) {
        isSelected = value
        parent?.checkChildren()
        if (isInitialized)
            updateChildren(value)
    }

    override fun checkChildren() {
        when {
            children.all { it.isSelected } -> {
                isSelected = true
            }
            children.any { it.isSelected || it.hasSelectedChildren } -> {
                hasSelectedChildren = true
                isSelected = false
            }
            else -> {
                isSelected = false
                hasSelectedChildren = false
            }
        }
        parent?.checkChildren()
    }

    override fun updateChildren(value: Boolean) {
        children.forEach { it.isSelected = value; it.updateChildren(value) }
    }

    override fun childAt(index: Int): FileTreeComponent {
        return children[index]
    }

    override fun childrenList(): ArrayList<FileTreeComponent> {
        initialize()
        return children
    }

    private fun isAcceptable(file: File): Boolean {
        return (file.isDirectory && !file.name.startsWith('.'))
                || (!file.isDirectory && file.name.endsWith(".mp3"))
    }
}