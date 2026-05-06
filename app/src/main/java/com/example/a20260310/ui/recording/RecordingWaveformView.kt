package com.example.a20260310.ui.recording

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.example.a20260310.BuildConfig
import com.example.a20260310.data.model.RecordingPhase
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * 고정 중앙 커서 + 가로 스크롤 가능한 파형 + 하단 타임라인 인디케이터.
 * [playheadMs]에 해당하는 시각이 화면 중앙에 오도록 샘플을 그린다.
 */
class RecordingWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    companion object {
        private const val TAG = "RecordingWaveformView"
    }

    /** 화면 너비에 대응하는 타임라인 가시 구간(ms) */
    private val viewportMs = 8000f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        style = Paint.Style.FILL
    }

    private val playedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F35555")
        style = Paint.Style.FILL
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x00000000
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val scrollbarTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC")
        style = Paint.Style.FILL
    }

    private val scrollbarThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private var samples: List<Float> = emptyList()
    private var playheadMs: Long = 0L
    private var totalRecordedMs: Long = 0L
    private var phase: RecordingPhase = RecordingPhase.IDLE

    /** RECORDING이 아닐 때만 드래그로 playhead 이동 */
    var waveformInteractive: Boolean = false
        private set

    var onPlayheadDeltaMs: ((Float) -> Unit)? = null

    /** 터치 직전(드래그 시작) — 재생 중이면 일시정지 등에 사용 */
    var onInteractionBegin: (() -> Unit)? = null

    private val density = resources.displayMetrics.density

    /**
     * 진폭이 거의 없을 때 막대 최소 높이(dp). 작게 잡아 조용한 구간은 세로만 얇게 보이게 한다.
     */
    private val quietBarFloorDp = 1.75f

    private val scrollbarHeightPx = 4f * density
    private val scrollbarCornerPx = 2f * density
    private val thumbMinPx = 8f * density

    private var flingVelocityMsPerSec = 0f
    private var lastFlingFrameTimeNs = 0L

    private val flingRunnable =
        object : Runnable {
            override fun run() {
                if (!isAttachedToWindow || flingVelocityMsPerSec == 0f) {
                    lastFlingFrameTimeNs = 0L
                    return
                }
                val now = SystemClock.elapsedRealtimeNanos()
                val dt =
                    if (lastFlingFrameTimeNs == 0L) {
                        (1f / 60f)
                    } else {
                        ((now - lastFlingFrameTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    }
                lastFlingFrameTimeNs = now

                val deltaMs = flingVelocityMsPerSec * dt
                onPlayheadDeltaMs?.invoke(deltaMs)

                flingVelocityMsPerSec *= exp((-dt * 3.2f).toDouble()).toFloat()

                if (abs(flingVelocityMsPerSec) > 45f) {
                    postOnAnimation(this)
                } else {
                    flingVelocityMsPerSec = 0f
                    lastFlingFrameTimeNs = 0L
                }
            }
        }

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float,
                ): Boolean {
                    val w = width.toFloat().coerceAtLeast(1f)
                    val pxPerMs = w / viewportMs
                    // distanceX ≈ 이전 터치 X − 현재 X. 오른쪽으로 드래그하면 음수 → playhead 감소(파형이 손과 같이 오른쪽으로).
                    val deltaMs = distanceX / pxPerMs
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "onScroll distanceX=$distanceX deltaMs=$deltaMs pxPerMs=$pxPerMs")
                    }
                    onPlayheadDeltaMs?.invoke(deltaMs)
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    val w = width.toFloat().coerceAtLeast(1f)
                    val pxPerMs = w / viewportMs
                    stopFlingOnlyVelocity()
                    // velocityX: 오른쪽 방향이 양수. drag와 같은 체감이 되도록 스크롤과 반대 부호로 ms/s 환산.
                    flingVelocityMsPerSec = -velocityX / pxPerMs * 0.55f
                    lastFlingFrameTimeNs = 0L
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "onFling velocityX=$velocityX flingVelocityMsPerSec=$flingVelocityMsPerSec pxPerMs=$pxPerMs",
                        )
                    }
                    if (abs(flingVelocityMsPerSec) > 120f) {
                        postOnAnimation(flingRunnable)
                    }
                    return true
                }
            },
        )

    fun bind(
        samples: List<Float>,
        playheadMs: Long,
        totalRecordedMs: Long,
        phase: RecordingPhase,
    ) {
        this.samples = samples
        this.playheadMs = playheadMs
        this.totalRecordedMs = totalRecordedMs
        this.phase = phase
        waveformInteractive =
            phase != RecordingPhase.RECORDING &&
                (phase == RecordingPhase.PAUSED || totalRecordedMs > 0)
        if (phase == RecordingPhase.RECORDING) {
            stopFling()
        }
        invalidate()
    }

    private fun stopFlingOnlyVelocity() {
        removeCallbacks(flingRunnable)
        flingVelocityMsPerSec = 0f
        lastFlingFrameTimeNs = 0L
    }

    private fun stopFling() {
        stopFlingOnlyVelocity()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val plotBottom = (h - scrollbarHeightPx).coerceAtLeast(1f)
        val centerX = w / 2f
        val pxPerMs = w / viewportMs

        val midY = plotBottom / 2f
        canvas.drawLine(0f, midY, w, midY, baselinePaint)

        val count = samples.size
        if (count > 0) {
            val stepMs = 100L
            val stepPx = stepMs * pxPerMs
            val minBarDp = 3f * density
            val maxBarDp = 4f * density
            val minGapDp = 3f * density
            val maxGapDp = 5f * density
            val gap = (stepPx * 0.35f).coerceIn(minGapDp, maxGapDp)
            val barWidth = (stepPx - gap).coerceIn(minBarDp, maxBarDp)
            val cornerRadius = 2.5f * density
            val quietFloorPx = quietBarFloorDp * density
            val maxBar = plotBottom * 0.58f
            for (i in 0 until count) {
                val tMs = i * stepMs
                val center = centerX + (tMs - playheadMs) * pxPerMs
                val x = center - (barWidth / 2f)
                if (x + barWidth < 0f || x > w) continue
                val amp = samples[i]
                val shapedAmp = amp.coerceIn(0f, 1f).pow(0.72f)
                val barHeight =
                    quietFloorPx + (maxBar - quietFloorPx) * shapedAmp
                val top = (plotBottom - barHeight) / 2f
                val bottom = top + barHeight
                val isPlayed = tMs.toLong() <= playheadMs
                val paint = if (isPlayed) playedBarPaint else barPaint
                canvas.drawRoundRect(x, top, x + barWidth, bottom, cornerRadius, cornerRadius, paint)
            }
        }

        val cursorHalf = 0.45f * density + 0.5f
        canvas.drawRect(centerX - cursorHalf, 0f, centerX + cursorHalf, plotBottom, centerLinePaint)

        drawScrollbar(canvas, w, h, plotBottom)
    }

    private fun drawScrollbar(canvas: Canvas, w: Float, fullH: Float, plotBottom: Float) {
        val pad = 2f * density
        val trackLeft = pad
        val trackRight = w - pad
        val trackW = (trackRight - trackLeft).coerceAtLeast(1f)
        val trackTop = plotBottom + 1f * density
        val trackBottom = fullH - 1f * density
        if (trackBottom <= trackTop) return

        canvas.drawRoundRect(
            trackLeft,
            trackTop,
            trackRight,
            trackBottom,
            scrollbarCornerPx,
            scrollbarCornerPx,
            scrollbarTrackPaint,
        )

        val spanMs = maxOf(totalRecordedMs, viewportMs.toLong(), 1L).toFloat()
        val thumbW = ((viewportMs / spanMs) * trackW).coerceIn(thumbMinPx, trackW)
        val playheadRatio = (playheadMs.toFloat() / spanMs).coerceIn(0f, 1f)
        val thumbCenter = playheadRatio * trackW
        var thumbLeft = trackLeft + thumbCenter - thumbW / 2f
        thumbLeft = thumbLeft.coerceIn(trackLeft, trackRight - thumbW)

        canvas.drawRoundRect(
            thumbLeft,
            trackTop,
            thumbLeft + thumbW,
            trackBottom,
            scrollbarCornerPx,
            scrollbarCornerPx,
            scrollbarThumbPaint,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!waveformInteractive || onPlayheadDeltaMs == null) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopFlingOnlyVelocity()
                onInteractionBegin?.invoke()
                lastFlingFrameTimeNs = 0L
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        stopFling()
        super.onDetachedFromWindow()
    }
}
