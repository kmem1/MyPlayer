package com.kmem.myplayer.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.ui.adapters.FileChooserAdapter
import com.kmem.myplayer.R
import kotlinx.coroutines.*
import java.io.File
import java.io.Serializable
import kotlin.collections.ArrayList

class FileChooserActivity : AppCompatActivity(), FileChooserAdapter.Listener {
    companion object {
        const val PATHS = "paths"
    }

    private val list by lazy {findViewById<RecyclerView>(R.id.fileList)}
    private var currentPath : String? = ""
    private var currentDirName : String? = ""
    private var currentDirs : ArrayList<FileTreeComponent> = ArrayList<FileTreeComponent>()
    private var currentTree : FileTreeComponent? = null

    private var internalStoragePath : String = ""
    private var sdCardPath : String = ""

    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var loadingSpinner : ProgressBar

    private var pathsToUpload = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_chooser)

        loadingSpinner = findViewById<ProgressBar>(R.id.progress_bar)
        loadingSpinner.visibility = View.GONE

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val previousPathButton = findViewById<ImageButton>(R.id.prev_path_button)
        previousPathButton.setOnClickListener { openPreviousDir() }
        val homeButton = findViewById<ImageButton>(R.id.home_button)
        homeButton.setOnClickListener { setHomeDirs() }
        val selectAllButton = findViewById<ImageButton>(R.id.select_all)
        selectAllButton.setOnClickListener { selectAllCurrent() }
        val loadButton = findViewById<ImageButton>(R.id.load_files)
        loadButton.setOnClickListener {
            loadFiles()
            sendPaths()
        }

        val adapter = FileChooserAdapter(currentDirs)
        adapter.setListener(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        if (savedInstanceState != null) {
            currentTree = savedInstanceState.getSerializable("currentTree") as FileTreeComponent?
            internalStoragePath = savedInstanceState.getString("internalStoragePath") ?: ""
            sdCardPath = savedInstanceState.getString("sdCardPath") ?: ""
            currentPath = savedInstanceState.getString("currentPath") ?: ""
            openDir(currentTree)
        } else {
            setHomeDirs()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("currentTree", currentTree)
        outState.putString("internalStoragePath", internalStoragePath)
        outState.putString("sdCardPath", sdCardPath)
        outState.putString("currentPath", currentPath)
    }

    private fun setHomeDirs() {
        val result = ArrayList<FileModel>()
        val externalDirs = getExternalFilesDirs(null)

        var internalPath = externalDirs[0].absolutePath
        internalPath = internalPath.replaceFirst("/Android.+".toRegex(), "")
        internalStoragePath = internalPath
        val internalPathFile = File(internalPath)
        val internalFileModel = FileModel("Internal memory", internalPathFile) // Custom name
        result.add(internalFileModel)
        if (externalDirs.size > 1) {
            var sdPath = externalDirs[1].absolutePath
            sdPath = sdPath.replaceFirst("/Android.+".toRegex(), "")
            sdCardPath = sdPath
            val sdPathFile = File(sdPath)
            val sdFileModel = FileModel("SD Card", sdPathFile)
            result.add(sdFileModel)
        }

        currentPath = ""

        // first invoke
        if (currentTree == null)
            currentTree = DirectoryNode(null, null).apply {
                this.childModels.addAll(result)
                this.initialize()
            }

        while(currentTree?.parent != null)
            currentTree = currentTree?.parent

        currentDirs.clear()
        currentDirs.addAll(currentTree?.childsList()!!)
        list.adapter?.notifyDataSetChanged()
        setupToolbarText()
    }

    private fun openDir(dir: FileTreeComponent?) {
        if (dir == null)
            return
        // main tree hasn't parent
        if (dir.parent == null) {
            setHomeDirs()
            return
        }

        currentDirs.clear()
        list.adapter?.notifyDataSetChanged()
        currentTree = dir
        currentPath = dir.model?.file?.absolutePath
        currentDirName = dir.model?.name
        list.scrollToPosition(0)
        setupToolbarText()
        loadingSpinner.visibility = View.VISIBLE
        scope.launch {
                withContext(Dispatchers.IO) {
                    dir.initialize()
                }
                currentDirs.addAll(dir.childsList()!!)
                list.adapter?.notifyDataSetChanged()
                loadingSpinner.visibility = View.GONE
        }
    }

    private fun selectFile(file : FileTreeComponent?, position: Int) {
        file?.changeSelected(!file.isSelected)
        list.adapter?.notifyItemChanged(position)
    }

    private fun setupToolbarText() {
        val dirNameView = findViewById<TextView>(R.id.dir_name) as TextView
        val pathView = findViewById<TextView>(R.id.dir_path) as TextView
        if (currentPath == "")
            dirNameView.text = resources.getString(R.string.home_screen)
        else
            dirNameView.text = currentDirName
        pathView.text = currentPath
    }

    private fun openPreviousDir() {
        val prevDir = currentTree?.parent
        if (prevDir?.model == null)
            setHomeDirs()
        else
            openDir(prevDir)
    }

    private fun selectAllCurrent() {
        if (currentDirs.all {it.isSelected})
            currentDirs.forEach {it.changeSelected(false)}
        else
            currentDirs.forEach {it.changeSelected(true)}
        list.adapter?.notifyDataSetChanged()
    }

    private fun loadFiles(tree: FileTreeComponent? = null) {
        var tmpTree = tree
        if(tree == null) {
            pathsToUpload.clear()
            // get the main tree
            while(currentTree?.parent != null)
                currentTree = currentTree?.parent
            tmpTree = currentTree
        }

        for (child in tmpTree?.childsList()!!) {
            Log.d("qwe", child.model?.name ?: "None")
            if (child.isSelected) {
                if (child.isDirectory)
                    loadAllFiles(child)
                else
                    pathsToUpload.add(child.model?.file?.absolutePath!!)
            } else if (child.hasSelectedChilds) { // always false for files (not dirs)
                loadFiles(child)
            }
        }
    }

    private fun loadAllFiles(tree: FileTreeComponent?) {
        for (child in tree?.childsList()!!) {
            if (child.isDirectory)
                loadAllFiles(child)
            else
                pathsToUpload.add(child.model?.file?.absolutePath!!)
        }
    }

    private fun sendPaths() {
        intent = Intent()
        intent.putStringArrayListExtra(PATHS, pathsToUpload)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onClick(position: Int) {
        val currChild = currentTree?.childAt(position)
        if (currChild?.isDirectory == true)
            openDir(currChild)
        else
            selectFile(currChild, position)
    }

    override fun onCheckboxClick(position: Int, value: Boolean) {
        currentTree?.childAt(position)?.changeSelected(value)
    }

    abstract class FileTreeComponent : Serializable {
        var parent : FileTreeComponent? = null
        var model : FileModel? = null
        var isSelected : Boolean = false
        var hasSelectedChilds = false
        var isInitialized = false
        open var isDirectory = false

        open fun initialize() {}
        open fun checkChilds() {}
        open fun childAt(index: Int) : FileTreeComponent? { return null }
        open fun childsList() : ArrayList<FileTreeComponent>? { return null }
        open fun changeSelected(value: Boolean) {}
        open fun updateChilds(value: Boolean) {}
    }

    class FileNode(parent: FileTreeComponent, model : FileModel) : FileTreeComponent(), Serializable {
        init {
            this.parent = parent
            this.model = model
        }

        override fun changeSelected(value: Boolean) {
            isSelected = value
            parent?.checkChilds()
        }
    }

    class DirectoryNode(parent: FileTreeComponent?, model: FileModel?) : FileTreeComponent(), Serializable {
        var childs = ArrayList<FileTreeComponent>()
        var childModels = ArrayList<FileModel>() // models for lately initializing
        override var isDirectory = true

        init {
            this.parent = parent
            this.model = model
        }

        override fun initialize() {
            if (isInitialized)
                return
            var node : FileTreeComponent // temporary variable

            model?.file?.listFiles()?.forEach {
                if (isAcceptable(it))
                    childModels.add(FileModel(it.name, it))
            }

            for (childModel in childModels) {
                if (childModel.file.isDirectory) {
                    node = DirectoryNode(this, childModel)
                } else {
                    node = FileNode(this, childModel)
                }
                node.isSelected = this.isSelected
                childs.add(node)
            }
            isInitialized = true
        }

        override fun changeSelected(value: Boolean) {
            isSelected = value
            parent?.checkChilds()
            if (isInitialized)
                updateChilds(value)
        }

        override fun checkChilds() {
            if (childs.all { it.isSelected }) {
                isSelected = true
            } else if (childs.any { it.isSelected || it.hasSelectedChilds }) {
                hasSelectedChilds = true
                isSelected = false
            } else {
                isSelected = false
                hasSelectedChilds = false
            }
            parent?.checkChilds()
        }

        override fun updateChilds(value: Boolean) {
            childs.forEach {it.isSelected = value; it.updateChilds(value)}
        }

        override fun childAt(index: Int) : FileTreeComponent {
            //childs[index].initialize()
            return childs[index]
        }

        override fun childsList(): ArrayList<FileTreeComponent> {
            initialize()
            return childs
        }

        private fun isAcceptable(file: File) : Boolean {
            return (file.isDirectory && !file.name.startsWith('.'))
                    || (!file.isDirectory && file.name.endsWith(".mp3"))
        }
    }

    // file wrapper for custom name
    data class FileModel(val name: String, val file : File) : Serializable
}