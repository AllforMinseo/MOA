package com.example.a20260310.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.ActionItem
import com.example.a20260310.data.model.DecisionItem
import com.example.a20260310.data.model.MeetingFileRow
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Locale

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var isSummaryTab = true
    private val gson = Gson()

    private lateinit var decisionAdapter: DecisionAdapter
    private lateinit var actionAdapter: ActionAdapter

    private val meetingTitle: String
        get() = arguments?.getString("meetingTitle") ?: "회의"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val summaryBtn = view.findViewById<MaterialButton>(R.id.tabSummary)
        val fileBtn = view.findViewById<MaterialButton>(R.id.tabFiles)
        val fileRecycler = view.findViewById<RecyclerView>(R.id.fileRecycler)
        val summaryScroll = view.findViewById<ScrollView>(R.id.summaryScroll)

        val summaryText = view.findViewById<TextView>(R.id.summaryText)
        val btnEditSummary = view.findViewById<View>(R.id.btnEditSummary)

        val decisionRecycler = view.findViewById<RecyclerView>(R.id.decisionRecycler)
        val actionRecycler = view.findViewById<RecyclerView>(R.id.actionRecycler)
        val btnAddDecision = view.findViewById<View>(R.id.btnAddDecision)
        val btnAddAction = view.findViewById<View>(R.id.btnAddAction)

        title.text = meetingTitle

        fileRecycler.layoutManager = LinearLayoutManager(requireContext())
        decisionRecycler.layoutManager = LinearLayoutManager(requireContext())
        actionRecycler.layoutManager = LinearLayoutManager(requireContext())

        bindSummary(summaryText)
        btnEditSummary.setOnClickListener { showEditSummaryDialog(summaryText) }

        setupDecisionRecycler(decisionRecycler)
        setupActionRecycler(actionRecycler)

        btnAddDecision.setOnClickListener { showAddDecisionDialog() }
        btnAddAction.setOnClickListener { showAddActionDialog() }

        updateTabs(summaryBtn, fileBtn)
        showSummary(summaryScroll, fileRecycler)

        summaryBtn.setOnClickListener {
            isSummaryTab = true
            updateTabs(summaryBtn, fileBtn)
            showSummary(summaryScroll, fileRecycler)
        }

        fileBtn.setOnClickListener {
            isSummaryTab = false
            updateTabs(summaryBtn, fileBtn)
            showFiles(summaryScroll, fileRecycler)
        }

        setupDeleteMenu()
    }

    private fun bindSummary(summaryText: TextView) {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        summaryText.text = prefs.getString("${meetingTitle}_summary", "요약 없음")
    }

    private fun showEditSummaryDialog(summaryTextView: TextView) {
        val editText = EditText(requireContext())
        editText.setText(summaryTextView.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("회의 요약 수정")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val text = editText.text.toString().trim().ifBlank { "요약 없음" }
                requireContext()
                    .getSharedPreferences("moa_prefs", 0)
                    .edit()
                    .putString("${meetingTitle}_summary", text)
                    .apply()
                summaryTextView.text = text
                Toast.makeText(requireContext(), "저장됨", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setupDecisionRecycler(recyclerView: RecyclerView) {
        decisionAdapter = DecisionAdapter(
            items = loadDecisions().toMutableList(),
            onEdit = { item, position -> showEditDecisionDialog(item, position) },
            onDelete = { _, position -> showDeleteDecisionDialog(position) },
        )
        recyclerView.adapter = decisionAdapter
    }

    private fun setupActionRecycler(recyclerView: RecyclerView) {
        actionAdapter = ActionAdapter(
            items = loadActions().toMutableList(),
            onEdit = { item, position -> showEditActionDialog(item, position) },
            onDelete = { _, position -> showDeleteActionDialog(position) },
        )
        recyclerView.adapter = actionAdapter
    }

    private fun showAddDecisionDialog() {
        val editText = EditText(requireContext())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("결정 사항 추가")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    decisionAdapter.addItem(
                        DecisionItem(
                            id = 0,
                            meetingId = 0,
                            content = text,
                        )
                    )
                    saveDecisions()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditDecisionDialog(item: DecisionItem, position: Int) {
        val editText = EditText(requireContext())
        editText.setText(item.content)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("결정 사항 수정")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    decisionAdapter.updateItem(position, text)
                    saveDecisions()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteDecisionDialog(position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("이 결정 사항을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                decisionAdapter.removeItem(position)
                saveDecisions()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddActionDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_action_item, null, false)

        val titleInput = dialogView.findViewById<EditText>(R.id.editTaskTitle)
        val ownerInput = dialogView.findViewById<EditText>(R.id.editTaskOwner)
        val deadlineInput = dialogView.findViewById<EditText>(R.id.editTaskDeadline)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("할 일 추가")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val titleText = titleInput.text.toString().trim()
                val owner = ownerInput.text.toString().trim()
                val deadline = deadlineInput.text.toString().trim()

                if (titleText.isNotEmpty()) {
                    actionAdapter.addItem(
                        ActionItem(
                            id = 0,
                            meetingId = 0,
                            title = titleText,
                            owner = owner,
                            deadline = deadline,
                        )
                    )
                    saveActions()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditActionDialog(item: ActionItem, position: Int) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_action_item, null, false)

        val titleInput = dialogView.findViewById<EditText>(R.id.editTaskTitle)
        val ownerInput = dialogView.findViewById<EditText>(R.id.editTaskOwner)
        val deadlineInput = dialogView.findViewById<EditText>(R.id.editTaskDeadline)

        titleInput.setText(item.title)
        ownerInput.setText(item.owner)
        deadlineInput.setText(item.deadline)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("할 일 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val updated = item.copy(
                    title = titleInput.text.toString().trim(),
                    owner = ownerInput.text.toString().trim(),
                    deadline = deadlineInput.text.toString().trim(),
                )
                if (updated.title.isNotEmpty()) {
                    actionAdapter.updateItem(position, updated)
                    saveActions()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteActionDialog(position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("이 할 일을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                actionAdapter.removeItem(position)
                saveActions()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveDecisions() {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        prefs.edit()
            .putString("${meetingTitle}_decisions_json", gson.toJson(decisionAdapter.currentItems()))
            .apply()
    }

    private fun loadDecisions(): List<DecisionItem> {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val key = "${meetingTitle}_decisions_json"
        val json = prefs.getString(key, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<DecisionItem>>() {}.type
            gson.fromJson<List<DecisionItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            prefs.edit().remove(key).apply()
            emptyList()
        }
    }

    private fun saveActions() {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        prefs.edit()
            .putString("${meetingTitle}_actions_json", gson.toJson(actionAdapter.currentItems()))
            .apply()
    }

    private fun loadActions(): List<ActionItem> {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val key = "${meetingTitle}_actions_json"
        val json = prefs.getString(key, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<ActionItem>>() {}.type
            gson.fromJson<List<ActionItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            prefs.edit().remove(key).apply()
            emptyList()
        }
    }

    private fun setupDeleteMenu() {
        val menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_delete -> {
                        Toast.makeText(requireContext(), "회의 삭제 TODO", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
        }

        requireActivity().addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun updateTabs(summaryBtn: MaterialButton, fileBtn: MaterialButton) {
        if (isSummaryTab) {
            fileBtn.setBackgroundColor(resources.getColor(R.color.color_on_primary, null))
            fileBtn.setTextColor(resources.getColor(android.R.color.black, null))
            summaryBtn.setBackgroundColor(resources.getColor(R.color.color_primary, null))
            summaryBtn.setTextColor(resources.getColor(R.color.color_on_primary, null))
        } else {
            summaryBtn.setBackgroundColor(resources.getColor(R.color.color_on_primary, null))
            summaryBtn.setTextColor(resources.getColor(android.R.color.black, null))
            fileBtn.setBackgroundColor(resources.getColor(R.color.color_primary, null))
            fileBtn.setTextColor(resources.getColor(R.color.color_on_primary, null))
        }
    }

    private fun showSummary(summaryScroll: View, recycler: RecyclerView) {
        summaryScroll.visibility = View.VISIBLE
        recycler.visibility = View.GONE
    }

    private fun showFiles(summaryScroll: View, recycler: RecyclerView) {
        summaryScroll.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        val files = loadFiles()

        recycler.adapter = MeetingFileAdapter(
            items = files,
            onOpenClick = { file -> openFile(file) }
        )
    }

    private fun loadFiles(): List<MeetingFileRow> {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val jsonKey = "${meetingTitle}_files_json"
        val json = prefs.getString(jsonKey, null)

        if (!json.isNullOrBlank()) {
            return try {
                val type = object : TypeToken<List<MeetingFileRow>>() {}.type
                gson.fromJson<List<MeetingFileRow>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                prefs.edit().remove(jsonKey).apply()
                emptyList()
            }
        }

        val baseDir = requireContext().getExternalFilesDir(null) ?: return emptyList()
        val allowedExt = setOf("m4a", "mp3", "wav", "aac", "mp4", "jpg", "jpeg", "png", "webp", "pdf")

        return baseDir.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.lowercase() in allowedExt }
            .filter { prefs.getString(it.name, "") == meetingTitle }
            .map { file ->
                MeetingFileRow(
                    title = file.name,
                    subtitle = "${file.length() / 1024} KB",
                    path = file.absolutePath,
                    type = detectFileType(file)
                )
            }
            .toList()
    }

    private fun detectFileType(file: File): MeetingFileRow.Type {
        return when (file.extension.lowercase(Locale.getDefault())) {
            "m4a", "mp3", "wav", "aac" -> MeetingFileRow.Type.AUDIO
            "jpg", "jpeg", "png", "webp" -> MeetingFileRow.Type.IMAGE
            "pdf" -> MeetingFileRow.Type.PDF
            else -> MeetingFileRow.Type.DOCUMENT
        }
    }

    private fun openFile(item: MeetingFileRow) {
        val file = File(item.path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file
        )

        val mimeType = getMimeType(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "이 파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.getDefault())
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext)
            ?: "*/*"
    }
}