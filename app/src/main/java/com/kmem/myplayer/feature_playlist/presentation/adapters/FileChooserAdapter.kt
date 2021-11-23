package com.kmem.myplayer.feature_playlist.presentation.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.databinding.FileListItemBinding
import com.kmem.myplayer.feature_playlist.domain.model.filechooser.FileModel
import com.kmem.myplayer.feature_playlist.domain.model.filechooser.FileTreeComponent

class FileChooserAdapter(private var listener: Listener? = null)
    : RecyclerView.Adapter<FileChooserAdapter.ViewHolder>() {

    private var treeList: List<FileTreeComponent> = emptyList()

    interface Listener {
        fun onListItemClick(position: Int)
        fun onCheckboxClick(position: Int, value: Boolean)
    }

    override fun getItemCount(): Int = treeList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.file_list_item, parent, false)
        val binding = FileListItemBinding.bind(view)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(treeList[position].model!!)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newList: List<FileTreeComponent>) {
        treeList = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: FileListItemBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(model: FileModel) {
            if (model.file.isDirectory) {
                binding.fileIconIv.setImageResource(R.drawable.baseline_folder_24)
            } else {
                binding.fileIconIv.setImageResource(R.drawable.baseline_file_24)
            }

            binding.fileNameTv.text = model.name
            binding.root.setOnClickListener { listener?.onListItemClick(position) }
            binding.checkbox.isChecked = treeList[position].isSelected
            binding.checkbox.setOnClickListener { buttonView ->
                if (buttonView is CheckBox) {
                    listener?.onCheckboxClick(position, buttonView.isChecked)
                }
            }
        }
    }
}