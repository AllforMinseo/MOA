package com.example.a20260310.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.DecisionItem

class DecisionAdapter(
    private val items: MutableList<DecisionItem>,
    private val onEdit: (DecisionItem, Int) -> Unit,
    private val onDelete: (DecisionItem, Int) -> Unit,
) : RecyclerView.Adapter<DecisionAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.decisionText)
        val editBtn: ImageButton = view.findViewById(R.id.btnEditDecision)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDeleteDecision)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_decision, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.text.text = item.content
        holder.editBtn.setOnClickListener { onEdit(item, position) }
        holder.deleteBtn.setOnClickListener { onDelete(item, position) }
    }

    override fun getItemCount(): Int = items.size

    fun addItem(item: DecisionItem) {
        items.add(item)
        notifyItemInserted(items.lastIndex)
    }

    fun updateItem(position: Int, newText: String) {
        items[position].content = newText
        notifyItemChanged(position)
    }

    fun removeItem(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun replaceAll(newItems: List<DecisionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun currentItems(): List<DecisionItem> = items.toList()
}
