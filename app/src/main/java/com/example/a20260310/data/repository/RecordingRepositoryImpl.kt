package com.example.a20260310.data.repository

import android.media.MediaRecorder

class RecordingRepositoryImpl : RecordingRepository {
    private var mediaRecorder: MediaRecorder? = null

    companion object {
        /** AAC — MPEG_4(.m4a)에서 일반적으로 안정적인 모노 음성 품질 */
        private const val AUDIO_BIT_RATE = 192_000

        private const val SAMPLE_RATE_HZ = 44_100
        private const val CHANNEL_COUNT = 1
    }

    override fun start(outputPath: String) {
        mediaRecorder =
            MediaRecorder().apply {
                // MIC: 원음에 가깝게 / VOICE_RECOGNITION: 음성·STT에 맞춘 AGC·노이즈 억제
                // 업로드 후 STT이므로 음성 명료도 우선해 VOICE_RECOGNITION 사용
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(AUDIO_BIT_RATE)
                setAudioSamplingRate(SAMPLE_RATE_HZ)
                setAudioChannels(CHANNEL_COUNT)
                setOutputFile(outputPath)
                prepare()
                start()
            }
    }

    override fun pause() {
        try {
            mediaRecorder?.pause()
        } catch (_: Exception) {
        }
    }

    override fun resume() {
        try {
            mediaRecorder?.resume()
        } catch (_: Exception) {
        }
    }

    override fun stop() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            // stop() can throw when recorder was not fully initialized.
        } finally {
            mediaRecorder = null
        }
    }

    override fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
