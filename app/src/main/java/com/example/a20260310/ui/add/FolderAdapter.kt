package com.example.a20260310.ui.add

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.example.a20260310.R


class FolderAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    private val items = mutableListOf<String>()
    private var selected: String? = null

    fun submitList(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(val btn: MaterialButton) : RecyclerView.ViewHolder(btn)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val btn = MaterialButton(parent.context)
        btn.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return ViewHolder(btn)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = items[position]

        holder.btn.text = folder

        holder.btn.setOnClickListener {
            selected = folder
            notifyDataSetChanged()
            onClick(folder)
        }

        // 선택 표시
        holder.btn.alpha = if (folder == selected) 1.0f else 0.5f
    }

    override fun getItemCount() = items.size



sealed class FolderItem {
    data class Folder(val name: String) : FolderItem()
    object Add : FolderItem()
}
class FolderAdapter(
    private val onFolderClick: (String) -> Unit,
    private val onAddClick: () -> Unit,
    private val onFolderLongClick: (String) -> Unit //for deleting folders
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FolderItem>()
    private var selected: String? = null

    fun submitList(folders: List<String>) {
        items.clear()
        items.addAll(folders.map { FolderItem.Folder(it) })
        items.add(FolderItem.Add) // 🔥 마지막에 추가 버튼
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FolderItem.Folder -> 0
            FolderItem.Add -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == 0) {
            val view = inflater.inflate(R.layout.item_folder, parent, false)
            FolderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_add_folder, parent, false)
            AddViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        when (val item = items[position]) {

            is FolderItem.Folder -> {
                (holder as FolderViewHolder).bind(item.name)
            }

            FolderItem.Add -> {
                (holder as AddViewHolder).bind()
            }
        }
    }

    override fun getItemCount() = items.size

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(name: String) {

            val title = itemView.findViewById<TextView>(R.id.folderName)
            title.text = name

            itemView.setOnClickListener {
                selected = name
                notifyDataSetChanged()
                onFolderClick(name)
            }
            itemView.setOnLongClickListener {
                onFolderLongClick(name)
                true
            }

            itemView.alpha = if (name == selected) 1.0f else 0.6f
        }
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            itemView.setOnClickListener {
                onAddClick()
            }
        }
    }
}