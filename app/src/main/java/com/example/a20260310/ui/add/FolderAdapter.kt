package com.example.a20260310.ui.add

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

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
}