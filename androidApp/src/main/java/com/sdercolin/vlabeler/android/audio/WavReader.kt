package com.sdercolin.vlabeler.android.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Lightweight PCM WAV reader for Android waveform previews; replaces javax.sound.sampled desktop code. */
data class Waveform(val sampleRate: Int, val channels: Int, val peaks: List<Float>)

fun readWavPeaks(input: InputStream, maxPeaks: Int = 2048): Waveform {
    val bytes = input.readBytes()
    require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Only RIFF/WAVE PCM files are supported" }
    var offset = 12
    var channels = 1
    var sampleRate = 44100
    var bitsPerSample = 16
    var dataOffset = -1
    var dataSize = 0
    while (offset + 8 <= bytes.size) {
        val id = String(bytes, offset, 4)
        val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val body = offset + 8
        when (id) {
            "fmt " -> {
                val fmt = ByteBuffer.wrap(bytes, body, size).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = fmt.short.toInt()
                require(audioFormat == 1) { "Only uncompressed PCM WAV is supported" }
                channels = fmt.short.toInt()
                sampleRate = fmt.int
                fmt.int; fmt.short
                bitsPerSample = fmt.short.toInt()
            }
            "data" -> { dataOffset = body; dataSize = size; break }
        }
        offset = body + size + (size % 2)
    }
    require(dataOffset >= 0) { "Missing WAV data chunk" }
    val sampleBytes = bitsPerSample / 8
    val frameBytes = sampleBytes * channels
    val frames = dataSize / frameBytes
    val bucket = (frames / maxPeaks).coerceAtLeast(1)
    val peaks = mutableListOf<Float>()
    var frame = 0
    while (frame < frames) {
        var peak = 0f
        repeat(bucket) {
            if (frame >= frames) return@repeat
            val base = dataOffset + frame * frameBytes
            repeat(channels) { ch ->
                val p = base + ch * sampleBytes
                val value = when (bitsPerSample) {
                    8 -> (bytes[p].toInt() and 0xff) - 128
                    16 -> ByteBuffer.wrap(bytes, p, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    24 -> ((bytes[p].toInt() and 0xff) or ((bytes[p + 1].toInt() and 0xff) shl 8) or (bytes[p + 2].toInt() shl 16))
                    32 -> ByteBuffer.wrap(bytes, p, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    else -> 0
                }
                peak = maxOf(peak, abs(value / (1 shl (bitsPerSample - 1)).toFloat()))
            }
            frame++
        }
        peaks += peak.coerceIn(0f, 1f)
    }
    return Waveform(sampleRate, channels, peaks)
}
