package com.example.sensorysync.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class AttentionAudioPlayer {
    @Volatile
    private var currentTrack: AudioTrack? = null

    fun playAttentionChime(volume: Float = 0.85f) {
        thread(start = true, name = "SensoryAttentionSound") {
            try {
                // Stop any previous playing sound
                try {
                    currentTrack?.stop()
                    currentTrack?.release()
                } catch (_: Exception) {}

                val sampleRate = 44100
                val totalDurationSec = 1.8f
                val totalSamples = (sampleRate * totalDurationSec).toInt()
                val buffer = ShortArray(totalSamples * 2) // Stereo (Left, Right)

                // Melodic Pentatonic chime arpeggio: C5 -> E5 -> G5 -> B5 -> C6 -> E6 -> G6 sparkle
                // Triple: (Frequency in Hz, Start Time in sec, Stereo Pan 0.0=Left, 0.5=Center, 1.0=Right)
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
                        // Fast smooth 10ms attack, exponential acoustic bell decay
                        val attack = (t / 0.010f).coerceIn(0f, 1f)
                        val decay = exp(-t * 5.2f)
                        val envelope = attack * decay

                        // Harmonically tuned bell overtones:
                        // 1.0x fundamental + 2.0x crystal octave + 3.01x bell harmonic + 4.2x celestial sparkle
                        val f1 = sin(2.0 * PI * freq * t).toFloat()
                        val f2 = sin(2.0 * PI * freq * 2.0 * t).toFloat() * 0.42f
                        val f3 = sin(2.0 * PI * freq * 3.01 * t).toFloat() * 0.22f
                        val f4 = sin(2.0 * PI * freq * 4.20 * t).toFloat() * 0.12f
                        val wave = (f1 + f2 + f3 + f4) * envelope * vol

                        // Constant-power stereo panning
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

                currentTrack = track
                track.write(buffer, 0, buffer.size)
                track.play()

                // Sleep duration + small tail before cleaning up
                Thread.sleep((totalDurationSec * 1000).toLong() + 250L)
                try {
                    track.stop()
                    track.release()
                    if (currentTrack === track) {
                        currentTrack = null
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        try {
            currentTrack?.stop()
            currentTrack?.release()
            currentTrack = null
        } catch (_: Exception) {}
    }
}
