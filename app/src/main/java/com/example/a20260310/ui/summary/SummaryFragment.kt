package com.example.a20260310.ui.summary

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
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
            if (minutes != null) {
                summaryText.text = minutes.summaryText
                bindLines(decisionsLayout, minutes.agenda)
                bindLines(actionItemsLayout, minutes.followup)
            }
        }

        view.findViewById<MaterialButton>(R.id.closeButton).setOnClickListener {
            findNavController().navigate(R.id.action_summaryFragment_to_addCompleteFragment)
        }
    }

    private fun bindLines(container: LinearLayout, source: String) {
        container.removeAllViews()
        val lines = source.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            container.addView(makeLineView("—"))
            return
        }
        lines.forEach { line ->
            container.addView(makeLineView(line))
        }
    }

    private fun makeLineView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
    }
}
