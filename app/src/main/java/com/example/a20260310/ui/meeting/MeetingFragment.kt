package com.example.a20260310.ui.meeting

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.a20260310.R
import com.google.android.material.button.MaterialButton

class MeetingFragment : Fragment(R.layout.fragment_meeting) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val status = view.findViewById<TextView>(R.id.status)
        val primaryButton = view.findViewById<MaterialButton>(R.id.primaryButton)

        var isRecording = false
        primaryButton.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                primaryButton.text = "녹음 중지 (더미)"
                status.text = "상태: 녹음중... (더미)"
            } else {
                primaryButton.text = "녹음 시작 (더미)"
                status.text = "상태: 대기중"
            }
        }
    }
}
