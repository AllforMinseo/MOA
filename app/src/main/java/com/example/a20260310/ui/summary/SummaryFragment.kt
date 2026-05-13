package com.example.a20260310.ui.summary

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.data.model.ActionItem
import com.example.a20260310.data.model.DecisionItem
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.google.android.material.button.MaterialButton

class SummaryFragment : Fragment(R.layout.fragment_summary) {
    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionViewModel.dismissSummaryProgressPanel()

        val summaryText = view.findViewById<TextView>(R.id.tvSummary)
        val decisionsLayout = view.findViewById<LinearLayout>(R.id.layoutDecisions)
        val actionItemsLayout = view.findViewById<LinearLayout>(R.id.layoutActionItems)

        sessionViewModel.minutes.observe(viewLifecycleOwner) { minutes ->
            minutes ?: return@observe
            val summary = minutes.summary

            summaryText.text = summary.summary.trim().ifBlank { "—" }
            bindDecisions(decisionsLayout, summary.decisions)
            bindActionItems(actionItemsLayout, summary.actionItems)
        }

        view.findViewById<MaterialButton>(R.id.closeButton).setOnClickListener {
            findNavController().navigate(R.id.action_summaryFragment_to_addCompleteFragment)
        }
    }

    private fun bindDecisions(container: LinearLayout, items: List<DecisionItem>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(makeLineView("—"))
            return
        }

        items.forEach { item ->
            val content = item.content.trim().ifBlank { "—" }
            container.addView(makeLineView("• $content"))
        }
    }

    private fun bindActionItems(container: LinearLayout, items: List<ActionItem>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(makeLineView("—"))
            return
        }

        items.forEach { item ->
            container.addView(makeLineView(formatActionItem(item)))
        }
    }

    private fun formatActionItem(item: ActionItem): String {
        val task = item.title.trim().ifBlank { "—" }
        val owner = item.owner.trim().ifBlank { "미정" }
        val deadline = item.deadline.trim().ifBlank { "미정" }
        return "• $task (담당: $owner / 마감: $deadline)"
    }

    private fun makeLineView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
    }
}