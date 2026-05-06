package com.example.a20260310.viewmodel

import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a20260310.data.model.Meeting
import com.example.a20260310.data.model.MeetingDraft
import com.example.a20260310.data.model.MeetingStatus
import com.example.a20260310.data.model.MinutesUiMapper
import com.example.a20260310.data.model.MinutesUiModel
import com.example.a20260310.data.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class SelectedSourceFile(
    val id: String = UUID.randomUUID().toString(),
    val type: Type,
    val displayName: String,
    val localPath: String,
    /** 녹음 세그먼트 m4a 경로(순서). 비어 있으면 [localPath]만 사용한다. */
    val segmentLocalPaths: List<String> = emptyList(),
) {
    enum class Type { AUDIO_RECORD, AUDIO_UPLOAD, IMAGE, DOCUMENT }
}

enum class SummaryPanelPhase {
    RUNNING,
    COMPLETED,
}

/**
 * 완료 결과 구분 (서버 꺼짐·타임아웃 등 테스트 폴백 vs 실제 API 성공).
 */
enum class SummaryCompletionMode {
    NONE,
    REAL_SUCCESS,
    /** 서버 미기동·연결/타임아웃 등으로 catch 더미-only 완료 — REAL_SUCCESS 아님 */
    TEST_NETWORK_FALLBACK,
}

data class SummaryProgressState(
    val meetingTitle: String,
    val progressPercent: Int,
    val etaSecondsRemaining: Long?,
    val isRunning: Boolean,
    val isComplete: Boolean,
    val phase: SummaryPanelPhase,
    val errorMessage: String?,
    val summarySucceeded: Boolean,
    /** RUNNING 뒤에 대기 중인 작업 수 (프로세스 메모리 큐만, 앱 재실행 시 복원 없음) */
    val waitingCount: Int = 0,
    val completionMode: SummaryCompletionMode = SummaryCompletionMode.NONE,
) {
    companion object {
        fun idle(): SummaryProgressState =
            SummaryProgressState(
                meetingTitle = "",
                progressPercent = 0,
                etaSecondsRemaining = null,
                isRunning = false,
                isComplete = false,
                phase = SummaryPanelPhase.RUNNING,
                errorMessage = null,
                summarySucceeded = true,
                waitingCount = 0,
                completionMode = SummaryCompletionMode.NONE,
            )
    }
}

/** 프로세스 메모리에만 유지되는 요약 작업 단위 */
private data class QueuedSummaryJob(
    val requestId: String,
    val draft: MeetingDraft,
    val files: List<SelectedSourceFile>,
    val meetingTitle: String,
    val estimateMs: Long,
)

class MeetingSessionViewModel(
    private val repository: MeetingRepository = MeetingRepository(),
) : ViewModel() {

    @Volatile
    private var draft: MeetingDraft = MeetingDraft()

    private val _meetingDraft = MutableLiveData(MeetingDraft())
    val meetingDraft: LiveData<MeetingDraft> = _meetingDraft

    private val _minutes = MutableLiveData<MinutesUiModel?>(null)
    val minutes: LiveData<MinutesUiModel?> = _minutes

    private val _isPipelineRunning = MutableLiveData(false)
    val isPipelineRunning: LiveData<Boolean> = _isPipelineRunning

    private val _pipelineError = MutableLiveData<String?>(null)
    val pipelineError: LiveData<String?> = _pipelineError

    private val _selectedFiles = MutableLiveData<List<SelectedSourceFile>>(emptyList())
    val selectedFiles: LiveData<List<SelectedSourceFile>> = _selectedFiles

    /** 폼 입력 기준 회의 엔티티. `serverFilePaths`는 업로드 응답 `file_path`만 누적한다. */
    private val _currentMeeting = MutableLiveData<Meeting?>(null)
    val currentMeeting: LiveData<Meeting?> = _currentMeeting

    private val _summaryProgress = MutableLiveData(SummaryProgressState.idle())
    val summaryProgress: LiveData<SummaryProgressState> = _summaryProgress

    private val _summaryPanelExpanded = MutableLiveData(false)
    val summaryPanelExpanded: LiveData<Boolean> = _summaryPanelExpanded

    private val queueLock = Any()
    private val waitingQueue = ArrayDeque<QueuedSummaryJob>()
    private var runningJob: QueuedSummaryJob? = null
    private var queueProcessorRunning = false

    private var lastSummarizeUsedFallback: Boolean = false

    private fun uiMeetingTitle(): String = draft.title.trim().ifBlank { "회의" }

    fun setSummaryPanelExpanded(expanded: Boolean) {
        _summaryPanelExpanded.value = expanded
    }

    fun dismissSummaryProgressPanel() {
        _summaryPanelExpanded.value = false
        val busy =
            synchronized(queueLock) {
                queueProcessorRunning || runningJob != null || waitingQueue.isNotEmpty()
            }
        if (!busy) {
            _summaryProgress.value = SummaryProgressState.idle()
        }
    }

    fun setDraft(newDraft: MeetingDraft) {
        draft = newDraft
        _meetingDraft.value = newDraft
        _currentMeeting.value =
            Meeting(
                id = UUID.randomUUID().toString(),
                title = newDraft.title,
                date = newDraft.date,
                time = newDraft.time,
                participants = newDraft.participantList(),
                serverFilePaths = emptyList(),
                status = MeetingStatus.CREATED,
                summary = null,
            )
    }

    private fun patchCurrentMeeting(transform: (Meeting) -> Meeting) {
        val cur = _currentMeeting.value ?: return
        _currentMeeting.postValue(transform(cur))
    }

    /** 파이프라인 진행 중 초안만 있을 때 */
    private fun ensureMeetingFromDraft(snapshot: MeetingDraft): Meeting {
        val existing = _currentMeeting.value
        if (existing != null) return existing
        val created =
            Meeting(
                id = UUID.randomUUID().toString(),
                title = snapshot.title.ifBlank { "무제 회의" },
                date = snapshot.date,
                time = snapshot.time,
                participants = snapshot.participantList(),
                serverFilePaths = emptyList(),
                status = MeetingStatus.CREATED,
                summary = null,
            )
        _currentMeeting.postValue(created)
        return created
    }

    fun clearPipelineError() {
        _pipelineError.value = null
    }

    fun clearMinutes() {
        _minutes.value = null
    }

    fun addSelectedFile(file: SelectedSourceFile) {
        val current = _selectedFiles.value.orEmpty()
        _selectedFiles.value = current + file
    }

    fun removeSelectedFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.orEmpty().filterNot { it.id == id }
    }

    /**
     * 새 회의 작성 플로우용 임시 첨부만 비운다.
     * 요약 큐·`minutes`·`summaryProgress`·`currentMeeting`(직전 결과)·파이프라인 오류 상태는 건드리지 않는다.
     */
    fun clearNewMeetingAttachmentSelection() {
        _selectedFiles.postValue(emptyList())
    }

    fun hasSelectedFilesForSummary(): Boolean = _selectedFiles.value.orEmpty().isNotEmpty()

    private fun waitingCountSnapshot(): Int = synchronized(queueLock) { waitingQueue.size }

    /**
     * 현재 선택된 회의·파일 스냅샷으로 요약 작업을 **메모리 큐**에 넣고,
     * 진행기가 한 번에 하나씩 순차 실행한다. (앱 프로세스 종료 시 큐 소멸)
     */
    fun startSummarizePipeline() {
        if (!hasSelectedFilesForSummary()) return

        val filesSnapshot = _selectedFiles.value.orEmpty().toList()
        val draftSnapshot = draft
        val title = uiMeetingTitle()
        val estimateMs = estimateDurationMs(filesSnapshot)

        val job =
            QueuedSummaryJob(
                requestId = UUID.randomUUID().toString(),
                draft = draftSnapshot,
                files = filesSnapshot,
                meetingTitle = title,
                estimateMs = estimateMs,
            )

        synchronized(queueLock) {
            waitingQueue.addLast(job)
        }

        bumpWaitingUiAfterEnqueue()
        ensureQueueProcessor()
    }

    private fun bumpWaitingUiAfterEnqueue() {
        val wc = waitingCountSnapshot()
        val cur = _summaryProgress.value
        if (cur == null) {
            if (wc > 0) emitWaitingOnlyUi()
            return
        }
        when {
            cur.isRunning && !cur.isComplete ->
                _summaryProgress.postValue(cur.copy(waitingCount = wc))
            !cur.isRunning && !cur.isComplete && wc > 0 ->
                emitWaitingOnlyUi()
        }
    }

    private fun emitWaitingOnlyUi() {
        val wc = waitingCountSnapshot()
        val nextTitle =
            synchronized(queueLock) { waitingQueue.firstOrNull()?.meetingTitle }.orEmpty()
        val etaSec =
            synchronized(queueLock) { waitingQueue.firstOrNull()?.estimateMs?.div(1000) }
        _summaryProgress.postValue(
            SummaryProgressState(
                meetingTitle = nextTitle,
                progressPercent = 0,
                etaSecondsRemaining = etaSec,
                isRunning = false,
                isComplete = false,
                phase = SummaryPanelPhase.RUNNING,
                errorMessage = null,
                summarySucceeded = true,
                waitingCount = wc,
                completionMode = SummaryCompletionMode.NONE,
            ),
        )
    }

    private fun ensureQueueProcessor() {
        synchronized(queueLock) {
            if (queueProcessorRunning) return
            queueProcessorRunning = true
        }
        viewModelScope.launch {
            try {
                while (true) {
                    val job =
                        synchronized(queueLock) {
                            if (waitingQueue.isEmpty()) {
                                queueProcessorRunning = false
                                null
                            } else {
                                waitingQueue.removeFirst()
                            }
                        } ?: break

                    synchronized(queueLock) {
                        runningJob = job
                    }
                    runQueuedJob(job)
                    synchronized(queueLock) {
                        runningJob = null
                    }
                }
            } finally {
                synchronized(queueLock) {
                    queueProcessorRunning = false
                }
            }
        }
    }

    private suspend fun runQueuedJob(job: QueuedSummaryJob) {
        _minutes.postValue(null)
        clearPipelineError()
        _summaryPanelExpanded.postValue(false)
        _isPipelineRunning.postValue(true)

        val estimateMs = job.estimateMs

        val ticker =
            viewModelScope.launch {
                val start = SystemClock.elapsedRealtime()
                while (isActive) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val denom = max(estimateMs, 1L)
                    val pct = min(95, ((elapsed * 95L) / denom).toInt())
                    val etaSec = ((estimateMs - elapsed).coerceAtLeast(0L)) / 1000L
                    _summaryProgress.postValue(
                        SummaryProgressState(
                            meetingTitle = job.meetingTitle,
                            progressPercent = pct,
                            etaSecondsRemaining = etaSec,
                            isRunning = true,
                            isComplete = false,
                            phase = SummaryPanelPhase.RUNNING,
                            errorMessage = null,
                            summarySucceeded = true,
                            waitingCount = waitingCountSnapshot(),
                            completionMode = SummaryCompletionMode.NONE,
                        ),
                    )
                    delay(250)
                }
            }

        try {
            lastSummarizeUsedFallback = false
            performSummarizePipeline(job.draft, job.files)
            ticker.cancel()
        } finally {
            ticker.cancel()
            _isPipelineRunning.postValue(false)

            val mins = _minutes.value
            if (mins != null) {
                val mode =
                    if (lastSummarizeUsedFallback) {
                        SummaryCompletionMode.TEST_NETWORK_FALLBACK
                    } else {
                        SummaryCompletionMode.REAL_SUCCESS
                    }
                _summaryProgress.postValue(
                    SummaryProgressState(
                        meetingTitle = job.meetingTitle,
                        progressPercent = 100,
                        etaSecondsRemaining = 0L,
                        isRunning = false,
                        isComplete = true,
                        phase = SummaryPanelPhase.COMPLETED,
                        errorMessage = _pipelineError.value,
                        // 서버 미기동·타임아웃 폴백도 패널에서는 완료(체크)로 표시 — 구분은 completionMode
                        summarySucceeded = true,
                        waitingCount = waitingCountSnapshot(),
                        completionMode = mode,
                    ),
                )
            }
        }
    }

    private fun estimateDurationMs(files: List<SelectedSourceFile>): Long {
        var bytes = 0L
        for (f in files) {
            val paths =
                when (f.type) {
                    SelectedSourceFile.Type.AUDIO_RECORD ->
                        f.segmentLocalPaths.takeIf { it.isNotEmpty() } ?: listOf(f.localPath)
                    else -> listOf(f.localPath)
                }
            for (p in paths) {
                val file = File(p)
                if (file.isFile) bytes += file.length()
            }
        }
        if (bytes == 0L) bytes = 256L * 1024L
        val extraFromSize = (bytes / 12_000L) * 1000L
        return (45_000L + extraFromSize).coerceIn(45_000L, 900_000L)
    }

    private suspend fun performSummarizePipeline(snapshot: MeetingDraft, selected: List<SelectedSourceFile>) {
        _pipelineError.value = null
        lastSummarizeUsedFallback = false
        ensureMeetingFromDraft(snapshot)
        patchCurrentMeeting { it.copy(status = MeetingStatus.PROCESSING) }
        try {
            val uploadedServerPaths = mutableListOf<String>()
            val created =
                withContext(Dispatchers.IO) {
                    repository.createMeeting(
                        title = snapshot.title.ifBlank { "무제 회의" },
                        description = snapshot.toDescription(),
                    )
                }
            var latestTranscriptText = ""
            withContext(Dispatchers.IO) {
                selected.forEach { file ->
                    when (file.type) {
                        SelectedSourceFile.Type.AUDIO_RECORD,
                        SelectedSourceFile.Type.AUDIO_UPLOAD -> {
                            val local = File(file.localPath)
                            val audioFiles =
                                when (file.type) {
                                    SelectedSourceFile.Type.AUDIO_RECORD -> {
                                        val segs = file.segmentLocalPaths
                                        if (segs.isNotEmpty()) {
                                            segs.map { File(it) }.filter { it.isFile && it.length() > 0L }
                                        } else {
                                            listOf(local).filter { it.isFile && it.length() > 0L }
                                        }
                                    }
                                    else -> listOf(local).filter { it.isFile && it.length() > 0L }
                                }
                            if (audioFiles.isEmpty()) return@forEach
                            val transcript = repository.uploadAudio(created.id, audioFiles)
                            latestTranscriptText = transcript.content
                            // 오디오 업로드 응답에 서버 파일 경로 필드 없음 → serverFilePaths에 저장하지 않음
                        }
                        SelectedSourceFile.Type.IMAGE,
                        SelectedSourceFile.Type.DOCUMENT -> {
                            val local = File(file.localPath)
                            if (!local.exists() || local.length() == 0L) return@forEach
                            val imageResp =
                                repository.uploadImage(created.id, local, imageType = "image")
                            imageResp.filePath.trim().takeIf { it.isNotEmpty() }?.let {
                                uploadedServerPaths.add(it)
                            }
                        }
                    }
                }
            }
            val summary =
                withContext(Dispatchers.IO) {
                    repository.generateSummary(created.id)
                }
            val ui = MinutesUiMapper.build(snapshot, latestTranscriptText, summary)
            patchCurrentMeeting {
                it.copy(
                    status = MeetingStatus.COMPLETED,
                    serverFilePaths = uploadedServerPaths,
                    summary = ui.summaryText,
                )
            }
            _minutes.value = ui
        } catch (_: Exception) {
            // [테스트 전용] 서버 미기동·타임아웃·연결 오류 시에도 UI/파이프라인 동작을 검증하기 위해
            // COMPLETED + 더미 요약으로 맞춘다. SummaryCompletionMode.TEST_NETWORK_FALLBACK 으로 UI에서 구분.
            // TODO(운영): 실패 시 Meeting.status = FAILED, 더미 요약 대신 오류 메시지·재시도 등으로 처리할 것.
            lastSummarizeUsedFallback = true
            _pipelineError.postValue(null)
            val dummy = buildDummyMinutes(snapshot, selected)
            patchCurrentMeeting {
                it.copy(
                    status = MeetingStatus.COMPLETED,
                    summary = dummy.summaryText,
                )
            }
            _minutes.value = dummy
        }
    }

    private fun buildDummyMinutes(snapshot: MeetingDraft, files: List<SelectedSourceFile>): MinutesUiModel {
        val now = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())
        val title = snapshot.title.trim().ifBlank { "더미 회의" }
        val summaryBody =
            "서버 비연결 상태로 더미 요약을 생성했습니다. 실제 연동 시에는 STT·요약 API 결과가 표시됩니다."
        val usedSources = files.joinToString(", ") { it.displayName }
        return MinutesUiModel(
            subject = title,
            datetime = snapshot.displayDatetime().takeIf { it != "—" } ?: now,
            attendees = "김모아, 이기록, 박요약",
            summaryText = summaryBody,
            agenda = "• 앱 UI 검증\n• 더미 파이프라인 점검\n• 서버 재연결 전 체크리스트 정리",
            discussion = buildString {
                append(summaryBody)
                append("\n\n선택 소스: ${usedSources.ifBlank { "없음" }}")
            },
            note = "현재 STT 서버가 닫혀 있어 로컬 더미 모드로 동작 중입니다.",
            followup = "• 서버 오픈 후 실데이터 리그레션 테스트\n• 공유/저장 기능 QA",
            writerLabel = "작성자: MOA (DUMMY)",
        )
    }
}
