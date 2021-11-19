package com.kmem.myplayer.core.presentation

import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.R
import com.kmem.myplayer.feature_player.service.PlayerService
import com.kmem.myplayer.core.presentation.adapters.NavListAdapter
import com.kmem.myplayer.core.presentation.adapters.NavPlaylistsAdapter
import com.kmem.myplayer.core_data.db.entities.Playlist
import com.kmem.myplayer.core_data.repositories.MusicRepository
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), NavListAdapter.Listener, NavPlaylistsAdapter.Listener,
        CreatePlaylistDialogFragment.CreatePlaylistDialogListener,
        DeletePlaylistConfirmationDialogFragment.DeletePlaylistConfirmationDialogListener {

    interface Repository {
        suspend fun addPlaylist(context: Context, playlistName: String)
        suspend fun getPlaylists(context: Context): ArrayList<Playlist>
        suspend fun deletePlaylist(context: Context, playlist: Playlist)
    }

    var lastOpenedPlaylistId = MyApplication.getCurrentPlaylistIdFromPreferences()

    private lateinit var navList: RecyclerView
    private val navItemList: ArrayList<Screen> = arrayListOf(Screen.Home, Screen.SoundEffects)

    private lateinit var playlistsList: RecyclerView
    private val navPlaylists: ArrayList<Playlist> = ArrayList()

    private val repository: Repository = MusicRepository.getInstance()

    private var serviceConnection: ServiceConnection? = null
    private var playerService: PlayerService? = null
    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navList = findViewById(R.id.nav_list)
        val navListAdapter = NavListAdapter(navItemList)
        navListAdapter.listener = this
        navList.adapter = navListAdapter
        navList.layoutManager = LinearLayoutManager(this)
        navList.isNestedScrollingEnabled = false

        MainScope().launch {
            navPlaylists.addAll(repository.getPlaylists(this@MainActivity))
            playlistsList = findViewById(R.id.playlists_list)
            val navPlaylistsAdapter = NavPlaylistsAdapter(navPlaylists)
            navPlaylistsAdapter.listener = this@MainActivity
            playlistsList.adapter = navPlaylistsAdapter
            playlistsList.layoutManager = LinearLayoutManager(this@MainActivity)
            playlistsList.isNestedScrollingEnabled = false
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                playerServiceBinder = service as PlayerService.PlayerServiceBinder
                playerService = playerServiceBinder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerServiceBinder = null
            }
        }

        bindService(
            Intent(this, PlayerService::class.java),
            serviceConnection!!,
            BIND_AUTO_CREATE
        )

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

    override fun onDeletePlaylistButtonClicked(playlist: Playlist) {
        DeletePlaylistConfirmationDialogFragment(playlist)
            .show(supportFragmentManager, "Delete Playlist")
    }

    override fun onCreatePlaylistDialogPositiveClick(dialog: DialogFragment, playlistName: String) {
        val name = if (playlistName == "") "New Playlist" else playlistName

        MainScope().launch {
            val playlists: ArrayList<Playlist>
            withContext(Dispatchers.IO) {
                repository.addPlaylist(this@MainActivity, name)
                playlists = repository.getPlaylists(this@MainActivity)
            }
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val newPlaylistId = playlists.maxByOrNull { it.playlistId }?.playlistId ?: 1
            if (playlists.size == 1) {
                MyApplication.setCurrentPlaylistIdInPreferences(newPlaylistId)
            }
            navController.popBackStack(R.id.nav_player, false)
            val bundle = bundleOf("playlist_id" to newPlaylistId)
            navController.navigate(R.id.nav_playlist, bundle)
            updateNavPlaylists()
            findViewById<DrawerLayout>(R.id.drawer).closeDrawers()
        }
    }

    override fun onCreatePlaylistDialogNegativeClick(dialog: DialogFragment) { }

    override fun onDeletePlaylistConfirmationPositiveClick(
        dialog: DialogFragment,
        playlist: Playlist
    ) {
        navPlaylists.remove(playlist)
        playlistsList.adapter?.notifyDataSetChanged()

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        if (navController.currentDestination?.id == R.id.nav_playlist) {
            if (navPlaylists.isEmpty()) {
                navController.popBackStack(R.id.nav_player, true)
                navController.navigate(R.id.nav_player)
                MyApplication.setCurrentPlaylistIdInPreferences(-1)
            }
            else if (lastOpenedPlaylistId == playlist.playlistId) {
                navController.popBackStack(R.id.nav_player, false)
                val nextPlaylist = navPlaylists.last()
                val bundle = bundleOf("playlist_id" to nextPlaylist.playlistId)
                navController.navigate(R.id.nav_playlist, bundle)
                if (MyApplication.getCurrentPlaylistIdFromPreferences() == playlist.playlistId)
                    MyApplication.setCurrentPlaylistIdInPreferences(nextPlaylist.playlistId)
            }
        }

        // Current playlist deleted
        if (MyApplication.getCurrentPlaylistIdFromPreferences() == playlist.playlistId) {
            if (navPlaylists.isNotEmpty()) {
                MyApplication.setCurrentPlaylistIdInPreferences(navPlaylists.last().playlistId)
            }
            playerService?.onCurrentPlaylistDeleted()
        }

        MainScope().launch {
            repository.deletePlaylist(this@MainActivity, playlist)
        }
    }

    override fun onDeletePlaylistConfirmationNegativeClick(dialog: DialogFragment) { }

    private fun showCreatePlaylistDialog() {
        CreatePlaylistDialogFragment().show(supportFragmentManager, "Create Playlist")
    }

    private fun updateNavPlaylists() {
        MainScope().launch {
            navPlaylists.clear()
            val playlists: ArrayList<Playlist>
            withContext(Dispatchers.IO) {
                playlists = repository.getPlaylists(this@MainActivity)
            }
            navPlaylists.addAll(playlists)
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
        fun onCreatePlaylistDialogPositiveClick(dialog: DialogFragment, playlistName: String)
        fun onCreatePlaylistDialogNegativeClick(dialog: DialogFragment)
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
                        listener.onCreatePlaylistDialogPositiveClick(this, name)
                    })
                .setNegativeButton(android.R.string.cancel,
                    DialogInterface.OnClickListener { dialog, id ->
                        listener.onCreatePlaylistDialogNegativeClick(this)
                    })

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}

class DeletePlaylistConfirmationDialogFragment(private val playlist: Playlist) : DialogFragment() {

    private lateinit var listener: DeletePlaylistConfirmationDialogListener

    interface DeletePlaylistConfirmationDialogListener {
        fun onDeletePlaylistConfirmationPositiveClick(dialog: DialogFragment, playlist: Playlist)
        fun onDeletePlaylistConfirmationNegativeClick(dialog: DialogFragment)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            listener = context as DeletePlaylistConfirmationDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException((context.toString() +
                    " must implement DeletePlaylistConfirmationDialogListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)

            builder.setTitle(R.string.delete_playlist_confirmation_dialog_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(
                    resources.getString(
                        R.string.delete_playlist_confirmation_dialog_message, playlist.name
                    )
                )
                .setPositiveButton(R.string.yes,
                    DialogInterface.OnClickListener { dialog, id ->
                        listener.onDeletePlaylistConfirmationPositiveClick(this, playlist)
                    })
                .setNegativeButton(R.string.no,
                    DialogInterface.OnClickListener { dialog, id ->
                        listener.onDeletePlaylistConfirmationNegativeClick(this)
                    })

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}