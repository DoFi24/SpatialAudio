package com.test.demospatialaudio.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "soundscape_presets")
data class SoundscapePresetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val reverbPresetOrdinal: Int = ReverbPreset.MEDIUM_ROOM.ordinal,
    val reverbLevel: Float = -6f,
    val masterVolume: Float = 1f
) {
    val reverbPreset: ReverbPreset
        get() = ReverbPreset.entries.getOrElse(reverbPresetOrdinal) { ReverbPreset.MEDIUM_ROOM }
}
