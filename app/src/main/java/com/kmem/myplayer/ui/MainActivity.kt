package com.kmem.myplayer.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.navigation.NavigationView
import com.kmem.myplayer.R
import com.kmem.myplayer.data.AppDatabase
import com.kmem.myplayer.data.MusicRepository
import com.kmem.myplayer.data.Playlist
import com.kmem.myplayer.ui.adapters.NavListAdapter
import com.kmem.myplayer.ui.adapters.NavPlaylistsAdapter
import com.kmem.myplayer.ui.fragments.MainPlayerFragment
import com.kmem.myplayer.ui.fragments.PlaylistFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ClassCastException
import java.lang.IllegalStateException

/**
 *  Главная активность приложения. Она запускается при открытии приложения.
 *  Отвечает за запуск остальных экранов приложения.
 */

class MainActivity : AppCompatActivity(), NavListAdapter.Listener, NavPlaylistsAdapter.Listener,
                                        CreatePlaylistDialogFragment.CreatePlaylistDialogListener {

    interface Repository {
        suspend fun addPlaylist(context: Context, playlistName: String)
        suspend fun getPlaylists(context: Context): ArrayList<Playlist>
    }

    private lateinit var navList: RecyclerView
    private val navItemList: ArrayList<Screen> = arrayListOf(Screen.Home, Screen.SoundEffects)

    private lateinit var playlistsList: RecyclerView
    private val navPlaylists: ArrayList<Playlist> = ArrayList()

    private val repository: Repository = MusicRepository.getInstance(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navList = findViewById<RecyclerView>(R.id.nav_list)
        val navListAdapter = NavListAdapter(navItemList)
        navListAdapter.listener = this
        navList.adapter = navListAdapter
        navList.layoutManager = LinearLayoutManager(this)
        navList.isNestedScrollingEnabled = false


        MainScope().launch {
            navPlaylists.addAll(repository.getPlaylists(this@MainActivity))
            playlistsList = findViewById<RecyclerView>(R.id.playlists_list)
            val navPlaylistsAdapter = NavPlaylistsAdapter(navPlaylists)
            navPlaylistsAdapter.listener = this@MainActivity
            playlistsList.adapter = navPlaylistsAdapter
            playlistsList.layoutManager = LinearLayoutManager(this@MainActivity)
            playlistsList.isNestedScrollingEnabled = false
        }

        findViewById<Button>(R.id.create_playlist_button).setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    override fun onNavItemClicked(destId: Int) {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        val inclusive = destId == R.id.nav_player
        navController.popBackStack(R.id.nav_player, inclusive)
        navController.navigate(destId)
        findViewById<DrawerLayout>(R.id.drawer).closeDrawers()
    }

    override fun onPlaylistClicked(playlistId: Int) {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.popBackStack(R.id.nav_player, false)
        val bundle = bundleOf("playlist_id" to playlistId)
        navController.navigate(R.id.nav_playlist, bundle)
        findViewById<DrawerLayout>(R.id.drawer).closeDrawers()
    }

    override fun onDialogPositiveClick(dialog: DialogFragment, playlistName: String) {
        val name = if (playlistName == "") "New Playlist" else playlistName

        MainScope().launch {
            repository.addPlaylist(this@MainActivity, name)
            updateNavPlaylists()
        }
    }

    override fun onDialogNegativeClick(dialog: DialogFragment) { }

    private fun showCreatePlaylistDialog() {
        CreatePlaylistDialogFragment().show(supportFragmentManager, "Create Playlist")
    }

    private fun updateNavPlaylists() {
        MainScope().launch {
            navPlaylists.clear()
            navPlaylists.addAll(repository.getPlaylists(this@MainActivity))
            playlistsList.adapter?.notifyDataSetChanged()
        }
    }

}

sealed class Screen(
    val dest_id: Int,
    @StringRes val stringResId: Int,
    @DrawableRes val iconResId: Int
) {
    object Home : Screen(R.id.nav_player, R.string.nav_home, R.drawable.baseline_home_24)
    object SoundEffects : Screen(R.id.nav_effects, R.string.nav_effects, R.drawable.baseline_play_arrow_24)
}

class CreatePlaylistDialogFragment : DialogFragment() {

    private lateinit var listener: CreatePlaylistDialogListener

    interface CreatePlaylistDialogListener {
        fun onDialogPositiveClick(dialog: DialogFragment, playlistName: String)
        fun onDialogNegativeClick(dialog: DialogFragment)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            listener = context as CreatePlaylistDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException((context.toString() +
                    " must implement CreatePlaylistDialogListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)

            val view = requireActivity().layoutInflater.inflate(
                R.layout.dialog_create_playlist,
                null
            )

            builder.setView(view)
                .setTitle(R.string.create_playlist_dialog_title)
                .setPositiveButton(android.R.string.ok,
                    DialogInterface.OnClickListener { dialog, id ->
                        val name = view.findViewById<EditText>(R.id.playlist_name).text.toString()
                        listener.onDialogPositiveClick(this, name)
                    })
                .setNegativeButton(android.R.string.cancel,
                    DialogInterface.OnClickListener { dialog, id ->
                        listener.onDialogNegativeClick(this)
                    })

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}