package com.kmem.myplayer.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.ui.fragments.FileChooserFragment

/**
 *  Адаптер для элемента RecyclerView в FileChooserFragment.
 *  Определяет поведение RecyclerView.
 *  Отвечает за заполнение элемента списка информацией.
 *  Отвечает за взаимодействие элементов списка с пользователем.
 *
 *  Аргументы:
 *      ArrayList с деревьями файлов.
 */

class FileChooserAdapter(private val treeList: ArrayList<FileChooserFragment.FileTreeComponent>)
    : RecyclerView.Adapter<FileChooserAdapter.ViewHolder>() {

    var listener: Listener? = null

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
}