package com.kmem.myplayer.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.drawerlayout.widget.DrawerLayout
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
import com.kmem.myplayer.ui.adapters.NavListAdapter
import com.kmem.myplayer.ui.adapters.NavPlaylistsAdapter
import com.kmem.myplayer.ui.fragments.MainPlayerFragment
import com.kmem.myplayer.ui.fragments.PlaylistFragment

/**
 *  Главная активность приложения. Она запускается при открытии приложения.
 *  Отвечает за запуск остальных экранов приложения.
 */

class MainActivity : AppCompatActivity(), NavListAdapter.Listener, NavPlaylistsAdapter.Listener {

    private val navItemList: ArrayList<Screen> = arrayListOf(Screen.Home, Screen.SoundEffects)
    private val navPlaylists: ArrayList<Playlist> = arrayListOf(
        Playlist(1, "Playlist 1"),
        Playlist(2, "Playlist 2"),
        Playlist(3, "Playlist 3"),
        Playlist(4, "Playlist 4"),
        Playlist(1, "Playlist 1"),
        Playlist(2, "Playlist 2"),
        Playlist(3, "Playlist 3"),
        Playlist(4, "Playlist 4"),
        Playlist(1, "Playlist 1"),
        Playlist(2, "Playlist 2"),
        Playlist(3, "Playlist 3"),
        Playlist(4, "Playlist 4"),
        Playlist(1, "Playlist 1"),
        Playlist(2, "Playlist 2"),
        Playlist(3, "Playlist 3"),
        Playlist(4, "Playlist 4"),
        Playlist(1, "Playlist 1"),
        Playlist(2, "Playlist 2"),
        Playlist(3, "Playlist 3"),
        Playlist(4, "Playlist 4"),
        Playlist(1, "Playlist 1"),
        Playlist(2, "Playlist 2"),
        Playlist(3, "Playlist 3"),
        Playlist(4, "Playlist 4")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navList = findViewById<RecyclerView>(R.id.nav_list)
        val navListAdapter = NavListAdapter(navItemList)
        navListAdapter.listener = this
        navList.adapter = navListAdapter
        navList.layoutManager = LinearLayoutManager(this)
        navList.isNestedScrollingEnabled = false

        val playlistsList = findViewById<RecyclerView>(R.id.playlists_list)
        val navPlaylistsAdapter = NavPlaylistsAdapter(navPlaylists)
        navPlaylistsAdapter.listener = this
        playlistsList.adapter = navPlaylistsAdapter
        playlistsList.layoutManager = LinearLayoutManager(this)
        playlistsList.isNestedScrollingEnabled = false

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

}

sealed class Screen(
    val dest_id: Int,
    @StringRes val stringResId: Int,
    @DrawableRes val iconResId: Int
) {
    object Home : Screen(R.id.nav_player, R.string.nav_home, R.drawable.baseline_home_24)
    object SoundEffects : Screen(R.id.nav_effects, R.string.nav_effects, R.drawable.baseline_play_arrow_24)
}

data class Playlist(val id: Int, val name: String)