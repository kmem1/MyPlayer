package com.kmem.myplayer.feature_playlist.presentation.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.feature_playlist.domain.model.filechooser.FileTreeComponent

class FileChooserAdapter(private var listener: Listener? = null)
    : RecyclerView.Adapter<FileChooserAdapter.ViewHolder>() {

    private var treeList: List<FileTreeComponent> = emptyList()

    interface Listener {
        fun onListItemClick(position: Int)
        fun onCheckboxClick(position: Int, value: Boolean)
    }

    inner class ViewHolder(val fileView: LinearLayout) : RecyclerView.ViewHolder(fileView)

    override fun getItemCount(): Int = treeList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val fileView = LayoutInflater.from(parent.context)
                .inflate(R.layout.file_list_item, parent, false) as LinearLayout
        return ViewHolder(fileView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fileView = holder.fileView
        val fileIcon = fileView.findViewById<View>(R.id.file_icon) as ImageView
        val fileModel = treeList[position].model!!

        if (fileModel.file.isDirectory)
            fileIcon.setImageResource(R.drawable.baseline_folder_24)
        else
            fileIcon.setImageResource(R.drawable.baseline_file_24)

        val fileName = fileView.findViewById<View>(R.id.file_name) as TextView
        fileName.text = fileModel.name
        fileView.setOnClickListener { listener?.onListItemClick(position) }
        val checkbox = fileView.findViewById<CheckBox>(R.id.checkbox)
        checkbox.isChecked = treeList[position].isSelected
        checkbox.setOnClickListener { buttonView ->
            if (buttonView is CheckBox) {
                listener?.onCheckboxClick(position, buttonView.isChecked)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newList: List<FileTreeComponent>) {
        treeList = newList
        notifyDataSetChanged()
    }
}