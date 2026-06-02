package com.example.a20260310.ui.summary

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class SummarizingFragment : Fragment(R.layout.fragment_summarizing) {
    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!sessionViewModel.hasSelectedFilesForSummary()) {
            Toast.makeText(requireContext(), "요약할 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val progressBar = view.findViewById<LinearProgressIndicator>(R.id.summarizingProgressBar)
        val meetingTitleText = view.findViewById<TextView>(R.id.summarizingMeetingTitle)
        val etaText = view.findViewById<TextView>(R.id.etaText)

        // 이전 요약 결과가 남아 있으면 observe 즉시 트리거로 결과 화면으로 튈 수 있어 먼저 초기화한다.
        sessionViewModel.clearMinutes()
        sessionViewModel.startSummarizePipeline()

        sessionViewModel.summaryProgress.observe(viewLifecycleOwner) { state ->
            progressBar.setProgressCompat(state.progressPercent.coerceIn(0, 100), true)
            meetingTitleText.text = state.meetingTitle
            when {
                state.isRunning && !state.isComplete -> {
                    etaText.visibility = View.VISIBLE
                    etaText.text = formatEtaSeconds(state.etaSecondsRemaining)
                }
                else -> etaText.visibility = View.GONE
            }
        }

        sessionViewModel.minutes.observe(viewLifecycleOwner) { minutes ->
            if (minutes != null &&
                findNavController().currentDestination?.id == R.id.summarizingFragment
            ) {
                findNavController().navigate(R.id.action_summarizingFragment_to_summaryFragment)
            }
        }

        view.findViewById<MaterialButton>(R.id.homeFromSummarizingButton).setOnClickListener {
            findNavController().navigate(R.id.action_summarizingFragment_to_homeFragment)
        }
    }

    private fun formatEtaSeconds(seconds: Long?): String {
        val total = seconds ?: return ""
        val s = total.coerceAtLeast(0L)
        val m = (s / 60).toInt()
        val sec = (s % 60).toInt()
        return getString(R.string.summarizing_eta_remaining, m, sec)
    }
}
