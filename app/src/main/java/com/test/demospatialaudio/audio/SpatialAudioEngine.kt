package com.test.demospatialaudio.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.test.demospatialaudio.domain.model.ReverbPreset
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SpatialAudioEngine(private val context: Context) {

    companion object {
        const val TAG = "SpatialAudioEngine"
        const val SAMPLE_RATE = 44100
        const val BUFFER_FRAMES = 1024
    }

    private class AtomPlayer(val pcmData: FloatArray) {
        var playbackPos: Double = 0.0
        @Volatile var x: Float = 0f
        @Volatile var y: Float = 0f
        @Volatile var z: Float = 0f
        @Volatile var volume: Float = 1f
        @Volatile var isMuted: Boolean = false
        @Volatile var rate: Float = 1f
        @Volatile var reverbBlend: Float = 0.3f
    }

    private val players = ConcurrentHashMap<String, AtomPlayer>()
    val levels = AtomLevels()

    private val reverb = ReverbProcessor()
    @Volatile private var globalWet: Float = 0.5f

    private var audioTrack: AudioTrack? = null
    private var mixThread: Thread? = null
    @Volatile private var isRunning = false
    @Volatile private var isPaused = false
    @Volatile private var masterVolume = 1f
    @Volatile private var soloId: String? = null
    @Volatile private var listenerYaw = 0f
    @Volatile private var outputRms: Float = 0f
    private val pauseLock = Object()

    private var referenceDistance = 1f
    private var maximumDistance = CoordinateMapper.ROOM_RADIUS * 3
    private var rolloffFactor = 2.5f

    @Volatile private var isRecording = false
    private var recordingStream: DataOutputStream? = null
    private var recordingFile: File? = null
    private var recordedFrames = 0L

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var transientLossPaused = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                setMasterPaused(true)
            }
        }
    }
    private var noisyRegistered = false

    @Volatile private var previewAtomId: String? = null

    fun start() {
        if (isRunning) return
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied")
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(BUFFER_FRAMES * 4)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        registerNoisyReceiver()
        audioTrack?.play()
        isRunning = true

        mixThread = Thread(::mixLoop, "SpatialAudioMixer").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        Log.i(TAG, "Audio engine started")
    }

    fun stop() {
        isRunning = false
        synchronized(pauseLock) { pauseLock.notifyAll() }
        mixThread?.join(2000)
        mixThread = null

        if (isRecording) stopRecordingInternal()

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        levels.clear()
        unregisterNoisyReceiver()
        abandonAudioFocus()
        Log.i(TAG, "Audio engine stopped")
    }

    fun loadAtom(id: String, fileName: String?): Boolean {
        if (players.containsKey(id)) return true
        val decoded = AudioDecoder.decode(context, fileName) ?: return false
        players[id] = AtomPlayer(decoded.monoSamples)
        return true
    }

    fun unloadAtom(id: String) {
        players.remove(id)
        levels.remove(id)
        if (soloId == id) soloId = null
        if (previewAtomId == id) previewAtomId = null
    }

    fun updateAtomPosition(id: String, x: Float, y: Float, z: Float) {
        players[id]?.let {
            it.x = x
            it.y = y
            it.z = z
        }
    }

    fun updateAtomVolume(id: String, volume: Float, isMuted: Boolean) {
        players[id]?.let {
            it.volume = volume.coerceIn(0f, 1f)
            it.isMuted = isMuted
        }
    }

    fun setAtomMuted(id: String, isMuted: Boolean, fallbackVolume: Float) {
        players[id]?.let {
            it.isMuted = isMuted
            it.volume = fallbackVolume.coerceIn(0f, 1f)
        }
    }

    fun setAtomRate(id: String, rate: Float) {
        players[id]?.rate = rate.coerceIn(0.25f, 4f)
    }

    fun setAtomReverbBlend(id: String, blend: Float) {
        players[id]?.reverbBlend = blend.coerceIn(0f, 1f)
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
    }

    fun setMasterPaused(paused: Boolean) {
        isPaused = paused
        if (!paused) {
            synchronized(pauseLock) { pauseLock.notifyAll() }
        } else {
            try { audioTrack?.pause() } catch (_: Exception) {}
        }
    }

    fun isMasterPaused(): Boolean = isPaused

    fun outputLevel(): Float = outputRms

    fun setSolo(id: String?) {
        soloId = id
    }

    fun updateListenerOrientation(yaw: Float) {
        listenerYaw = yaw
    }

    fun resetListener() {
        listenerYaw = 0f
    }

    fun applyReverb(preset: ReverbPreset, level: Float) {
        val cfg = ReverbProcessor.configFor(preset)
        reverb.configure(cfg.size, cfg.damping, level)
        globalWet = ((level + 12f) / 18f).coerceIn(0f, 1f)
    }

    fun previewAtom(id: String, fileName: String?): Boolean {
        stopPreview()
        if (!loadAtom(id, fileName)) return false
        players[id]?.let {
            it.x = 0f
            it.y = -0.4f
            it.z = 0f
            it.volume = 0.7f
            it.reverbBlend = 0.2f
        }
        previewAtomId = id
        return true
    }

    fun stopPreview() {
        previewAtomId?.let { unloadAtom(it) }
        previewAtomId = null
    }

    fun isPreviewActive(id: String): Boolean = previewAtomId == id

    fun startRecording(file: File) {
        if (isRecording) return
        try {
            recordingStream = DataOutputStream(BufferedOutputStream(FileOutputStream(file)))
            recordingFile = file
            writeWavHeaderPlaceholder(recordingStream!!)
            recordedFrames = 0
            isRecording = true
            Log.i(TAG, "Recording started: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    fun stopRecording(): File? {
        val file = recordingFile
        stopRecordingInternal()
        return file
    }

    fun isRecordingActive() = isRecording

    fun reset(atomDescriptors: List<AtomDescriptor>) {
        for (id in players.keys.toList()) {
            unloadAtom(id)
        }
        soloId = null
        for (desc in atomDescriptors) {
            if (loadAtom(desc.id, desc.fileName)) {
                updateAtomPosition(desc.id, desc.x, desc.y, desc.z)
                updateAtomVolume(desc.id, desc.volume, desc.isMuted)
                setAtomReverbBlend(desc.id, desc.reverbBlend)
            }
        }
        reverb.reset()
    }

    private fun mixLoop() {
        val dryL = FloatArray(BUFFER_FRAMES)
        val dryR = FloatArray(BUFFER_FRAMES)
        val wetInL = FloatArray(BUFFER_FRAMES)
        val wetInR = FloatArray(BUFFER_FRAMES)
        val wetOutL = FloatArray(BUFFER_FRAMES)
        val wetOutR = FloatArray(BUFFER_FRAMES)
        val outBuffer = ShortArray(BUFFER_FRAMES * 2)

        while (isRunning) {
            if (isPaused) {
                synchronized(pauseLock) {
                    while (isPaused && isRunning) {
                        try {
                            pauseLock.wait()
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return
                        }
                    }
                }
                if (!isRunning) return
                try { audioTrack?.play() } catch (_: Exception) {}
                continue
            }

            dryL.fill(0f); dryR.fill(0f)
            wetInL.fill(0f); wetInR.fill(0f)

            val yawRad = Math.toRadians(listenerYaw.toDouble()).toFloat()
            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)

            for ((id, player) in players) {
                val effectiveVol = computeEffectiveVolume(id, player)
                if (effectiveVol <= 0f) {
                    levels.update(id, 0f)
                    continue
                }

                val data = player.pcmData
                val dataLen = data.size
                if (dataLen == 0) continue

                val rx = player.x * cosYaw + player.y * sinYaw
                val ry = -player.x * sinYaw + player.y * cosYaw

                val azimuth = atan2(rx, -ry)
                val pan = sin(azimuth)

                val angle = (pan + 1f) / 2f * (PI.toFloat() / 2f)
                val leftGain = cos(angle)
                val rightGain = sin(angle)

                val dist = sqrt(
                    player.x * player.x + player.y * player.y + player.z * player.z
                ) * CoordinateMapper.ROOM_RADIUS
                val atten = if (dist <= referenceDistance) 1f
                else {
                    val d = dist.coerceAtMost(maximumDistance)
                    referenceDistance / (referenceDistance + rolloffFactor * (d - referenceDistance))
                }

                val finalGain = effectiveVol * atten
                val lG = leftGain * finalGain
                val rG = rightGain * finalGain
                val sendL = lG * player.reverbBlend
                val sendR = rG * player.reverbBlend

                var rmsSum = 0f
                for (i in 0 until BUFFER_FRAMES) {
                    val pos = player.playbackPos
                    val idx = pos.toInt() % dataLen
                    val sample = data[idx]
                    player.playbackPos = (pos + player.rate) % dataLen.toDouble()

                    rmsSum += sample * sample
                    dryL[i] += sample * lG
                    dryR[i] += sample * rG
                    wetInL[i] += sample * sendL
                    wetInR[i] += sample * sendR
                }

                levels.update(id, sqrt(rmsSum / BUFFER_FRAMES))
            }

            reverb.processBlock(wetInL, wetInR, wetOutL, wetOutR, BUFFER_FRAMES)

            var outSum = 0f
            val wetMul = globalWet
            for (i in 0 until BUFFER_FRAMES) {
                val l = ((dryL[i] + wetOutL[i] * wetMul) * masterVolume).coerceIn(-1f, 1f)
                val r = ((dryR[i] + wetOutR[i] * wetMul) * masterVolume).coerceIn(-1f, 1f)
                outSum += l * l + r * r
                outBuffer[i * 2] = (l * 32767f).toInt().toShort()
                outBuffer[i * 2 + 1] = (r * 32767f).toInt().toShort()
            }
            outputRms = sqrt(outSum / (BUFFER_FRAMES * 2))

            audioTrack?.write(outBuffer, 0, outBuffer.size)

            if (isRecording) {
                writeRecordingBuffer(outBuffer)
            }
        }
    }

    private fun computeEffectiveVolume(id: String, player: AtomPlayer): Float {
        if (player.isMuted) return 0f
        val solo = soloId
        if (solo != null && solo != id && id != previewAtomId) return 0f
        return player.volume.coerceIn(0f, 1f)
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change -> handleFocusChange(change) }
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = req
        val result = am.requestAudioFocus(req)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun handleFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                setMasterPaused(true)
                transientLossPaused = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!isPaused) {
                    transientLossPaused = true
                    setMasterPaused(true)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (transientLossPaused) {
                    transientLossPaused = false
                    setMasterPaused(false)
                }
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyRegistered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(noisyReceiver, filter)
            noisyRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register noisy receiver", e)
        }
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyRegistered) return
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}
        noisyRegistered = false
    }

    private fun stopRecordingInternal() {
        if (!isRecording) return
        isRecording = false
        try {
            recordingStream?.flush()
            recordingStream?.close()
            recordingFile?.let { finalizeWavHeader(it, recordedFrames) }
            Log.i(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
        recordingStream = null
        recordingFile = null
    }

    private fun writeRecordingBuffer(buffer: ShortArray) {
        try {
            val byteBuffer = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in buffer) byteBuffer.putShort(s)
            recordingStream?.write(byteBuffer.array())
            recordedFrames += buffer.size / 2
        } catch (e: Exception) {
            Log.e(TAG, "Recording write failed", e)
        }
    }

    private fun writeWavHeaderPlaceholder(stream: DataOutputStream) {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(0)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(2)
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2 * 2)
        buf.putShort(4)
        buf.putShort(16)
        buf.put("data".toByteArray())
        buf.putInt(0)
        stream.write(buf.array())
    }

    private fun finalizeWavHeader(file: File, frames: Long) {
        try {
            val dataSize = frames * 2 * 2
            val fileSize = dataSize + 36
            val raf = RandomAccessFile(file, "rw")
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            raf.seek(4)
            buf.clear(); buf.putInt(fileSize.toInt()); raf.write(buf.array())

            raf.seek(40)
            buf.clear(); buf.putInt(dataSize.toInt()); raf.write(buf.array())

            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize WAV header", e)
        }
    }
}

data class AtomDescriptor(
    val id: String,
    val fileName: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val volume: Float,
    val isMuted: Boolean,
    val reverbBlend: Float = 0.3f
)
