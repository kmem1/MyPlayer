package com.kmem.myplayer.feature_playlist.presentation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.kmem.myplayer.R
import com.kmem.myplayer.databinding.FragmentFileChooserBinding
import com.kmem.myplayer.feature_playlist.presentation.adapters.FileChooserAdapter
import com.kmem.myplayer.feature_playlist.presentation.viewmodels.FileChooserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 *  Fragment that allows to browse mp3 files and add them to playlist.
 */

@AndroidEntryPoint
class FileChooserFragment : Fragment(), FileChooserAdapter.Listener {

    private var _binding: FragmentFileChooserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FileChooserViewModel by viewModels()

    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var playlistId: Int = 0
    private var adapter: FileChooserAdapter? = null

    private val args: FileChooserFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileChooserBinding.inflate(inflater, container, false)

        playlistId = args.playlistId

        binding.loadingPb.visibility = View.GONE

        binding.prevPathBtn.setOnClickListener { scope.launch { viewModel.openPreviousDir() } }
        binding.homeBtn.setOnClickListener { viewModel.setHomeDirs() }
        binding.selectAllBtn.setOnClickListener { viewModel.selectAllCurrent() }

        binding.loadFilesBtn.setOnClickListener {
            viewModel.loadFilesToRepository(requireContext(), playlistId)
            val action = FileChooserFragmentDirections.actionFilechooserToPlaylist(playlistId)
            findNavController().navigate(action)
        }

        adapter = FileChooserAdapter(this)
        binding.fileListRv.layoutManager = LinearLayoutManager(requireContext())
        binding.fileListRv.adapter = adapter

        setupToolbar()
        collectDataFromViewModel()

        return binding.root
    }

    private fun setupToolbar() {
        val drawer = activity?.findViewById<DrawerLayout>(R.id.drawer)
        drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun collectDataFromViewModel() {
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentDirs.collectLatest { value ->
                    adapter?.setData(value)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentDirName.collect { value ->
                    if (value == "") {
                        binding.dirNameTv.text = resources.getString(R.string.home_screen)
                    } else {
                        binding.dirNameTv.text = value
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentPath.collect { value ->
                    binding.dirPathTv.text = value
                }
            }
        }
    }

    override fun onListItemClick(position: Int) {
        scope.launch {
            if (viewModel.getDirectoryAtPosition(position).isDirectory) {
                binding.loadingPb.visibility = View.VISIBLE

                viewModel.onListItemClick(position)

                binding.loadingPb.visibility = View.GONE
                binding.fileListRv.scrollToPosition(0)
            } else {
                viewModel.onListItemClick(position)
                binding.fileListRv.adapter?.notifyItemChanged(position)
            }
        }
    }

    override fun onCheckboxClick(position: Int, value: Boolean) {
        viewModel.onCheckboxClick(position, value)
    }
}