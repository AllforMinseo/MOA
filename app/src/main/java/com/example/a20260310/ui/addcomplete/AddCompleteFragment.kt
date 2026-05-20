package com.example.a20260310.ui.addcomplete

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.google.android.material.button.MaterialButton

class AddCompleteFragment : Fragment(R.layout.fragment_add_complete) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.closeButton).setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
    }
}
