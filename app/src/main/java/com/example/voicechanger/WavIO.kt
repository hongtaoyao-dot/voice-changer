package com.example.voicechanger

import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit 单声道 PCM WAV 写出。 */
object WavIO {

    fun write(file: File, pcm: ShortArray, sampleRate: Int) {
        file.outputStream().buffered().use { writeTo(it, pcm, sampleRate) }
    }

    fun writeTo(out: OutputStream, pcm: ShortArray, sampleRate: Int) {
        val dataLen = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1.toShort())                 // PCM
        bb.putShort(1.toShort())                 // 单声道
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * 2)                // byte rate
        bb.putShort(2.toShort())                 // block align
        bb.putShort(16.toShort())                // bits per sample
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataLen)
        bb.asShortBuffer().put(pcm)
        out.write(bb.array())
        out.flush()
    }
}
