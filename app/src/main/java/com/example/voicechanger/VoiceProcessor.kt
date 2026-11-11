package com.example.voicechanger

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯 Kotlin 的离线变声引擎（不依赖 Android API，可在 JVM 上直接测试）。
 *
 * 流程：
 *  1. WSOLA 时间拉伸（保持音高，改变时长）
 *  2. 抗混叠低通
 *  3. 变速重采样（把拉伸掉的时长"读"回来，同时音高和共振峰一起上移；可叠加音高颤动）
 *  4. 音色修饰：明亮度、气声、音量颤动
 *  5. 防削波
 */
object VoiceProcessor {

    fun process(input: ShortArray, sampleRate: Int, fx: VoiceEffect, extraSemitones: Float = 0f): ShortArray {
        if (input.size < 8) return input.copyOf()

        val x = FloatArray(input.size) { input[it] / 32768f }
        val ratio = 2.0.pow(((fx.semitones + extraSemitones) / 12f).toDouble()).toFloat()
        val stretch = ratio / fx.tempo

        var y = if (abs(stretch - 1f) < 1e-3f) x else wsola(x, stretch, sampleRate)

        val needResample = abs(ratio - 1f) >= 1e-3f || fx.vibratoDepth > 0f
        if (needResample) {
            if (ratio > 1f) {
                val cutoff = 0.45f * sampleRate / ratio
                y = lowPass(lowPass(y, cutoff, sampleRate), cutoff, sampleRate)
            }
            y = resample(y, ratio, fx.vibratoDepth, fx.vibratoRateHz, sampleRate)
        }

        y = shapeTone(y, fx, sampleRate)
        return toShorts(limit(y))
    }

    // ---------------------------------------------------------------- WSOLA

    /** 把 x 的时长拉伸为 stretch 倍（>1 变长），音高不变。 */
    internal fun wsola(x: FloatArray, stretch: Float, sr: Int): FloatArray {
        val n = x.size
        val hs = max(64, (0.015f * sr).roundToInt())     // 合成跳步，约 15ms
        val win = 2 * hs                                  // 窗长，50% 重叠
        val tol = (0.012f * sr).roundToInt()              // 搜索范围，约 ±12ms，覆盖 >80Hz 的基频周期
        val outLen = max(1, (n * stretch).roundToInt())

        val xp = FloatArray(n + 3 * win)
        System.arraycopy(x, 0, xp, 0, n)

        val window = FloatArray(win) { (0.5 - 0.5 * cos(2.0 * PI * it / win)).toFloat() }
        val out = FloatArray(outLen + win)
        val anaHop = hs / stretch

        var prev = 0
        var k = 0
        var outPos = 0
        while (outPos < outLen) {
            var best = 0
            if (k > 0) {
                val nominal = (k * anaHop).roundToInt()
                val lo = max(0, nominal - tol)
                val hi = min(n, nominal + tol)
                val refStart = prev + hs                  // 上一段"自然延续"的位置
                var bestScore = -Float.MAX_VALUE
                best = min(max(nominal, lo), hi)
                var c = lo
                while (c <= hi) {
                    var dot = 0f
                    var e = 1e-6f
                    var i = 0
                    while (i < hs) {                      // 隔 4 点取样，速度优先
                        val a = xp[refStart + i]
                        val b = xp[c + i]
                        dot += a * b
                        e += b * b
                        i += 4
                    }
                    val score = dot / sqrt(e)
                    if (score > bestScore) {
                        bestScore = score
                        best = c
                    }
                    c += 2
                }
            }
            for (i in 0 until win) {
                val w = if (k == 0 && i < hs) 1f else window[i]   // 第一帧前半段不淡入
                out[outPos + i] += w * xp[best + i]
            }
            prev = best
            outPos += hs
            k++
        }
        return out.copyOf(outLen)
    }

    // ------------------------------------------------------------ 重采样

    private fun resample(x: FloatArray, ratio: Float, vibDepth: Float, vibRate: Float, sr: Int): FloatArray {
        if (x.size < 8) return x
        val maxOut = (x.size / ratio * 1.1f).toInt() + 8
        val out = FloatArray(maxOut)
        val limit = x.size - 3.0
        var pos = 0.0
        var n = 0
        while (n < maxOut && pos < limit) {
            out[n] = hermite(x, pos)
            val mod = if (vibDepth > 0f) 1.0 + vibDepth * sin(2.0 * PI * vibRate * n / sr) else 1.0
            pos += ratio * mod
            n++
        }
        return out.copyOf(n)
    }

    /** 4 点 Hermite 插值。要求 0 <= pos < x.size - 3。 */
    private fun hermite(x: FloatArray, pos: Double): Float {
        val i = floor(pos).toInt()
        val f = (pos - i).toFloat()
        val xm1 = if (i >= 1) x[i - 1] else x[0]
        val x0 = x[i]
        val x1 = x[i + 1]
        val x2 = x[i + 2]
        val c1 = 0.5f * (x1 - xm1)
        val c2 = xm1 - 2.5f * x0 + 2f * x1 - 0.5f * x2
        val c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1)
        return ((c3 * f + c2) * f + c1) * f + x0
    }

    private fun lowPass(x: FloatArray, cutoffHz: Float, sr: Int): FloatArray {
        val w0 = 2.0 * PI * cutoffHz / sr
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * 0.7071)
        val a0 = 1.0 + alpha
        val b0 = ((1.0 - cosW) / 2.0) / a0
        val b1 = (1.0 - cosW) / a0
        val b2 = b0
        val a1 = (-2.0 * cosW) / a0
        val a2 = (1.0 - alpha) / a0

        val out = FloatArray(x.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val xi = x[i].toDouble()
            val yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = xi
            y2 = y1
            y1 = yi
            out[i] = yi.toFloat()
        }
        return out
    }

    // ------------------------------------------------------------ 音色修饰

    private fun shapeTone(x: FloatArray, fx: VoiceEffect, sr: Int): FloatArray {
        val y = x.copyOf()

        if (fx.brightness != 0f) {
            val a = exp(-2.0 * PI * 3000.0 / sr).toFloat()
            var lp = 0f
            for (i in y.indices) {
                val v = y[i]
                lp = (1f - a) * v + a * lp
                y[i] = v + fx.brightness * (v - lp)
            }
        }

        if (fx.breath > 0f) {
            val rnd = Random(42)
            val att = exp(-1.0 / (0.005 * sr)).toFloat()
            val rel = exp(-1.0 / (0.060 * sr)).toFloat()
            var env = 0f
            var prevNoise = 0f
            for (i in y.indices) {
                val ax = abs(y[i])
                env = if (ax > env) att * env + (1f - att) * ax else rel * env + (1f - rel) * ax
                val noise = rnd.nextFloat() * 2f - 1f
                val hp = noise - prevNoise                 // 简单高通：气声偏高频
                prevNoise = noise
                y[i] += fx.breath * 2f * env * hp
            }
        }

        if (fx.tremoloDepth > 0f) {
            for (i in y.indices) {
                val lfo = 0.5f - 0.5f * cos(2.0 * PI * fx.tremoloRateHz * i / sr).toFloat()
                y[i] *= 1f - fx.tremoloDepth * lfo
            }
        }
        return y
    }

    private fun limit(x: FloatArray): FloatArray {
        var peak = 0f
        for (v in x) peak = max(peak, abs(v))
        if (peak > 0.97f) {
            val g = 0.97f / peak
            for (i in x.indices) x[i] *= g
        }
        return x
    }

    private fun toShorts(x: FloatArray): ShortArray =
        ShortArray(x.size) { (x[it] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort() }
}
