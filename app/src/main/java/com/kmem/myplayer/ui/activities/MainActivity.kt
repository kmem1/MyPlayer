package com.kmem.myplayer.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.kmem.myplayer.ui.fragments.MainPlayerFragment
import com.kmem.myplayer.ui.fragments.PlaylistFragment
import com.kmem.myplayer.R
import com.kmem.myplayer.service.PlayerService

/**
 *  Главная активность приложения. Она запускается при открытии приложения.
 *  Отвечает за запуск остальных экранов приложения.
 */

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pagerAdapter = SectionsPagerAdapter(supportFragmentManager)
        val viewPager = findViewById<View>(R.id.pager) as ViewPager
        viewPager.adapter = pagerAdapter
        startService(Intent(baseContext, PlayerService::class.java))
    }

    private inner class SectionsPagerAdapter(fm: FragmentManager?) :
        FragmentPagerAdapter(fm!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int {
            return 2
        }

        override fun getItem(position: Int): Fragment {
            when (position) {
                0 -> return MainPlayerFragment()
                1 -> return PlaylistFragment()
            }
            return MainPlayerFragment()
        }

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return "main"
                1 -> return "Playlist"
            }
            return null
        }
    }
}