package com.example.a20260310.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a20260310.R
import com.example.a20260310.data.model.SimpleRow
import com.example.a20260310.ui.common.SimpleRowAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import java.io.File
import java.util.Locale
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.FileInputStream
import java.io.OutputStream

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var isSummaryTab = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val summaryBtn = view.findViewById<MaterialButton>(R.id.tabSummary)
        val fileBtn = view.findViewById<MaterialButton>(R.id.tabFiles)
        val recycler = view.findViewById<RecyclerView>(R.id.fileRecycler)
        val summaryScroll = view.findViewById<ScrollView>(R.id.summaryScroll)

        val meetingTitle = arguments?.getString("meetingTitle") ?: "회의"

        title.text = meetingTitle
        recycler.layoutManager = LinearLayoutManager(requireContext())

        setupCard(
            parentView = view,
            cardId = R.id.cardSummary,
            titleText = "회의 요약",
            prefKey = "${meetingTitle}_summary",
            defaultText = "요약 없음",
            scrollView = summaryScroll
        )

        setupCard(
            parentView = view,
            cardId = R.id.cardDecisions,
            titleText = "결정 사항",
            prefKey = "${meetingTitle}_decisions",
            defaultText = "결정 사항 없음",
            scrollView = summaryScroll
        )

        setupCard(
            parentView = view,
            cardId = R.id.cardActions,
            titleText = "할 일",
            prefKey = "${meetingTitle}_actions",
            defaultText = "할 일 없음",
            scrollView = summaryScroll
        )

        setupCard(
            parentView = view,
            cardId = R.id.cardParticipants,
            titleText = "담당자",
            prefKey = "${meetingTitle}_participants",
            defaultText = "",
            scrollView = summaryScroll
        )

        updateTabs(summaryBtn, fileBtn)
        showSummary(summaryScroll, recycler)

        summaryBtn.setOnClickListener {
            isSummaryTab = true
            updateTabs(summaryBtn, fileBtn)
            showSummary(summaryScroll, recycler)
        }

        fileBtn.setOnClickListener {
            isSummaryTab = false
            updateTabs(summaryBtn, fileBtn)
            showFiles(summaryScroll, recycler)
        }
    }

    private fun setupCard(
        parentView: View,
        cardId: Int,
        titleText: String,
        prefKey: String,
        defaultText: String,
        scrollView: ScrollView
    ) {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val card = parentView.findViewById<View>(cardId)

        val title = card.findViewById<TextView>(R.id.cardTitle)
        val editBtn = card.findViewById<View>(R.id.btnEdit)
        val saveBtn = card.findViewById<View>(R.id.btnSave)
        val editText = card.findViewById<TextInputEditText>(R.id.cardContent)

        title.text = titleText
        editText.setText(prefs.getString(prefKey, defaultText))

        editBtn.setOnClickListener {
            editBtn.visibility = View.GONE
            saveBtn.visibility = View.VISIBLE

            editText.isFocusableInTouchMode = true
            editText.isCursorVisible = true
            editText.requestFocus()

            val imm = requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager

            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)

            editText.post {
                scrollView.smoothScrollTo(0, editText.bottom)
            }
        }

        saveBtn.setOnClickListener {
            val text = editText.text.toString()
            prefs.edit().putString(prefKey, text).apply()

            editText.isFocusable = false
            editText.isFocusableInTouchMode = false
            editText.isCursorVisible = false
            editText.clearFocus()

            editBtn.visibility = View.VISIBLE
            saveBtn.visibility = View.GONE

            val imm = requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager

            imm.hideSoftInputFromWindow(editText.windowToken, 0)

            Toast.makeText(requireContext(), "저장됨", Toast.LENGTH_SHORT).show()
        }
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

        recycler.adapter = SimpleRowAdapter(files) { row ->
            downloadFile(row.title)
        }
    }

    private fun loadFiles(): List<SimpleRow> {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val meetingTitle = arguments?.getString("meetingTitle") ?: return emptyList()
        val key = "meeting_files_$meetingTitle"
        val json = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(json)

        val items = mutableListOf<SimpleRow>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val displayName = obj.optString("displayName")
            val localPath = obj.optString("localPath")
            val size = obj.optLong("size", 0L)

            val file = File(localPath)
            if (file.exists()) {
                items.add(
                    SimpleRow(
                        title = displayName.ifBlank { file.name },
                        subtitle = formatFileSize(size)
                    )
                )
            }
        }

        return items
    }

    private fun openFile(displayName: String) {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val meetingTitle = arguments?.getString("meetingTitle") ?: return
        val key = "meeting_files_$meetingTitle"
        val json = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(json)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("displayName") == displayName) {
                val localPath = obj.optString("localPath")
                val file = File(localPath)

                if (!file.exists()) {
                    Toast.makeText(requireContext(), "파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                    return
                }

                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )

                val mime = getMimeType(file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                runCatching {
                    startActivity(intent)
                }.onFailure {
                    Toast.makeText(requireContext(), "열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }

        Toast.makeText(requireContext(), "파일 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
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

    private fun downloadFile(displayName: String) {
        val prefs = requireContext().getSharedPreferences("moa_prefs", 0)
        val meetingTitle = arguments?.getString("meetingTitle") ?: return
        val key = "meeting_files_$meetingTitle"
        val json = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(json)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("displayName") == displayName) {
                val localPath = obj.optString("localPath")
                val file = File(localPath)

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
                return
            }
        }

        Toast.makeText(requireContext(), "파일 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun copyFileToDownloads(sourceFile: File): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyFileToDownloadsApi29Plus(sourceFile)
        } else {
            copyFileToDownloadsLegacy(sourceFile)
        }
    }

    private fun copyFileToDownloadsApi29Plus(sourceFile: File): Boolean {
        val resolver = requireContext().contentResolver
        val mimeType = getMimeType(sourceFile)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/MOA"
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
}