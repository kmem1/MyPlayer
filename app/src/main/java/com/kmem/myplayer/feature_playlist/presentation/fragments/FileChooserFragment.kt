package com.kmem.myplayer.feature_playlist.presentation.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.feature_playlist.presentation.adapters.FileChooserAdapter
import com.kmem.myplayer.feature_playlist.presentation.viewmodels.FileChooserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.Serializable

/**
 *  Fragment that allows to browse mp3 files and add them to playlist.
 */
class FileChooserFragment : Fragment(), FileChooserAdapter.Listener {

    private val currentDirs: ArrayList<FileTreeComponent> = ArrayList()
    private val viewModel: FileChooserViewModel by viewModels()

    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var playlistId: Int = 0
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var layout: View
    private lateinit var mActivity: AppCompatActivity
    private lateinit var list: RecyclerView

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = context as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        layout = inflater.inflate(R.layout.fragment_file_chooser, container, false)

        viewModel.currentDirs.observe(viewLifecycleOwner, currentDirsObserver)
        viewModel.currentDirName.observe(viewLifecycleOwner, currentDirNameObserver)
        viewModel.currentPath.observe(viewLifecycleOwner, currentPathObserver)

        loadingSpinner = layout.findViewById(R.id.progress_bar)
        loadingSpinner.visibility = View.GONE

        playlistId = arguments?.getInt("playlist_id") ?: 0

        val previousPathButton = layout.findViewById<ImageButton>(R.id.prev_path_button)
        previousPathButton.setOnClickListener { scope.launch { viewModel.openPreviousDir() } }
        val homeButton = layout.findViewById<ImageButton>(R.id.home_button)
        homeButton.setOnClickListener { viewModel.setHomeDirs() }
        val selectAllButton = layout.findViewById<ImageButton>(R.id.select_all)
        selectAllButton.setOnClickListener { viewModel.selectAllCurrent() }
        val loadButton = layout.findViewById<ImageButton>(R.id.load_files)

        loadButton.setOnClickListener {
            viewModel.loadFilesToRepository(playlistId)
            val action = FileChooserFragmentDirections.actionFilechooserToPlaylist(playlistId)
            findNavController().navigate(action)
        }

        list = layout.findViewById(R.id.fileList)
        val adapter = FileChooserAdapter(currentDirs)
        adapter.listener = this
        list.layoutManager = LinearLayoutManager(mActivity)
        list.adapter = adapter

        setupToolbar()

        return layout
    }

    private fun setupToolbar() {
        val toolbar = layout.findViewById<Toolbar>(R.id.toolbar)
        val drawer = activity?.findViewById<DrawerLayout>(R.id.drawer)
        drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private val currentDirsObserver = object : Observer<ArrayList<FileTreeComponent>> {
        override fun onChanged(t: ArrayList<FileTreeComponent>?) {
            if (t == null)
                return

            if (viewModel.wasSelectedOneFile) {
                currentDirs[viewModel.positionSelected] = t[viewModel.positionSelected]
                list.adapter?.notifyItemChanged(viewModel.positionSelected)
            } else {
                currentDirs.clear()
                currentDirs.addAll(t)
                list.adapter?.notifyDataSetChanged()
            }
        }
    }

    private val currentDirNameObserver = Observer<String> { t ->
        val dirNameView = layout.findViewById<TextView>(R.id.dir_name)
        if (t == "")
            dirNameView.text = resources.getString(R.string.home_screen)
        else
            dirNameView.text = t
    }

    private val currentPathObserver = Observer<String> { t ->
        val pathView = layout.findViewById<TextView>(R.id.dir_path)
        pathView.text = t
    }

    override fun onListItemClick(position: Int) {
        scope.launch {
            if (currentDirs[position].isDirectory) {
                loadingSpinner.visibility = View.VISIBLE
                currentDirs.clear()
                list.adapter?.notifyDataSetChanged()

                viewModel.onListItemClick(position)

                loadingSpinner.visibility = View.GONE
                list.scrollToPosition(0)
            } else {
                viewModel.onListItemClick(position)
            }
        }
    }

    override fun onCheckboxClick(position: Int, value: Boolean) {
        viewModel.onCheckboxClick(position, value)
    }


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
                if (isAcceptable(it))
                    childModels.add(FileModel(it.name, it))
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

    // file wrapper for custom name
    data class FileModel(val name: String, val file: File) : Serializable

}