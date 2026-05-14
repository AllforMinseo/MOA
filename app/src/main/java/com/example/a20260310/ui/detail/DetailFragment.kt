package com.example.a20260310.ui.detail

import android.app.AlertDialog
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.auth.TokenManager
import com.example.a20260310.data.model.DecisionItem
import com.example.a20260310.data.model.DetailTaskItem
import com.example.a20260310.data.model.MeetingFileRow
import com.example.a20260310.data.remote.dto.ActionItemDto
import com.example.a20260310.data.remote.dto.DecisionDto
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto
import com.example.a20260310.viewmodel.DetailViewModel
import com.example.a20260310.viewmodel.DetailViewModelFactory
import com.example.a20260310.viewmodel.MeetingDetailEffect
import com.example.a20260310.viewmodel.MeetingDetailUiState
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.util.Locale

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var isSummaryTab = true

    private lateinit var detailProgress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var meetingDateView: TextView
    private lateinit var meetingTimeView: TextView
    private lateinit var meetingAttendeesView: TextView
    private lateinit var summaryBtn: MaterialButton
    private lateinit var fileBtn: MaterialButton
    private lateinit var fileRecycler: RecyclerView
    private lateinit var summaryScroll: ScrollView
    private lateinit var summaryText: TextView

    private lateinit var decisionAdapter: DecisionAdapter
    private lateinit var actionAdapter: ActionAdapter

    private val meetingId: Int
        get() = arguments?.takeIf { it.containsKey("meetingId") }?.getInt("meetingId", 0) ?: 0

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory(meetingId)
    }

    private val meetingTitle: String
        get() = arguments?.getString("meetingTitle") ?: "회의"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        detailProgress = view.findViewById(R.id.detailProgress)
        titleView = view.findViewById(R.id.title)
        meetingDateView = view.findViewById(R.id.meetingDate)
        meetingTimeView = view.findViewById(R.id.meetingTime)
        meetingAttendeesView = view.findViewById(R.id.meetingAttendees)
        summaryBtn = view.findViewById(R.id.tabSummary)
        fileBtn = view.findViewById(R.id.tabFiles)
        fileRecycler = view.findViewById(R.id.fileRecycler)
        summaryScroll = view.findViewById(R.id.summaryScroll)
        summaryText = view.findViewById(R.id.summaryText)

        val decisionRecycler = view.findViewById<RecyclerView>(R.id.decisionRecycler)
        val actionRecycler = view.findViewById<RecyclerView>(R.id.actionRecycler)

        titleView.text = meetingTitle
        fileRecycler.layoutManager = LinearLayoutManager(requireContext())
        decisionRecycler.layoutManager = LinearLayoutManager(requireContext())
        actionRecycler.layoutManager = LinearLayoutManager(requireContext())

        decisionAdapter =
            DecisionAdapter(
                items = mutableListOf(),
                onEdit = { item, position -> showEditDecisionDialog(item, position) },
                onDelete = { item, position -> confirmDeleteDecision(item, position) },
            )
        decisionRecycler.adapter = decisionAdapter

        actionAdapter =
            ActionAdapter(
                items = mutableListOf(),
                onEdit = { item, position -> showEditActionDialog(item, position) },
                onDelete = { item, position -> confirmDeleteAction(item, position) },
            )
        actionRecycler.adapter = actionAdapter

        summaryText.text = "—"
        meetingAttendeesView.text = getString(R.string.detail_attendees_empty)
        meetingAttendeesView.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.color_text_secondary),
        )

        view.findViewById<View>(R.id.btnEditMeetingInfo).setOnClickListener {
            showEditMeetingInfoDialog()
        }
        view.findViewById<View>(R.id.btnViewTranscript).setOnClickListener {
            viewModel.loadTranscript()
        }
        view.findViewById<View>(R.id.btnAddDecision).setOnClickListener { showAddDecisionDialog() }
        view.findViewById<View>(R.id.btnAddAction).setOnClickListener { showAddActionDialog() }

        setupListeners(view)
        setupToolbarMenu()
        updateTabs(summaryBtn, fileBtn)
        showSummary(summaryScroll, fileRecycler)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> applyUiState(state) }
                }
                launch {
                    viewModel.effects.collect { effect -> handleEffect(effect, view) }
                }
            }
        }

        if (meetingId > 0) {
            viewModel.loadInitial()
        } else {
            meetingDateView.text = "—"
            meetingTimeView.text = "—"
            bindAttendees(null)
        }
    }

    private fun setupToolbarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_detail, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_delete_meeting -> {
                            showDeleteMeetingDialog()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun applyUiState(state: MeetingDetailUiState) {
        detailProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        val meeting = state.meeting
        if (meeting != null) {
            titleView.text = meeting.title.ifBlank { meetingTitle }
            meetingDateView.text = meeting.meetingDate?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
            meetingTimeView.text = meeting.meetingTime?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
            bindAttendees(meeting)
        } else if (meetingId <= 0) {
            titleView.text = meetingTitle
            meetingDateView.text = "—"
            meetingTimeView.text = "—"
            bindAttendees(null)
        }
        state.summary?.let { bindSummaryDetail(it) }
    }

    private fun bindAttendees(meeting: MeetingResponseDto?) {
        val names =
            meeting?.attendees
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (names.isEmpty()) {
            meetingAttendeesView.text = getString(R.string.detail_attendees_empty)
            meetingAttendeesView.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.color_text_secondary),
            )
        } else {
            meetingAttendeesView.text = names.joinToString(", ")
            meetingAttendeesView.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.color_text_primary),
            )
        }
    }

    private fun handleEffect(effect: MeetingDetailEffect, root: View) {
        when (effect) {
            is MeetingDetailEffect.Toast ->
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()

            is MeetingDetailEffect.Error ->
                Snackbar.make(root, effect.message, Snackbar.LENGTH_LONG).show()

            MeetingDetailEffect.NavigateToLogin -> {
                TokenManager.clear()
                findNavController().navigate(R.id.loginFragment)
            }

            MeetingDetailEffect.MeetingDeleted -> {
                Toast.makeText(requireContext(), "회의가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }

            is MeetingDetailEffect.TranscriptReady -> showTranscriptDialog(effect.text)
        }
    }

    private fun showTranscriptDialog(text: String) {
        val scroll =
            ScrollView(requireContext()).apply {
                val tv =
                    TextView(requireContext()).apply {
                        setTextIsSelectable(true)
                        textSize = 14f
                        setPadding(48, 24, 48, 24)
                        this.text = text
                    }
                addView(tv)
            }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("회의 전사")
            .setView(scroll)
            .setPositiveButton("닫기", null)
            .show()
    }

    private fun showEditMeetingInfoDialog() {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "유효한 회의가 아니면 서버에 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_meeting_info, null, false)
        val inputTitle = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingTitle)
        val inputDate = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingDate)
        val inputTime = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingTime)
        val inputAttendees = dialogView.findViewById<TextInputEditText>(R.id.inputMeetingAttendees)

        val m = viewModel.uiState.value.meeting
        inputTitle.setText(m?.title ?: titleView.text?.toString().orEmpty())
        inputDate.setText(
            when {
                m?.meetingDate?.isNotBlank() == true -> m.meetingDate
                meetingDateView.text.toString() != "—" -> meetingDateView.text.toString()
                else -> ""
            },
        )
        inputTime.setText(
            when {
                m?.meetingTime?.isNotBlank() == true -> m.meetingTime
                meetingTimeView.text.toString() != "—" -> meetingTimeView.text.toString()
                else -> ""
            },
        )
        inputAttendees.setText(m?.attendees?.joinToString(", ").orEmpty())

        AlertDialog.Builder(requireContext())
            .setTitle("회의 정보 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val title = inputTitle.text?.toString().orEmpty()
                if (title.isBlank()) {
                    Toast.makeText(requireContext(), "제목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val date = inputDate.text?.toString().orEmpty()
                val time = inputTime.text?.toString().orEmpty()
                val attendees = inputAttendees.text?.toString().orEmpty()
                viewModel.updateMeetingInfo(title, date, time, attendees)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setupListeners(view: View) {
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

        view.findViewById<View>(R.id.btnEditSummary).setOnClickListener {
            showEditSummaryDialog()
        }
    }

    private fun showDeleteMeetingDialog() {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "삭제할 회의 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("회의 삭제")
            .setMessage("삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> viewModel.deleteMeeting() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun bindSummaryDetail(detail: SummaryDetailResponseDto) {
        summaryText.text = detail.summary.trim().ifBlank { "—" }
        decisionAdapter.replaceAll(detail.decisions.map { it.toDecisionItem() })
        actionAdapter.replaceAll(detail.actionItems.map { it.toDetailTaskItem() })
    }

    private fun showEditSummaryDialog() {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "유효한 회의가 아니면 서버에 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_summary, null, false)
        val etSummary = dialogView.findViewById<TextInputEditText>(R.id.etSummary)
        etSummary.setText(
            if (summaryText.text.toString() == "—") "" else summaryText.text.toString(),
        )

        AlertDialog.Builder(requireContext())
            .setTitle("회의 요약 수정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val text = etSummary.text?.toString().orEmpty().trim()
                viewModel.patchSummaryBody(text)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddDecisionDialog() {
        if (!ensureHasMeetingId()) return
        val editText = EditText(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("결정 사항 추가")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                viewModel.addDecision(text)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditDecisionDialog(item: DecisionItem, position: Int) {
        if (!ensureHasMeetingId()) return
        val editText = EditText(requireContext())
        editText.setText(item.content)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("결정 사항 수정")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                viewModel.updateDecision(item.id, text)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteDecision(item: DecisionItem, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("이 결정 사항을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                if (!ensureHasMeetingId()) return@setPositiveButton
                viewModel.deleteDecision(item.id)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddActionDialog() {
        if (!ensureHasMeetingId()) return
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_item, null, false)
        val titleInput = dialogView.findViewById<EditText>(R.id.editTaskTitle)
        val ownerInput = dialogView.findViewById<EditText>(R.id.editTaskOwner)
        val deadlineInput = dialogView.findViewById<EditText>(R.id.editTaskDeadline)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("할 일 추가")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val titleText = titleInput.text.toString().trim()
                if (titleText.isEmpty()) return@setPositiveButton
                val owner = ownerInput.text.toString().trim()
                val deadline = deadlineInput.text.toString().trim()
                viewModel.addActionItem(
                    task = titleText,
                    assignee = owner.ifBlank { null },
                    dueDate = deadline.ifBlank { null },
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditActionDialog(item: DetailTaskItem, position: Int) {
        if (!ensureHasMeetingId()) return
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_action_item, null, false)
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
                val titleText = titleInput.text.toString().trim()
                if (titleText.isEmpty()) return@setPositiveButton
                val owner = ownerInput.text.toString().trim()
                val deadline = deadlineInput.text.toString().trim()
                viewModel.updateActionItem(
                    actionItemId = item.id,
                    task = titleText,
                    assignee = owner.ifBlank { null },
                    dueDate = deadline.ifBlank { null },
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteAction(item: DetailTaskItem, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("이 할 일을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                if (!ensureHasMeetingId()) return@setPositiveButton
                viewModel.deleteActionItem(item.id)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun ensureHasMeetingId(): Boolean {
        if (meetingId <= 0) {
            Toast.makeText(requireContext(), "유효한 회의가 아니면 서버에 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updateTabs(summaryBtn: MaterialButton, fileBtn: MaterialButton) {
        val primary = ContextCompat.getColor(requireContext(), R.color.color_primary)
        val onPrimary = ContextCompat.getColor(requireContext(), R.color.color_on_primary)
        val surface = ContextCompat.getColor(requireContext(), R.color.color_surface)
        val textPrimary = ContextCompat.getColor(requireContext(), R.color.color_text_primary)
        val line = ContextCompat.getColor(requireContext(), R.color.moa_line)
        val strokePx = (resources.displayMetrics.density * 1f).toInt().coerceAtLeast(1)
        if (isSummaryTab) {
            applyTabSelected(summaryBtn, primary, onPrimary)
            applyTabUnselected(fileBtn, surface, textPrimary, line, strokePx)
        } else {
            applyTabUnselected(summaryBtn, surface, textPrimary, line, strokePx)
            applyTabSelected(fileBtn, primary, onPrimary)
        }
    }

    private fun applyTabSelected(btn: MaterialButton, bg: Int, fg: Int) {
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
        btn.strokeWidth = 0
        btn.setTextColor(fg)
    }

    private fun applyTabUnselected(
        btn: MaterialButton,
        bg: Int,
        fg: Int,
        stroke: Int,
        strokePx: Int,
    ) {
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
        btn.strokeColor = android.content.res.ColorStateList.valueOf(stroke)
        btn.strokeWidth = strokePx
        btn.setTextColor(fg)
    }

    private fun showSummary(summaryScroll: View, recycler: RecyclerView) {
        summaryScroll.visibility = View.VISIBLE
        recycler.visibility = View.GONE
    }

    private fun showFiles(summaryScroll: View, recycler: RecyclerView) {
        summaryScroll.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        val files = loadFiles()
        recycler.adapter =
            MeetingFileAdapter(files) { row ->
                downloadFile(row)
            }
    }

    private fun loadFiles(): List<MeetingFileRow> {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val key = "meeting_files_$meetingTitle"
        val json = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(json)
        val items = mutableListOf<MeetingFileRow>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val displayName = obj.optString("displayName")
            val localPath = obj.optString("localPath")
            val storedSize = obj.optLong("size", 0L)
            val file = File(localPath)
            if (file.exists()) {
                val actualSize = if (storedSize > 0L) storedSize else file.length()
                val ext = file.extension.lowercase(Locale.ROOT)
                val type =
                    when (ext) {
                        "m4a", "mp3", "wav", "aac", "mp4" -> MeetingFileRow.Type.AUDIO
                        "jpg", "jpeg", "png", "webp" -> MeetingFileRow.Type.IMAGE
                        "pdf" -> MeetingFileRow.Type.PDF
                        else -> MeetingFileRow.Type.DOCUMENT
                    }
                val extLabel = if (ext.isBlank()) "FILE" else ext.uppercase(Locale.ROOT)
                items.add(
                    MeetingFileRow(
                        title = displayName.ifBlank { file.name },
                        subtitle = "$extLabel · ${formatFileSize(actualSize)}",
                        localPath = localPath,
                        displayName = displayName.ifBlank { file.name },
                        type = type,
                    ),
                )
            }
        }
        return items.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    private fun downloadFile(item: MeetingFileRow) {
        val file = File(item.localPath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val success = copyFileToDownloads(file)
        if (success) {
            Toast.makeText(requireContext(), "다운로드 완료", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "다운로드 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyFileToDownloads(sourceFile: File): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyFileToDownloadsApi29Plus(sourceFile)
        } else {
            copyFileToDownloadsLegacy(sourceFile)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun copyFileToDownloadsApi29Plus(sourceFile: File): Boolean {
        val resolver = requireContext().contentResolver
        val mimeType = getMimeType(sourceFile)
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/MOA",
                )
            }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            FileInputStream(sourceFile).use { input ->
                resolver.openOutputStream(itemUri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("출력 스트림을 열 수 없습니다.")
            }
            true
        }.getOrElse {
            resolver.delete(itemUri, null, null)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun copyFileToDownloadsLegacy(sourceFile: File): Boolean {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val moaDir = File(downloadsDir, "MOA")
        if (!moaDir.exists()) {
            moaDir.mkdirs()
        }
        val targetFile = File(moaDir, sourceFile.name)
        return runCatching {
            FileInputStream(sourceFile).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrElse {
            false
        }
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "pdf" -> "application/pdf"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "aac" -> "audio/aac"
                "mp4" -> "audio/mp4"
                else -> "*/*"
            }
    }

    private fun formatFileSize(size: Long): String {
        if (size < 1024) return "${size} B"
        if (size < 1024 * 1024) return "${size / 1024} KB"
        return String.format(Locale.getDefault(), "%.1f MB", size / 1024f / 1024f)
    }
}

private fun DecisionDto.toDecisionItem(): DecisionItem =
    DecisionItem(
        id = id,
        meetingId = meetingId,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun ActionItemDto.toDetailTaskItem(): DetailTaskItem =
    DetailTaskItem(
        id = id,
        meetingId = meetingId,
        title = task,
        owner = assignee.orEmpty(),
        deadline = dueDate.orEmpty(),
    )
