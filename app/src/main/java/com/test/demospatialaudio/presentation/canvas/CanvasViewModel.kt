package com.test.demospatialaudio.presentation.canvas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.test.demospatialaudio.DemoSpatialAudioApp
import com.test.demospatialaudio.audio.AtomAnimationDriver
import com.test.demospatialaudio.audio.AtomDescriptor
import com.test.demospatialaudio.audio.AudioLibraryEntry
import com.test.demospatialaudio.audio.AudioLibraryService
import com.test.demospatialaudio.audio.CoordinateMapper
import com.test.demospatialaudio.audio.PlaybackController
import com.test.demospatialaudio.audio.PlaybackEvents
import com.test.demospatialaudio.audio.SpatialAudioEngine
import com.test.demospatialaudio.domain.AppDatabase
import com.test.demospatialaudio.domain.model.AtomAnimationMode
import com.test.demospatialaudio.domain.model.AudioAtomEntity
import com.test.demospatialaudio.domain.model.ReverbPreset
import com.test.demospatialaudio.domain.model.SoundscapePresetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CanvasViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val presetId: String = savedStateHandle["presetId"]!!
    private val db = AppDatabase.getInstance(application)
    private val presetDao = db.presetDao()
    private val atomDao = db.atomDao()
    private val app = application as DemoSpatialAudioApp
    private val controller: PlaybackController = app.playback

    val engine: SpatialAudioEngine get() = controller.engine
    val animationDriver: AtomAnimationDriver get() = controller.animationDriver
    private val headTracking get() = controller.headTracking

    var preset by mutableStateOf<SoundscapePresetEntity?>(null)
        private set
    val atoms = mutableStateListOf<AudioAtomEntity>()
    val loadingAtomIds = mutableStateListOf<String>()
    var isEngineReady by mutableStateOf(false)
        private set
    var selectedAtomId by mutableStateOf<String?>(null)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var soloAtomId by mutableStateOf<String?>(null)
        private set
    var listenerYaw by mutableFloatStateOf(0f)
        private set
    var recenterPulse by mutableFloatStateOf(0f)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var snackbarMessage by mutableStateOf<String?>(null)
        private set
    var lastRecordingFile by mutableStateOf<File?>(null)
        private set
    var pendingUndoAtomId by mutableStateOf<String?>(null)
        private set
    var sleepTimerEndsAt by mutableStateOf<Long?>(null)
        private set
    var previewingId by mutableStateOf<String?>(null)
        private set

    private val pendingRemovals = mutableStateMapOf<String, AudioAtomEntity>()
    private val pendingRemovalJobs = mutableMapOf<String, Job>()

    private var sleepTimerJob: Job? = null

    val selectedAtom: AudioAtomEntity?
        get() = atoms.firstOrNull { it.id == selectedAtomId }

    fun isLoading(atomId: String): Boolean = loadingAtomIds.contains(atomId)

    init {
        viewModelScope.launch {
            PlaybackEvents.events.collect { event ->
                when (event) {
                    PlaybackEvents.Event.Pause -> if (!isPaused) toggleMasterPause()
                    PlaybackEvents.Event.Play -> if (isPaused) toggleMasterPause()
                    PlaybackEvents.Event.Stop -> {
                        engine.stop()
                        controller.onPlaybackStopped()
                    }
                }
            }
        }
        viewModelScope.launch {
            controller.state.collect { state ->
                if (state.isPaused != isPaused && state.presetId == presetId) {
                    isPaused = state.isPaused
                }
            }
        }
    }

    fun onAppear() {
        viewModelScope.launch {
            val data = presetDao.getPresetWithAtomsOnce(presetId) ?: return@launch
            preset = data.preset
            atoms.clear()
            atoms.addAll(data.atoms)

            val isAlreadyBound = controller.isBoundTo(presetId) && controller.state.value.isPlaying

            launch(Dispatchers.IO) {
                if (!isAlreadyBound) {
                    engine.stop()
                    animationDriver.clear()
                    controller.bindPreset(presetId, data.preset.name)
                    engine.start()
                    engine.applyReverb(data.preset.reverbPreset, data.preset.reverbLevel)
                    engine.setMasterVolume(0f)

                    val descriptors = data.atoms.map { atom ->
                        AtomDescriptor(
                            atom.id, playableSource(atom),
                            atom.x.toFloat(), atom.y.toFloat(), atom.z.toFloat(),
                            atom.volume, atom.isMuted, atom.reverbBlend
                        )
                    }
                    engine.reset(descriptors)
                }

                launch(Dispatchers.Main) {
                    if (!isAlreadyBound) {
                        for (atom in atoms) registerAnimation(atom)
                    }
                    animationDriver.startTicker(viewModelScope)
                    isEngineReady = true
                    headTracking.start()
                    isPaused = engine.isMasterPaused()
                    controller.bindPreset(presetId, data.preset.name)
                    controller.onPlaybackStarted()
                    app.preferences.setLastPresetId(presetId)
                }

                if (!isAlreadyBound) {
                    fadeMasterVolume(0f, data.preset.masterVolume, 800)
                }
            }
        }
    }

    fun onDisappear() {
        sleepTimerJob?.cancel()
        headTracking.stop()
        stopAllPreviews()
    }

    fun livePosition(atom: AudioAtomEntity): Triple<Double, Double, Double> {
        val live = animationDriver.livePositions[atom.id]
        return if (live != null) Triple(live.x, live.y, live.z)
        else Triple(atom.x, atom.y, atom.z)
    }

    fun positionTrail(atomId: String): List<AtomAnimationDriver.LivePosition>? {
        return animationDriver.history[atomId]
    }

    fun level(atom: AudioAtomEntity): Float {
        return animationDriver.atomLevels[atom.id] ?: 0f
    }

    fun outputLevel(): Float = engine.outputLevel()

    fun selectAtom(id: String?) {
        selectedAtomId = id
    }

    fun updatePosition(atomId: String, normalizedX: Double, normalizedY: Double) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        val clamped = CoordinateMapper.clampMinRadius(normalizedX, normalizedY)
        val updated = atoms[index].copy(x = clamped.first, y = clamped.second)
        atoms[index] = updated
        engine.updateAtomPosition(atomId, clamped.first.toFloat(), clamped.second.toFloat(), updated.z.toFloat())
        if (updated.animationMode != AtomAnimationMode.NONE) {
            registerAnimation(updated)
        }
    }

    fun updateElevation(atomId: String, z: Double) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        val clampedZ = z.coerceIn(-1.0, 1.0)
        val atom = atoms[index]
        val updated = atom.copy(z = clampedZ)
        atoms[index] = updated
        engine.updateAtomPosition(atomId, atom.x.toFloat(), atom.y.toFloat(), clampedZ.toFloat())
        if (updated.animationMode != AtomAnimationMode.NONE) {
            registerAnimation(updated)
        }
    }

    fun commitChanges() {
        viewModelScope.launch(Dispatchers.IO) {
            preset?.let { presetDao.update(it) }
            for (atom in atoms.toList()) {
                atomDao.update(atom)
            }
        }
    }

    fun updateVolume(atomId: String, volume: Float) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        atoms[index] = atoms[index].copy(volume = volume)
        engine.updateAtomVolume(atomId, volume, atoms[index].isMuted)
    }

    fun toggleMute(atomId: String) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        val atom = atoms[index]
        val newMuted = !atom.isMuted
        atoms[index] = atom.copy(isMuted = newMuted)
        engine.setAtomMuted(atomId, newMuted, atom.volume)
        commitChanges()
    }

    fun toggleSolo(atomId: String) {
        soloAtomId = if (soloAtomId == atomId) null else atomId
        engine.setSolo(soloAtomId)
    }

    fun updateReverbBlend(atomId: String, blend: Float) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        atoms[index] = atoms[index].copy(reverbBlend = blend)
        engine.setAtomReverbBlend(atomId, blend)
    }

    fun updateAnimation(atomId: String, mode: AtomAnimationMode? = null, speed: Double? = null, depth: Double? = null) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        var atom = atoms[index]
        if (mode != null) atom = atom.copy(animationModeOrdinal = mode.ordinal)
        if (speed != null) atom = atom.copy(animationSpeed = speed)
        if (depth != null) atom = atom.copy(animationDepth = depth)
        atoms[index] = atom
        commitChanges()
        registerAnimation(atom)
    }

    fun updateDisplayName(atomId: String, name: String) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        atoms[index] = atoms[index].copy(displayName = name)
        commitChanges()
    }

    fun toggleMasterPause() {
        isPaused = !isPaused
        engine.setMasterPaused(isPaused)
        controller.onPlaybackPaused(isPaused)
    }

    fun updateMasterVolume(volume: Float) {
        preset = preset?.copy(masterVolume = volume)
        engine.setMasterVolume(volume)
        commitChanges()
    }

    fun updateListenerYaw(yaw: Float) {
        listenerYaw = yaw
        engine.updateListenerOrientation(yaw)
    }

    fun recenterHeading() {
        headTracking.stop()
        headTracking.start()
        recenterPulse += 1f
    }

    fun applyReverb(reverbPreset: ReverbPreset) {
        preset = preset?.copy(reverbPresetOrdinal = reverbPreset.ordinal)
        val level = preset?.reverbLevel ?: -6f
        engine.applyReverb(reverbPreset, level)
        commitChanges()
    }

    fun updateReverbLevel(level: Float) {
        preset = preset?.copy(reverbLevel = level)
        val reverbPreset = preset?.reverbPreset ?: ReverbPreset.MEDIUM_ROOM
        engine.applyReverb(reverbPreset, level)
        commitChanges()
    }

    fun addAtomFromLibrary(entry: AudioLibraryEntry) {
        addAtom(entry.fileName, entry.displayName)
    }

    fun importAtom(uri: android.net.Uri) {
        val entry = AudioLibraryService.importFile(getApplication(), uri)
        if (entry != null) {
            addAtom(entry.fileName, entry.displayName)
        } else {
            importError = "We couldn't read that file. Try another format."
        }
    }

    fun addRemoteAtom(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return
        val atom = AudioAtomEntity(
            presetId = presetId,
            fileName = url,
            displayName = "Loading…",
            sourceUrl = url,
            x = 0.0, y = -0.5, z = 0.0,
            volume = 1f
        )
        atoms.add(atom)
        loadingAtomIds.add(atom.id)
        viewModelScope.launch(Dispatchers.IO) {
            atomDao.insert(atom)
            val loaded = engine.loadAtom(atom.id, url)
            val finalAtom = atom.copy(displayName = urlDisplayName(url))
            atomDao.update(finalAtom)
            launch(Dispatchers.Main) {
                val index = atoms.indexOfFirst { it.id == atom.id }
                if (index >= 0) atoms[index] = finalAtom
                loadingAtomIds.remove(atom.id)
                if (loaded) {
                    engine.updateAtomPosition(atom.id, finalAtom.x.toFloat(), finalAtom.y.toFloat(), finalAtom.z.toFloat())
                    engine.updateAtomVolume(atom.id, finalAtom.volume, finalAtom.isMuted)
                    engine.setAtomReverbBlend(atom.id, finalAtom.reverbBlend)
                    registerAnimation(finalAtom)
                    snackbarMessage = "New atom drifting in"
                } else {
                    atomDao.deleteById(atom.id)
                    atoms.removeAll { it.id == atom.id }
                    snackbarMessage = "Couldn't reach that link"
                }
            }
        }
    }

    fun previewLibraryEntry(entry: AudioLibraryEntry) {
        val current = previewingId
        if (current != null) {
            engine.stopPreview()
            previewingId = null
            if (current == previewIdFor(entry)) return
        }
        val id = previewIdFor(entry)
        val ok = engine.previewAtom(id, entry.fileName)
        if (ok) {
            previewingId = id
        } else {
            snackbarMessage = "Couldn't preview \"${entry.displayName}\""
        }
    }

    fun isPreviewing(entry: AudioLibraryEntry): Boolean = previewingId == previewIdFor(entry)

    fun stopAllPreviews() {
        if (previewingId == null) return
        engine.stopPreview()
        previewingId = null
    }

    private fun previewIdFor(entry: AudioLibraryEntry): String = "__preview__${entry.fileName}"

    fun removeAtom(atomId: String) {
        val index = atoms.indexOfFirst { it.id == atomId }
        if (index < 0) return
        val source = atoms[index]
        atoms.removeAt(index)
        engine.unloadAtom(atomId)
        animationDriver.unregister(atomId)
        if (soloAtomId == atomId) {
            soloAtomId = null
            engine.setSolo(null)
        }
        if (selectedAtomId == atomId) selectedAtomId = null

        pendingRemovals[atomId] = source
        pendingUndoAtomId = atomId
        pendingRemovalJobs[atomId]?.cancel()
        pendingRemovalJobs[atomId] = viewModelScope.launch {
            delay(5500)
            if (pendingRemovals.remove(atomId) != null) {
                launch(Dispatchers.IO) { atomDao.deleteById(atomId) }
            }
            if (pendingUndoAtomId == atomId) pendingUndoAtomId = null
            pendingRemovalJobs.remove(atomId)
        }
        snackbarMessage = "Atom dissolved"
    }

    fun undoRemoveAtom(atomId: String) {
        pendingRemovalJobs[atomId]?.cancel()
        pendingRemovalJobs.remove(atomId)
        val atom = pendingRemovals.remove(atomId) ?: return
        if (pendingUndoAtomId == atomId) pendingUndoAtomId = null
        atoms.add(atom)
        viewModelScope.launch(Dispatchers.IO) {
            val ok = engine.loadAtom(atom.id, playableSource(atom))
            launch(Dispatchers.Main) {
                if (ok) {
                    engine.updateAtomPosition(atom.id, atom.x.toFloat(), atom.y.toFloat(), atom.z.toFloat())
                    engine.updateAtomVolume(atom.id, atom.volume, atom.isMuted)
                    engine.setAtomReverbBlend(atom.id, atom.reverbBlend)
                    registerAnimation(atom)
                }
            }
        }
    }

    fun toggleRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isRecording) {
                stopRecordingInternal()
            } else {
                startRecordingInternal()
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndsAt = null
        if (minutes == null || minutes <= 0) {
            snackbarMessage = "Sleep timer off"
            return
        }
        val durationMs = minutes * 60_000L
        sleepTimerEndsAt = System.currentTimeMillis() + durationMs
        sleepTimerJob = viewModelScope.launch {
            val fadeMs = 30_000L.coerceAtMost(durationMs)
            delay(durationMs - fadeMs)
            val from = preset?.masterVolume ?: 1f
            fadeMasterVolume(from, 0f, fadeMs)
            engine.setMasterPaused(true)
            isPaused = true
            controller.onPlaybackPaused(true)
            engine.setMasterVolume(from)
            sleepTimerEndsAt = null
            snackbarMessage = "Drifted off"
        }
        snackbarMessage = "Drifting off in $minutes min"
    }

    fun dismissImportError() {
        importError = null
    }

    fun clearSnackbar() {
        snackbarMessage = null
    }

    fun consumeLastRecording(): File? {
        val f = lastRecordingFile
        lastRecordingFile = null
        return f
    }

    fun duplicateAtom(atomId: String) {
        val source = atoms.firstOrNull { it.id == atomId } ?: return
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            displayName = "${source.displayName} ·",
            x = (source.x + 0.15).coerceIn(-1.0, 1.0),
            y = (source.y + 0.15).coerceIn(-1.0, 1.0)
        )
        atoms.add(copy)
        loadingAtomIds.add(copy.id)
        viewModelScope.launch(Dispatchers.IO) {
            atomDao.insert(copy)
            val loaded = engine.loadAtom(copy.id, copy.fileName)
            if (loaded) {
                engine.updateAtomPosition(copy.id, copy.x.toFloat(), copy.y.toFloat(), copy.z.toFloat())
                engine.updateAtomVolume(copy.id, copy.volume, copy.isMuted)
                engine.setAtomReverbBlend(copy.id, copy.reverbBlend)
            }
            launch(Dispatchers.Main) {
                loadingAtomIds.remove(copy.id)
                if (!loaded) snackbarMessage = "Couldn't clone \"${copy.displayName}\""
            }
        }
        registerAnimation(copy)
        snackbarMessage = "Atom cloned"
    }

    private fun addAtom(fileName: String, displayName: String) {
        val atom = AudioAtomEntity(
            presetId = presetId,
            fileName = fileName,
            displayName = displayName,
            x = 0.0, y = -0.5, z = 0.0,
            volume = 1f
        )
        atoms.add(atom)
        loadingAtomIds.add(atom.id)
        viewModelScope.launch(Dispatchers.IO) {
            atomDao.insert(atom)
            val loaded = engine.loadAtom(atom.id, atom.fileName)
            if (loaded) {
                engine.updateAtomPosition(atom.id, atom.x.toFloat(), atom.y.toFloat(), atom.z.toFloat())
                engine.updateAtomVolume(atom.id, atom.volume, atom.isMuted)
                engine.setAtomReverbBlend(atom.id, atom.reverbBlend)
            }
            launch(Dispatchers.Main) {
                loadingAtomIds.remove(atom.id)
                if (!loaded) snackbarMessage = "Couldn't load \"$displayName\""
            }
        }
        registerAnimation(atom)
        snackbarMessage = "New atom drifting in"
    }

    private fun playableSource(atom: AudioAtomEntity): String {
        return atom.sourceUrl ?: atom.fileName
    }

    private fun urlDisplayName(url: String): String {
        return url.substringAfterLast('/').substringBefore('?').ifEmpty { "Stream" }
    }

    private fun registerAnimation(atom: AudioAtomEntity) {
        animationDriver.register(
            atom.id,
            atom.x, atom.y, atom.z,
            atom.animationMode.ordinal,
            atom.animationSpeed,
            atom.animationDepth
        )
    }

    private suspend fun fadeMasterVolume(from: Float, to: Float, durationMs: Long) {
        val steps = (durationMs / 40).coerceAtLeast(1)
        val stepDuration = durationMs / steps
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val v = from + (to - from) * progress
            engine.setMasterVolume(v)
            delay(stepDuration)
        }
        engine.setMasterVolume(to)
    }

    private fun startRecordingInternal() {
        val dir = recordingsDirectory() ?: return
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        val name = "Atoms-${preset?.name ?: "Mix"}-${formatter.format(Date())}.wav"
        val file = File(dir, name)
        engine.startRecording(file)
        isRecording = true
    }

    private fun stopRecordingInternal() {
        val file = engine.stopRecording()
        isRecording = false
        if (file != null) {
            lastRecordingFile = file
            snackbarMessage = "Captured. Mixed in real space."
        }
    }

    private fun recordingsDirectory(): File? {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()
        return if (dir.exists()) dir else null
    }
}
