package com.example.a20260310.ui.home

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.ui.common.SimpleRowAdapter
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var recycler: RecyclerView
    private lateinit var folderTabs: LinearLayout
    private var selectedFolder: String = "전체"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.recycler)
        folderTabs = view.findViewById(R.id.folderTabs)

        recycler.layoutManager = LinearLayoutManager(context)

        setupFolderTabs()
        loadList()

        view.findViewById<MaterialButton>(R.id.addMeetingButton).setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_folderSelectFragment)
        }
    }

    private fun setupFolderTabs() {
        folderTabs.removeAllViews()

        val folders = mutableListOf("전체")
        folders.addAll(getFolderNames(requireContext()))

        folders.forEach { name ->
            val btn = createFolderButton(name)
            folderTabs.addView(btn)
        }

        updateTabs()
    }

    private fun createFolderButton(name: String): MaterialButton {
        val btn = MaterialButton(requireContext())

        btn.text = name

        btn.setPadding(40, 16, 40, 16)

        btn.strokeWidth = 2
        btn.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.moa_line)

        btn.setBackgroundColor(resources.getColor(R.color.moa_bg, null))
        btn.setTextColor(resources.getColor(R.color.moa_orange_soft, null))

        btn.setOnClickListener {
            selectedFolder = name
            updateTabs()
            loadList()
        }

        return btn
    }

    private fun updateTabs() {
        for (i in 0 until folderTabs.childCount) {
            val btn = folderTabs.getChildAt(i) as MaterialButton

            if (btn.text == selectedFolder) {
                btn.setBackgroundColor(resources.getColor(R.color.moa_orange, null))
                btn.setTextColor(resources.getColor(android.R.color.white, null))
            } else {
                btn.setBackgroundColor(resources.getColor(R.color.moa_bg, null))
                btn.setTextColor(resources.getColor(R.color.moa_orange_soft, null))
            }
        }
    }

    private fun loadList() {
        val items = if (selectedFolder == "전체") {
            getAllFiles(requireContext())
        } else {
            getFilesInFolder(requireContext(), selectedFolder)
        }

        recycler.adapter = SimpleRowAdapter(items)
    }
}

fun getFolderNames(context: Context): List<String> {
    val prefs = context.getSharedPreferences("moa_prefs", 0)
    return prefs.getStringSet("folder_list", setOf())?.toList() ?: emptyList()
}

fun getFilesInFolder(context: Context, folderName: String): List<SimpleRow> {

    val folder = File(context.filesDir, "MOA/$folderName")
    val prefs = context.getSharedPreferences("moa_prefs", 0)

    return folder.listFiles()
        ?.filter { it.name.endsWith(".m4a") }
        ?.sortedByDescending { it.lastModified() }
        ?.map {
            val title = prefs.getString(it.name, it.name)
            val date = SimpleDateFormat("yyyy년 M월 d일 a HH:mm", Locale.KOREA)
                .format(Date(it.lastModified()))

            SimpleRow(title ?: it.name, date)
        } ?: emptyList()
}

fun getAllFiles(context: Context): List<SimpleRow> {

    val baseDir = File(context.filesDir, "MOA")
    val prefs = context.getSharedPreferences("moa_prefs", 0)

    if (!baseDir.exists()) return emptyList()

    return baseDir.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".m4a") }
        .sortedByDescending { it.lastModified() }
        .map {
            val title = prefs.getString(it.name, it.name)
            val date = SimpleDateFormat("yyyy년 M월 d일 a HH:mm", Locale.KOREA)
                .format(Date(it.lastModified()))

            SimpleRow(title ?: it.name, date)
        }.toList()
}