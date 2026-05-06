package com.example.a20260310.data.model

enum class RecordingPhase {
    IDLE,
    RECORDING,
    PAUSED,
}

data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    /** 현재 세그먼트 내 경과(ms), 100ms 틱 기준 */
    val elapsedMs: Long = 0L,
    val outputPath: String? = null,
    val amplitude: Int = 0,
    /** 100ms 간격 정규화 진폭(0~1), 세션 전체에 누적 */
    val waveformSamples: List<Float> = emptyList(),
    /** 전체 타임라인 기준 커서(ms) */
    val playheadMs: Long = 0L,
    /** 녹음으로 쌓인 타임라인 끝(ms). 일시정지 시 고정 */
    val totalRecordedMs: Long = 0L,
    /** 확정된 각 세그먼트 길이(ms), 순서는 녹음 세그먼트 파일 순과 동일 */
    val completedSegmentDurationsMs: List<Long> = emptyList(),
)
