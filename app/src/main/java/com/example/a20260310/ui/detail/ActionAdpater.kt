package com.example.a20260310.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.ActionItem

class ActionAdapter(
    private val items: MutableList<ActionItem>,
    private val onEdit: (ActionItem, Int) -> Unit,
    private val onDelete: (ActionItem, Int) -> Unit,
) : RecyclerView.Adapter<ActionAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.taskTitle)
        val owner: TextView = view.findViewById(R.id.taskOwner)
        val deadline: TextView = view.findViewById(R.id.taskDeadline)
        val editBtn: ImageButton = view.findViewById(R.id.btnEditAction)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDeleteAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.owner.text = item.owner
        holder.deadline.text = item.deadline
        holder.editBtn.setOnClickListener { onEdit(item, position) }
        holder.deleteBtn.setOnClickListener { onDelete(item, position) }
    }

    override fun getItemCount(): Int = items.size

    fun addItem(item: ActionItem) {
        items.add(item)
        notifyItemInserted(items.lastIndex)
    }

    fun updateItem(position: Int, updated: ActionItem) {
        items[position] = updated
        notifyItemChanged(position)
    }

    fun removeItem(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun replaceAll(newItems: List<ActionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun currentItems(): List<ActionItem> = items.toList()
}