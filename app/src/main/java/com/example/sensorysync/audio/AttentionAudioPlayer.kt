package com.example.sensorysync.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.*

class AttentionAudioPlayer {
    @Volatile
    private var currentChimeTrack: AudioTrack? = null

    // Cache pre-synthesized pop PCM buffers for 5 pitch variations (0.8x to 1.4x)
    private val popBuffers = ConcurrentHashMap<Int, ShortArray>()
    private val sampleRate = 44100

    init {
        // Pre-generate 5 pitch variations in background for 0ms latency bubble pops
        thread(start = true, name = "PreGenPopBuffers") {
            val pitches = listOf(0.85f, 0.95f, 1.05f, 1.18f, 1.32f)
            for (idx in pitches.indices) {
                popBuffers[idx] = generateBubblePopPcm(pitches[idx])
            }
        }
    }

    private fun generateBubblePopPcm(pitchFactor: Float): ShortArray {
        val durationSec = 0.085f // 85ms crisp pop
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples * 2) // Stereo (L, R)

        val baseFreq = 420f * pitchFactor
        val peakFreq = 1250f * pitchFactor
        val chirpDuration = 0.014f // 14ms upward chirp

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate.toFloat()

            // 1. Frequency trajectory: rapid upward pop sweep then resonant cavity ring
            val freq = if (t < chirpDuration) {
                baseFreq + (peakFreq - baseFreq) * (t / chirpDuration)
            } else {
                baseFreq * 1.35f * exp(-(t - chirpDuration) * 18f) + baseFreq * 0.85f
            }

            // Phase accumulation approximation for clean pitch sweep
            val phase = 2.0 * PI * freq * t

            // 2. Multi-harmonic pop acoustics (fundamental + cavity 2nd harmonic + snap transient)
            val f1 = sin(phase).toFloat()
            val f2 = sin(phase * 1.95).toFloat() * 0.35f
            val snap = sin(2.0 * PI * 3400.0 * pitchFactor * t).toFloat() * exp(-t * 220f) * 0.28f

            // 3. Crisp acoustic envelope: 3ms ultra-fast attack, steep organic bubble decay
            val attack = (t / 0.003f).coerceIn(0f, 1f)
            val decay = exp(-t * 52f)
            val envelope = attack * decay

            val rawWave = ((f1 + f2) * 0.72f + snap) * envelope
            val sampleVal = (rawWave * 0.65f * 32767f).toInt().coerceIn(-32768, 32767).toShort()

            val bufIdx = i * 2
            buffer[bufIdx] = sampleVal     // Left
            buffer[bufIdx + 1] = sampleVal // Right
        }
        return buffer
    }

    fun playBubblePop(pitchFactor: Float = 1.0f, volume: Float = 0.85f) {
        thread(start = true, name = "BubblePopSound") {
            try {
                val clampedPitch = pitchFactor.coerceIn(0.75f, 1.45f)
                val pitchKey = when {
                    clampedPitch < 0.90f -> 0
                    clampedPitch < 1.00f -> 1
                    clampedPitch < 1.12f -> 2
                    clampedPitch < 1.25f -> 3
                    else -> 4
                }

                val pcmData = popBuffers[pitchKey] ?: generateBubblePopPcm(clampedPitch)
                val vol = volume.coerceIn(0.1f, 1.0f)

                val scaledBuffer = if (vol < 0.98f) {
                    ShortArray(pcmData.size) { i ->
                        (pcmData[i] * vol).toInt().toShort()
                    }
                } else {
                    pcmData
                }

                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(scaledBuffer.size * 2, minBufSize))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(scaledBuffer, 0, scaledBuffer.size)
                track.play()

                Thread.sleep(110L)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    fun playAttentionChime(volume: Float = 0.85f) {
        thread(start = true, name = "SensoryAttentionSound") {
            try {
                try {
                    currentChimeTrack?.stop()
                    currentChimeTrack?.release()
                } catch (_: Exception) {}

                val totalDurationSec = 1.8f
                val totalSamples = (sampleRate * totalDurationSec).toInt()
                val buffer = ShortArray(totalSamples * 2)

                val notes = listOf(
                    Triple(523.25f, 0.00f, 0.15f),  // C5 (left)
                    Triple(659.25f, 0.14f, 0.32f),  // E5 (mid-left)
                    Triple(783.99f, 0.28f, 0.50f),  // G5 (center)
                    Triple(987.77f, 0.42f, 0.68f),  // B5 (mid-right)
                    Triple(1046.50f, 0.56f, 0.85f), // C6 (right)
                    Triple(1318.51f, 0.70f, 0.50f), // E6 (apex center)
                    Triple(1567.98f, 0.84f, 0.50f)  // G6 shimmer (center)
                )

                val noteDecaySec = 0.60f
                val vol = volume.coerceIn(0.05f, 1.0f) * 0.40f

                for (n in notes) {
                    val (freq, startSec, pan) = n
                    val startSample = (startSec * sampleRate).toInt()
                    val noteSamples = (noteDecaySec * sampleRate).toInt()

                    for (i in 0 until noteSamples) {
                        val sampleIdx = startSample + i
                        if (sampleIdx >= totalSamples) break

                        val t = i.toFloat() / sampleRate.toFloat()
                        val attack = (t / 0.010f).coerceIn(0f, 1f)
                        val decay = exp(-t * 5.2f)
                        val envelope = attack * decay

                        val f1 = sin(2.0 * PI * freq * t).toFloat()
                        val f2 = sin(2.0 * PI * freq * 2.0 * t).toFloat() * 0.42f
                        val f3 = sin(2.0 * PI * freq * 3.01 * t).toFloat() * 0.22f
                        val f4 = sin(2.0 * PI * freq * 4.20 * t).toFloat() * 0.12f
                        val wave = (f1 + f2 + f3 + f4) * envelope * vol

                        val leftGain = cos(pan * PI.toFloat() / 2f)
                        val rightGain = sin(pan * PI.toFloat() / 2f)

                        val leftSample = (wave * leftGain * 32767f).toInt().coerceIn(-32768, 32767)
                        val rightSample = (wave * rightGain * 32767f).toInt().coerceIn(-32768, 32767)

                        val bufIdx = sampleIdx * 2
                        buffer[bufIdx] = (buffer[bufIdx] + leftSample).coerceIn(-32768, 32767).toShort()
                        buffer[bufIdx + 1] = (buffer[bufIdx + 1] + rightSample).coerceIn(-32768, 32767).toShort()
                    }
                }

                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(buffer.size * 2, minBufSize))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                currentChimeTrack = track
                track.write(buffer, 0, buffer.size)
                track.play()

                Thread.sleep((totalDurationSec * 1000).toLong() + 250L)
                try {
                    track.stop()
                    track.release()
                    if (currentChimeTrack === track) {
                        currentChimeTrack = null
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        try {
            currentChimeTrack?.stop()
            currentChimeTrack?.release()
            currentChimeTrack = null
        } catch (_: Exception) {}
    }
}
