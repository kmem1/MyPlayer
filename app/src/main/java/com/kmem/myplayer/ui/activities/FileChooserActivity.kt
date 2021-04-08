package com.kmem.myplayer.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.ui.adapters.FileChooserAdapter
import com.kmem.myplayer.viewmodels.FileChooserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.Serializable

/**
 *  Активность экрана добавления песен.
 *  Отвечает за графические элементы и взаимодействие с пользователем.
 */

class FileChooserActivity : AppCompatActivity(), FileChooserAdapter.Listener {
    companion object {
        const val PATHS = "paths"
    }

    private val list by lazy { findViewById<RecyclerView>(R.id.fileList) }
    private val currentDirs: ArrayList<FileTreeComponent> = ArrayList()
    private lateinit var model: FileChooserViewModel

    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var loadingSpinner: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_chooser)

        model = ViewModelProvider(this).get(FileChooserViewModel::class.java)

        model.currentDirs.observe(this, currentDirsObserver)
        model.currentDirName.observe(this, currentDirNameObserver)
        model.currentPath.observe(this, currentPathObserver)

        loadingSpinner = findViewById<ProgressBar>(R.id.progress_bar)
        loadingSpinner.visibility = View.GONE

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val previousPathButton = findViewById<ImageButton>(R.id.prev_path_button)
        previousPathButton.setOnClickListener { scope.launch { model.openPreviousDir() } }
        val homeButton = findViewById<ImageButton>(R.id.home_button)
        homeButton.setOnClickListener { model.setHomeDirs() }
        val selectAllButton = findViewById<ImageButton>(R.id.select_all)
        selectAllButton.setOnClickListener { model.selectAllCurrent() }
        val loadButton = findViewById<ImageButton>(R.id.load_files)
        loadButton.setOnClickListener {
            sendPaths(model.loadFiles())
        }

        val adapter = FileChooserAdapter(currentDirs)
        adapter.setListener(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
    }

    private val currentDirsObserver = object : Observer<ArrayList<FileTreeComponent>> {
        override fun onChanged(t: ArrayList<FileTreeComponent>?) {
            if (t == null)
                return

            if (model.wasSelectedOneFile) {
                currentDirs[model.positionSelected] = t[model.positionSelected]
                list.adapter?.notifyItemChanged(model.positionSelected)
            } else {
                currentDirs.clear()
                currentDirs.addAll(t)
                list.adapter?.notifyDataSetChanged()
            }
        }
    }

    private val currentDirNameObserver = Observer<String> { t ->
        val dirNameView = findViewById<TextView>(R.id.dir_name)
        if (t == "")
            dirNameView.text = resources.getString(R.string.home_screen)
        else
            dirNameView.text = t
    }

    private val currentPathObserver = Observer<String> { t ->
        val pathView = findViewById<TextView>(R.id.dir_path)
        pathView.text = t
    }

    private fun sendPaths(paths: ArrayList<String>) {
        intent = Intent()
        intent.putStringArrayListExtra(PATHS, paths)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onClick(position: Int) {
        scope.launch {
            val isDirectory = currentDirs[position].isDirectory
            if (isDirectory) {
                loadingSpinner.visibility = View.VISIBLE
                currentDirs.clear()
                list.adapter?.notifyDataSetChanged()
            }

            model.onClick(position)

            if (isDirectory) {
                loadingSpinner.visibility = View.GONE
                list.scrollToPosition(0)
            }
        }
    }

    override fun onCheckboxClick(position: Int, value: Boolean) {
        model.onCheckboxClick(position, value)
    }


    abstract class FileTreeComponent : Serializable {
        var parent: FileTreeComponent? = null
        var model: FileModel? = null
        var isSelected: Boolean = false
        var hasSelectedChilds = false
        var isInitialized = false
        open var isDirectory = false

        open fun initialize() {}
        open fun checkChilds() {}
        open fun childAt(index: Int): FileTreeComponent? {
            return null
        }

        open fun childsList(): ArrayList<FileTreeComponent>? {
            return null
        }

        open fun changeSelected(value: Boolean) {}
        open fun updateChilds(value: Boolean) {}
    }

    class FileNode(parent: FileTreeComponent, model: FileModel) : FileTreeComponent(),
        Serializable {
        init {
            this.parent = parent
            this.model = model
        }

        override fun changeSelected(value: Boolean) {
            isSelected = value
            parent?.checkChilds()
        }
    }

    class DirectoryNode(parent: FileTreeComponent?, model: FileModel?) : FileTreeComponent(),
        Serializable {
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
            var node: FileTreeComponent // temporary variable

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
            childs.forEach { it.isSelected = value; it.updateChilds(value) }
        }

        override fun childAt(index: Int): FileTreeComponent {
            return childs[index]
        }

        override fun childsList(): ArrayList<FileTreeComponent> {
            initialize()
            return childs
        }

        private fun isAcceptable(file: File): Boolean {
            return (file.isDirectory && !file.name.startsWith('.'))
                    || (!file.isDirectory && file.name.endsWith(".mp3"))
        }
    }

    // file wrapper for custom name
    data class FileModel(val name: String, val file: File) : Serializable
}