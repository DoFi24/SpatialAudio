package com.test.demospatialaudio.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

data class AudioLibraryEntry(
    val fileName: String,
    val displayName: String,
    val iconName: String,
    val isBuiltIn: Boolean
)

object AudioLibraryService {

    val supportedExtensions = listOf("wav", "mp3", "ogg", "m4a", "aac", "flac", "caf")

    private val builtInEntries = listOf(
        AudioLibraryEntry("audio/rain.mp3", "Rain", "cloud_rain", true),
        AudioLibraryEntry("audio/wind.mp3", "Wind", "air", true),
        AudioLibraryEntry("audio/fire.mp3", "Fire", "fire", true)
    )

    fun builtIn(): List<AudioLibraryEntry> = builtInEntries

    fun importedEntries(context: Context): List<AudioLibraryEntry> {
        val dir = userAudioDirectory(context) ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.extension.lowercase() in supportedExtensions }
            ?.map { file ->
                AudioLibraryEntry(
                    fileName = file.name,
                    displayName = file.nameWithoutExtension,
                    iconName = "waveform",
                    isBuiltIn = false
                )
            }
            ?.sortedBy { it.displayName }
            ?: emptyList()
    }

    fun importFile(context: Context, uri: Uri): AudioLibraryEntry? {
        val dir = userAudioDirectory(context) ?: return null
        val displayName = resolveDisplayName(context, uri)
        val ext = uri.lastPathSegment?.substringAfterLast('.', "wav") ?: "wav"
        val targetName = uniqueFileName(displayName, ext, dir)
        val targetFile = File(dir, targetName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        return AudioLibraryEntry(
            fileName = targetName,
            displayName = displayName,
            iconName = "waveform",
            isBuiltIn = false
        )
    }

    fun resolveFileDescriptor(context: Context, fileName: String): AssetFileDescriptor? {
        try {
            return context.assets.openFd(fileName)
        } catch (_: Exception) {}

        val dir = userAudioDirectory(context)
        if (dir != null) {
            val file = File(dir, fileName)
            if (file.exists()) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, file.length())
            }
        }

        val baseName = fileName.substringBeforeLast('.')
        for (ext in supportedExtensions) {
            try {
                return context.assets.openFd("$baseName.$ext")
            } catch (_: Exception) {}
        }

        return null
    }

    private fun userAudioDirectory(context: Context): File? {
        val dir = File(context.filesDir, "Atoms")
        if (!dir.exists()) dir.mkdirs()
        return if (dir.exists()) dir else null
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        val segment = uri.lastPathSegment ?: "audio"
        return segment.substringBeforeLast('.').substringAfterLast('/')
    }

    private fun uniqueFileName(baseName: String, ext: String, dir: File): String {
        var candidate = "$baseName.$ext"
        var counter = 1
        while (File(dir, candidate).exists()) {
            counter++
            candidate = "$baseName-$counter.$ext"
        }
        return candidate
    }
}
