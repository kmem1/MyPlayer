package com.kmem.myplayer.feature_playlist.presentation.viewmodels

import android.app.Application
import android.content.Context

import androidx.core.content.ContextCompat.getExternalFilesDirs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kmem.myplayer.core_data.repositories.MusicRepository
import com.kmem.myplayer.feature_playlist.presentation.fragments.FileChooserFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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

    private val _currentPath: MutableLiveData<String> = MutableLiveData()
    private val _currentDirName: MutableLiveData<String> = MutableLiveData()
    private val _currentDirs: MutableLiveData<ArrayList<FileChooserFragment.FileTreeComponent>> =
        MutableLiveData()

    val currentPath: LiveData<String> = _currentPath
    val currentDirName: LiveData<String> = _currentDirName
    val currentDirs: LiveData<ArrayList<FileChooserFragment.FileTreeComponent>> = _currentDirs
    var wasSelectedOneFile = false
    var positionSelected = 0

    private var currentTree: FileChooserFragment.FileTreeComponent? = null
    private val pathsToUpload: ArrayList<String> = ArrayList()

    init {
        setHomeDirs()
    }

    fun setHomeDirs() {
        val result = ArrayList<FileChooserFragment.FileModel>()
        val externalDirs = getExternalFilesDirs(getApplication(), null)

        var internalPath = externalDirs[0].absolutePath
        internalPath = internalPath.replaceFirst("/Android.+".toRegex(), "")
        val internalPathFile = File(internalPath)
        val internalFileModel =
            FileChooserFragment.FileModel("Internal memory", internalPathFile) // Custom name
        result.add(internalFileModel)
        if (externalDirs.size > 1) {
            var sdPath = externalDirs[1].absolutePath
            sdPath = sdPath.replaceFirst("/Android.+".toRegex(), "")
            val sdPathFile = File(sdPath)
            val sdFileModel = FileChooserFragment.FileModel("SD Card", sdPathFile)
            result.add(sdFileModel)
        }

        _currentPath.value = ""
        _currentDirName.value = ""

        // first invoke
        if (currentTree == null)
            currentTree = FileChooserFragment.DirectoryNode(null, null).apply {
                this.childModels.addAll(result)
                this.initialize()
            }

        while (currentTree?.parent != null)
            currentTree = currentTree?.parent

        wasSelectedOneFile = false
        _currentDirs.value = currentTree?.childrenList()!!
    }

    private suspend fun openDir(dir: FileChooserFragment.FileTreeComponent?) {
        if (dir == null)
            return
        // main tree hasn't parent
        if (dir.parent == null) {
            setHomeDirs()
            return
        }

        currentTree = dir
        _currentPath.value = dir.model?.file?.absolutePath
        _currentDirName.value = dir.model?.name

        withContext(Dispatchers.IO) {
            dir.initialize()
        }

        wasSelectedOneFile = false
        _currentDirs.value = dir.childrenList()!!
    }

    suspend fun openPreviousDir() {
        val prevDir = currentTree?.parent
        if (prevDir?.model == null)
            setHomeDirs()
        else
            openDir(prevDir)
    }

    suspend fun onListItemClick(position: Int) {
        val currChild = currentTree!!.childAt(position)
        if (currChild!!.isDirectory)
            openDir(currChild)
        else
            selectFile(currChild, position)
    }

    fun onCheckboxClick(position: Int, value: Boolean) {
        currentTree?.childAt(position)?.changeSelected(value)
    }

    private fun selectFile(file: FileChooserFragment.FileTreeComponent, position: Int) {
        wasSelectedOneFile = true
        positionSelected = position
        file.changeSelected(!file.isSelected)
        _currentDirs.value = currentTree?.childrenList()!!
    }

    fun selectAllCurrent() {
        if (currentTree?.childrenList()!!.all { it.isSelected })
            currentTree?.childrenList()!!.forEach { it.changeSelected(false) }
        else
            currentTree?.childrenList()!!.forEach { it.changeSelected(true) }

        wasSelectedOneFile = false
        _currentDirs.value = currentTree?.childrenList()!!
    }

    /**
     * Load selected tracks from tree
     * @param tree Tree which tracks should be loaded
     */
    private suspend fun loadFiles(tree: FileChooserFragment.FileTreeComponent? = null): ArrayList<String> {
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
    private suspend fun loadAllFiles(tree: FileChooserFragment.FileTreeComponent?) {
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