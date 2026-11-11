package com.example.voicechanger

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * 在后台线程录制 16-bit 单声道 PCM。
 * 回调都发生在录音线程上，调用方需要自己切回主线程。
 */
class PcmRecorder(
    private val sampleRate: Int,
    private val maxSeconds: Int,
    private val onTick: (seconds: Float) -> Unit,
    private val onFinished: (pcm: ShortArray) -> Unit,
    private val onError: () -> Unit,
) {
    private val running = AtomicBoolean(false)

    @SuppressLint("MissingPermission") // 调用前由 Activity 检查/申请权限
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            onError()
            return
        }
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, sampleRate),
            )
        } catch (e: Exception) {
            onError()
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError()
            return
        }

        running.set(true)
        Thread {
            val data = ShortArray(sampleRate * maxSeconds)
            val chunk = ShortArray(2048)
            var total = 0
            var failed = false
            try {
                record.startRecording()
                while (running.get() && total < data.size) {
                    val n = record.read(chunk, 0, min(chunk.size, data.size - total))
                    if (n > 0) {
                        System.arraycopy(chunk, 0, data, total, n)
                        total += n
                        onTick(total.toFloat() / sampleRate)
                    } else if (n < 0) {
                        failed = true
                        break
                    }
                }
            } catch (e: Exception) {
                failed = true
            } finally {
                try {
                    record.stop()
                } catch (e: Exception) {
                    // 已经停止，忽略
                }
                record.release()
            }
            running.set(false)
            if (failed && total == 0) onError() else onFinished(data.copyOf(total))
        }.start()
    }

    fun stop() {
        running.set(false)
    }
}
