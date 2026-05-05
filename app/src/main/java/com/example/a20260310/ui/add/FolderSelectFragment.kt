package com.example.a20260310.ui.add

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.a20260310.ui.recording.getOrCreateFolder

class FolderSelectFragment : Fragment(R.layout.fragment_folder_select) {

    private lateinit var adapter: FolderAdapter
    private var selectedFolder: String = "전체"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.folderRecycler)
        recycler.layoutManager = LinearLayoutManager(context)

        adapter = FolderAdapter { folder ->
            selectedFolder = folder
        }

        recycler.adapter = adapter

        loadFolders()

        view.findViewById<View>(R.id.addFolderCard).setOnClickListener {
            showAddFolderDialog()
        }

        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener {

            if (selectedFolder == null) return@setOnClickListener

            val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
            prefs.edit().putString("selected_folder", selectedFolder).apply()

            findNavController().navigate(
                R.id.action_folderSelectFragment_to_meetingCreateFragment
            )
        }
    }

    private fun loadFolders() {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val folders = prefs.getStringSet("folder_list", setOf()) ?: setOf()

        adapter.submitList(folders.toList())
    }

    private fun showAddFolderDialog() {
        val editText = EditText(requireContext())

        AlertDialog.Builder(requireContext())
            .setTitle("폴더 추가")
            .setView(editText)
            .setPositiveButton("추가") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveFolder(name)
                    loadFolders() // 🔥 추가 후 즉시 반영
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveFolder(name: String) {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val set = prefs.getStringSet("folder_list", mutableSetOf())!!.toMutableSet()
        set.add(name)
        prefs.edit().putStringSet("folder_list", set).apply()

        getOrCreateFolder(requireContext(), name)
    }
}