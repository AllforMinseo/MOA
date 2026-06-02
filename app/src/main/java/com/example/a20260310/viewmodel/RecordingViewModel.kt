package com.example.a20260310.viewmodel

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.a20260310.data.model.RecordingPhase
import com.example.a20260310.data.model.RecordingUiState
import com.example.a20260310.data.repository.RecordingRepository
import com.example.a20260310.data.repository.RecordingRepositoryImpl
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

class RecordingViewModel(
    private val recordingRepository: RecordingRepository = RecordingRepositoryImpl(),
) : ViewModel() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var elapsedTickCount = 0L
    private var finalizedSegmentMs: Long = 0L
    private val segmentDurationsInternal = mutableListOf<Long>()
    private val sampleBuffer = ArrayDeque<Float>()
    private val maxSamples = 6000

    /**
     * 파형 UI 전용 스케일(실제 인코딩 볼륨과 무관).
     * [WAVEFORM_NORMALIZATION_DENOMINATOR]·gain·압축으로 동적 범위만 조절한다.
     */
    companion object {
        private const val TAG = "RecordingViewModel"

        /**
         * 정규화 분모. 이론 최대(32767)보다 크게 잡으면 동일 raw 피크의 상대 레벨이 낮아져
         * AGC·음성 구간에서도 막대가 쉽게 1.0에 붙지 않는다.
         */
        private const val WAVEFORM_NORMALIZATION_DENOMINATOR = 42_000f

        /**
         * 정규화 후·압축 전 지수. 1보다 작게 두면 저·중음역 대비가 조금 더 벌어진다.
         */
        private const val WAVEFORM_LEVEL_CURVE_EXPONENT = 0.88f

        /** 선형 게인(압축 전). 일반 말소리가 화면에서 대략 40~70% 높이대에 오도록 맞춘 값 */
        private const val WAVEFORM_LINEAR_GAIN = 0.52f

        /**
         * log 압축.
         * display = log10(1 + linearScaled * K) / log10(1 + K)
         */
        private const val WAVEFORM_LOG_COMPRESSION_K = 17f

        /** 디버그 로그 출력 간격(녹음 틱 1회 = 100ms → 10이면 약 1초마다) */
        private const val WAVEFORM_DEBUG_LOG_INTERVAL_TICKS = 10L
    }

    private val _uiState = MutableLiveData(RecordingUiState())
    val uiState: LiveData<RecordingUiState> = _uiState

    private val ticker = object : Runnable {
        override fun run() {
            val current = _uiState.value ?: RecordingUiState()
            if (current.phase == RecordingPhase.RECORDING) {
                elapsedTickCount += 1
                val segmentMs = elapsedTickCount * 100L
                val totalMs = finalizedSegmentMs + segmentMs
                val amp = recordingRepository.getMaxAmplitude()
                val normalizedAmp = amp.toFloat() / WAVEFORM_NORMALIZATION_DENOMINATOR
                val displayAmp = amplitudeToDisplaySample(normalizedAmp)
                sampleBuffer.addLast(displayAmp)
                if (elapsedTickCount % WAVEFORM_DEBUG_LOG_INTERVAL_TICKS == 0L) {
                    Log.d(
                        TAG,
                        "waveform level raw=$amp normalized=$normalizedAmp display=$displayAmp",
                    )
                }
                while (sampleBuffer.size > maxSamples) {
                    sampleBuffer.removeFirst()
                }
                _uiState.value =
                    current.copy(
                        elapsedMs = segmentMs,
                        totalRecordedMs = totalMs,
                        playheadMs = totalMs,
                        amplitude = amp,
                        waveformSamples = sampleBuffer.toList(),
                        completedSegmentDurationsMs = segmentDurationsInternal.toList(),
                    )
            } else if (current.amplitude != 0) {
                _uiState.value = current.copy(amplitude = 0)
            }
            mainHandler.postDelayed(this, 100L)
        }
    }

    init {
        mainHandler.postDelayed(ticker, 100L)
    }

    /** 마이크 해제·버퍼 비우기(새 세션 시작 전 등) */
    fun resetSession() {
        recordingRepository.stop()
        sampleBuffer.clear()
        elapsedTickCount = 0L
        finalizedSegmentMs = 0L
        segmentDurationsInternal.clear()
        _uiState.value = RecordingUiState()
    }

    /**
     * 새 세그먼트 파일로 녹음 시작.
     * 이전 세그먼트에서 확정된 길이는 유지하고 파형은 이어 붙인다.
     */
    fun beginRecording(outputPath: String) {
        recordingRepository.start(outputPath)
        elapsedTickCount = 0L
        val base = finalizedSegmentMs
        _uiState.value =
            RecordingUiState(
                phase = RecordingPhase.RECORDING,
                outputPath = outputPath,
                elapsedMs = 0L,
                totalRecordedMs = base,
                playheadMs = base,
                waveformSamples = sampleBuffer.toList(),
                amplitude = 0,
                completedSegmentDurationsMs = segmentDurationsInternal.toList(),
            )
    }

    /**
     * 현재 세그먼트 녹음을 종료하고(파일 확정) 일시정지 상태로 둔다.
     * 세그먼트 길이(틱 기준)는 [completedSegmentDurationsMs]에 반영된다.
     */
    fun finalizeCurrentSegment() {
        if (_uiState.value?.phase != RecordingPhase.RECORDING) return
        val segmentMs = elapsedTickCount * 100L
        recordingRepository.stop()
        if (segmentMs > 0L) {
            finalizedSegmentMs += segmentMs
            segmentDurationsInternal.add(segmentMs)
        }
        elapsedTickCount = 0L
        val cur = _uiState.value ?: return
        _uiState.value =
            cur.copy(
                phase = RecordingPhase.PAUSED,
                amplitude = 0,
                elapsedMs = 0L,
                totalRecordedMs = finalizedSegmentMs,
                playheadMs = finalizedSegmentMs,
                completedSegmentDurationsMs = segmentDurationsInternal.toList(),
            )
    }

    fun adjustPlayheadByMs(deltaMs: Float) {
        val cur = _uiState.value ?: return
        if (cur.phase == RecordingPhase.RECORDING) return
        val maxP = max(cur.totalRecordedMs, 1L)
        val next = (cur.playheadMs + deltaMs.toLong()).coerceIn(0L, maxP)
        _uiState.value = cur.copy(playheadMs = next)
    }

    fun setPlayheadMs(ms: Long) {
        val cur = _uiState.value ?: return
        val maxP = max(cur.totalRecordedMs, 1L)
        _uiState.value = cur.copy(playheadMs = ms.coerceIn(0L, maxP))
    }

    /**
     * UI 파형용 샘플(0~1). 녹음 파일이 아니라 화면 막대 높이만 조정한다.
     */
    private fun amplitudeToDisplaySample(normalizedAmp: Float): Float {
        val n = normalizedAmp.coerceIn(0f, 1f)
        val expanded = n.pow(WAVEFORM_LEVEL_CURVE_EXPONENT)
        val linearScaled = expanded * WAVEFORM_LINEAR_GAIN
        val k = WAVEFORM_LOG_COMPRESSION_K.toDouble()
        val compressed =
            log10(1.0 + linearScaled.toDouble() * k) / log10(1.0 + k)
        return compressed.toFloat().coerceIn(0f, 1f)
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(ticker)
        recordingRepository.stop()
        super.onCleared()
    }
}
