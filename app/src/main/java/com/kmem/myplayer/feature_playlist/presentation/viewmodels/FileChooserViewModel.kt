package com.kmem.myplayer.feature_playlist.presentation.viewmodels

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat.getExternalFilesDirs
import androidx.lifecycle.AndroidViewModel
import com.kmem.myplayer.core_data.repositories.MusicRepository
import com.kmem.myplayer.feature_playlist.domain.model.filechooser.DirectoryNode
import com.kmem.myplayer.feature_playlist.domain.model.filechooser.FileModel
import com.kmem.myplayer.feature_playlist.domain.model.filechooser.FileTreeComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 *  ViewModel for FileChooserFragment.
 *  Works with directories.
 */

class FileChooserViewModel(application: Application) : AndroidViewModel(application) {

    interface Repository {
        fun addTracks(context: Context, paths: ArrayList<String>, playlistId: Int)
    }

    private val _currentPath: MutableStateFlow<String> = MutableStateFlow("")
    private val _currentDirName: MutableStateFlow<String> = MutableStateFlow("")
    private val _currentDirs: MutableStateFlow<List<FileTreeComponent>> =
            MutableStateFlow(emptyList())

    val currentPath = _currentPath.asStateFlow()
    val currentDirName = _currentDirName.asStateFlow()
    val currentDirs = _currentDirs.asStateFlow()

    private var currentTree: FileTreeComponent? = null
    private val pathsToUpload: ArrayList<String> = ArrayList()

    init {
        setHomeDirs()
    }

    fun setHomeDirs() {
        val result = ArrayList<FileModel>()
        val externalDirs = getExternalFilesDirs(getApplication(), null)

        var internalPath = externalDirs[0].absolutePath
        internalPath = internalPath.replaceFirst("/Android.+".toRegex(), "")
        val internalPathFile = File(internalPath)
        val internalFileModel = FileModel("Internal memory", internalPathFile) // Custom name
        result.add(internalFileModel)
        if (externalDirs.size > 1) {
            var sdPath = externalDirs[1].absolutePath
            sdPath = sdPath.replaceFirst("/Android.+".toRegex(), "")
            val sdPathFile = File(sdPath)
            val sdFileModel = FileModel("SD Card", sdPathFile)
            result.add(sdFileModel)
        }

        _currentPath.value = ""
        _currentDirName.value = ""

        // first invoke
        if (currentTree == null)
            currentTree = DirectoryNode(null, null).apply {
                this.childModels.addAll(result)
                this.initialize()
            }

        while (currentTree?.parent != null)
            currentTree = currentTree?.parent

        _currentDirs.value = currentTree?.childrenList()!!
    }

    private suspend fun openDir(dir: FileTreeComponent?) {
        if (dir == null)
            return
        // main tree hasn't parent
        if (dir.parent == null) {
            setHomeDirs()
            return
        }

        currentTree = dir
        _currentPath.value = dir.model?.file?.absolutePath ?: ""
        _currentDirName.value = dir.model?.name ?: ""

        withContext(Dispatchers.IO) {
            dir.initialize()
        }

        _currentDirs.value = dir.childrenList()!!
    }

    suspend fun openPreviousDir() {
        val prevDir = currentTree?.parent
        if (prevDir?.model == null) {
            setHomeDirs()
        } else {
            openDir(prevDir)
        }
    }

    suspend fun onListItemClick(position: Int) {
        val currChild = currentTree!!.childAt(position)
        if (currChild!!.isDirectory) {
            _currentDirs.value = emptyList()
            openDir(currChild)
        } else {
            selectFile(currChild)
        }
    }

    fun onCheckboxClick(position: Int, value: Boolean) {
        currentTree?.childAt(position)?.changeSelected(value)
    }

    private fun selectFile(file: FileTreeComponent) {
        file.changeSelected(!file.isSelected)
    }

    fun selectAllCurrent() {
        if (currentTree?.childrenList()!!.all { it.isSelected }) {
            currentTree?.childrenList()!!.forEach { it.changeSelected(false) }
        } else {
            currentTree?.childrenList()!!.forEach { it.changeSelected(true) }
        }

        _currentDirs.value = currentTree?.childrenList()!!
    }

    fun getDirectoryAtPosition(position: Int): FileTreeComponent {
        return currentDirs.value[position]
    }

    /**
     * Load selected tracks from tree
     * @param tree Tree which tracks should be loaded
     */
    private suspend fun loadFiles(tree: FileTreeComponent? = null): ArrayList<String> {
        withContext(Dispatchers.IO) {
            var tmpTree = tree
            if (tree == null) {
                pathsToUpload.clear()
                // get the main tree
                while (currentTree?.parent != null)
                    currentTree = currentTree?.parent
                tmpTree = currentTree
            }

            for (child in tmpTree?.childrenList()!!) {
                if (child.isSelected) {
                    if (child.isDirectory)
                        loadAllFiles(child)
                    else
                        pathsToUpload.add(child.model?.file?.absolutePath!!)
                } else if (child.hasSelectedChildren) { // always false for files (not dirs)
                    loadFiles(child)
                }
            }
        }

        return pathsToUpload
    }

    /**
     * Load all files from tree. This function is used to load selected directories.
     * @param tree Tree which files should be loaded
     */
    private suspend fun loadAllFiles(tree: FileTreeComponent?) {
        withContext(Dispatchers.IO) {
            for (child in tree?.childrenList()!!) {
                if (child.isDirectory)
                    loadAllFiles(child)
                else
                    pathsToUpload.add(child.model?.file?.absolutePath!!)
            }
        }
    }

    fun loadFilesToRepository(playlistId: Int) {
        val repository: Repository = MusicRepository.getInstance()
        MainScope().launch {
            repository.addTracks(getApplication(), loadFiles(), playlistId)
        }
    }
}