package com.kmem.myplayer.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.ui.Screen

class NavListAdapter(val navItems: ArrayList<Screen>) : RecyclerView.Adapter<NavListAdapter.ViewHolder>() {

    var listener: Listener? = null

    interface Listener {
        fun onNavItemClicked(destId: Int)
    }

    class ViewHolder(val navItemView: LinearLayout) : RecyclerView.ViewHolder(navItemView)

    override fun getItemCount(): Int = navItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val navItemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.nav_item, parent, false) as LinearLayout
        return ViewHolder(navItemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val itemView = holder.navItemView
        val currentScreen = navItems[position]
        val navIconView = itemView.findViewById<ImageView>(R.id.nav_item_icon)
        val navTextView = itemView.findViewById<TextView>(R.id.nav_item_text)

        navIconView.setImageResource(currentScreen.iconResId)
        navTextView.setText(currentScreen.stringResId)

        itemView.setOnClickListener { listener?.onNavItemClicked(currentScreen.dest_id) }
    }

}