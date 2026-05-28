package com.test.demospatialaudio.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    private const val TAG = "AudioDecoder"

    data class DecodedAudio(
        val monoSamples: FloatArray,
        val sampleRate: Int
    )

    fun decode(context: Context, fileName: String?): DecodedAudio? {
        val extractor = MediaExtractor()
        fileName?.let {
            if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
                try {
                    extractor.setDataSource(fileName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set data source for URL", e)
                    return null
                }
            } else {
                val afd = AudioLibraryService.resolveFileDescriptor(context, fileName)
                if (afd == null) {
                    Log.e(TAG, "Audio file not found: $fileName")
                    return null
                }
                try {
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set data source for $fileName", e)
                    afd.close()
                    return null
                }
                afd.close()
            }
        }

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIndex = i
                format = f
                break
            }
        }

        if (audioTrackIndex < 0 || format == null) {
            Log.e(TAG, "No audio track found in $fileName")
            extractor.release()
            return null
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec: MediaCodec
        try {
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create decoder for $fileName", e)
            extractor.release()
            return null
        }

        val samples = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    readPcmSamples(outputBuffer, channelCount, samples)

                    codec.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // format changed, continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decoding error for $fileName", e)
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        if (samples.isEmpty()) {
            Log.e(TAG, "No samples decoded from $fileName")
            return null
        }

        var monoSamples = samples.toFloatArray()
        if (sampleRate != SpatialAudioEngine.SAMPLE_RATE) {
            monoSamples = resample(monoSamples, sampleRate, SpatialAudioEngine.SAMPLE_RATE)
        }

        Log.i(TAG, "Decoded $fileName: ${monoSamples.size} samples at ${SpatialAudioEngine.SAMPLE_RATE}Hz")
        return DecodedAudio(monoSamples, SpatialAudioEngine.SAMPLE_RATE)
    }

    private fun readPcmSamples(
        buffer: ByteBuffer,
        channelCount: Int,
        out: MutableList<Float>
    ) {
        val ordered = buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = ordered.asShortBuffer()
        val shortCount = shortBuffer.remaining()

        if (channelCount == 1) {
            for (i in 0 until shortCount) {
                out.add(shortBuffer.get() / 32768f)
            }
        } else {
            var i = 0
            while (i + channelCount - 1 < shortCount) {
                var sum = 0f
                for (ch in 0 until channelCount) {
                    sum += shortBuffer.get() / 32768f
                }
                out.add(sum / channelCount)
                i += channelCount
            }
        }
    }

    private fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outputLength = (input.size / ratio).toInt()
        val output = FloatArray(outputLength)

        for (i in output.indices) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()
            output[i] = if (srcIdx + 1 < input.size) {
                input[srcIdx] * (1f - frac) + input[srcIdx + 1] * frac
            } else {
                input[srcIdx.coerceIn(input.indices)]
            }
        }
        return output
    }
}
