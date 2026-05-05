package com.example.a20260310.ui.add

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.data.model.MeetingDraft
import com.example.a20260310.viewmodel.MeetingSessionViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeetingCreateFragment : Fragment(R.layout.fragment_meeting_create) {

    private val sessionViewModel: MeetingSessionViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nameInput = view.findViewById<TextInputEditText>(R.id.nameInput)
        val dateInput = view.findViewById<TextInputEditText>(R.id.dateInput)
        val timeInput = view.findViewById<TextInputEditText>(R.id.timeInput)

        val currentDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        if (nameInput.text.isNullOrEmpty()) {
            nameInput.setText(currentDate)
        }

        dateInput.setText(currentDate)
        timeInput.setText(currentTime)

        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener {
            val meetingName = nameInput.text.toString()

            val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
            prefs.edit().putString("current_meeting_name", meetingName).apply()


            sessionViewModel.setDraft(
                MeetingDraft(
                    title = nameInput.text.toString(),
                    date = dateInput.text.toString(),
                    time = timeInput.text.toString()
                )
            )

            findNavController().navigate(R.id.action_meetingCreateFragment_to_addMethodFragment)
        }
    }
}