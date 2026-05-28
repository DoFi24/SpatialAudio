package com.test.demospatialaudio.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "audio_atoms",
    foreignKeys = [ForeignKey(
        entity = SoundscapePresetEntity::class,
        parentColumns = ["id"],
        childColumns = ["presetId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("presetId")]
)
data class AudioAtomEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val presetId: String,
    val fileName: String,
    val displayName: String,
    val sourceUrl: String? = null,
    val x: Double = 0.0,
    val y: Double = -0.5,
    val z: Double = 0.0,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val reverbBlend: Float = 0.3f,
    val animationModeOrdinal: Int = AtomAnimationMode.NONE.ordinal,
    val animationSpeed: Double = 0.25,
    val animationDepth: Double = 0.4
) {
    val animationMode: AtomAnimationMode
        get() = AtomAnimationMode.entries.getOrElse(animationModeOrdinal) { AtomAnimationMode.NONE }
}
