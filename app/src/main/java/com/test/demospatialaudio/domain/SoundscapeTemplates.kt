package com.test.demospatialaudio.domain

import com.test.demospatialaudio.domain.model.AtomAnimationMode
import com.test.demospatialaudio.domain.model.AudioAtomEntity
import com.test.demospatialaudio.domain.model.ReverbPreset
import com.test.demospatialaudio.domain.model.SoundscapePresetEntity
import java.util.UUID

object SoundscapeTemplates {

    data class Template(
        val key: String,
        val name: String,
        val description: String,
        val reverb: ReverbPreset,
        val reverbLevel: Float,
        val atoms: List<TemplateAtom>
    )

    data class TemplateAtom(
        val fileName: String,
        val displayName: String,
        val x: Double,
        val y: Double,
        val z: Double = 0.0,
        val volume: Float = 1f,
        val reverbBlend: Float = 0.3f,
        val animationMode: AtomAnimationMode = AtomAnimationMode.NONE,
        val animationSpeed: Double = 0.2,
        val animationDepth: Double = 0.4
    )

    val all: List<Template> = listOf(
        Template(
            key = "forest",
            name = "Forest at dawn",
            description = "Soft rain, gentle wind, a fire close by",
            reverb = ReverbPreset.LARGE_HALL,
            reverbLevel = -10f,
            atoms = listOf(
                TemplateAtom("audio/rain.mp3", "Rain", -0.6, -0.4, volume = 0.6f, reverbBlend = 0.45f),
                TemplateAtom("audio/wind.mp3", "Wind", 0.7, 0.3, volume = 0.5f, reverbBlend = 0.55f,
                    animationMode = AtomAnimationMode.DRIFT, animationSpeed = 0.15, animationDepth = 0.5),
                TemplateAtom("audio/fire.mp3", "Fire", 0.0, 0.6, volume = 0.7f, reverbBlend = 0.2f)
            )
        ),
        Template(
            key = "storm",
            name = "Storm",
            description = "Heavy rain and gusts circling you",
            reverb = ReverbPreset.LARGE_ROOM,
            reverbLevel = -6f,
            atoms = listOf(
                TemplateAtom("audio/rain.mp3", "Rain", 0.0, -0.7, volume = 0.95f, reverbBlend = 0.5f),
                TemplateAtom("audio/wind.mp3", "Gust", -0.5, 0.5, volume = 0.8f, reverbBlend = 0.6f,
                    animationMode = AtomAnimationMode.ORBIT, animationSpeed = 0.12)
            )
        ),
        Template(
            key = "fire",
            name = "Cozy fire",
            description = "A small fire and distant wind",
            reverb = ReverbPreset.SMALL_ROOM,
            reverbLevel = -12f,
            atoms = listOf(
                TemplateAtom("audio/fire.mp3", "Fire", 0.0, -0.25, volume = 0.85f, reverbBlend = 0.15f),
                TemplateAtom("audio/wind.mp3", "Wind outside", -0.9, 0.4, volume = 0.35f, reverbBlend = 0.7f)
            )
        ),
        Template(
            key = "cosmos",
            name = "Cosmos",
            description = "Slow wind drifting, fire pulsing in the distance",
            reverb = ReverbPreset.CATHEDRAL,
            reverbLevel = -4f,
            atoms = listOf(
                TemplateAtom("audio/wind.mp3", "Drone", 0.0, -0.8, volume = 0.6f, reverbBlend = 0.85f,
                    animationMode = AtomAnimationMode.DRIFT, animationSpeed = 0.08, animationDepth = 0.7),
                TemplateAtom("audio/fire.mp3", "Pulse", 0.8, 0.6, volume = 0.45f, reverbBlend = 0.9f,
                    animationMode = AtomAnimationMode.PULSE, animationSpeed = 0.06, animationDepth = 0.6)
            )
        ),
        Template(
            key = "focus",
            name = "Focus loop",
            description = "Steady rain orbiting, wind on the horizon",
            reverb = ReverbPreset.MEDIUM_ROOM,
            reverbLevel = -10f,
            atoms = listOf(
                TemplateAtom("audio/rain.mp3", "Rain", 0.55, 0.0, volume = 0.6f, reverbBlend = 0.3f,
                    animationMode = AtomAnimationMode.ORBIT, animationSpeed = 0.05),
                TemplateAtom("audio/wind.mp3", "Horizon", 0.0, -0.95, volume = 0.3f, reverbBlend = 0.55f)
            )
        ),
        Template(
            key = "sleep",
            name = "Sleep",
            description = "Whisper rain drifting all around",
            reverb = ReverbPreset.LARGE_HALL,
            reverbLevel = -14f,
            atoms = listOf(
                TemplateAtom("audio/rain.mp3", "Rain near", -0.3, -0.3, volume = 0.45f, reverbBlend = 0.4f,
                    animationMode = AtomAnimationMode.DRIFT, animationSpeed = 0.07, animationDepth = 0.4),
                TemplateAtom("audio/rain.mp3", "Rain far", 0.4, 0.3, volume = 0.35f, reverbBlend = 0.65f,
                    animationMode = AtomAnimationMode.DRIFT, animationSpeed = 0.06, animationDepth = 0.5),
                TemplateAtom("audio/wind.mp3", "Breath", 0.0, -0.7, volume = 0.18f, reverbBlend = 0.8f)
            )
        )
    )

    fun materialize(template: Template): Pair<SoundscapePresetEntity, List<AudioAtomEntity>> {
        val preset = SoundscapePresetEntity(
            name = template.name,
            reverbPresetOrdinal = template.reverb.ordinal,
            reverbLevel = template.reverbLevel
        )
        val atoms = template.atoms.map { t ->
            AudioAtomEntity(
                id = UUID.randomUUID().toString(),
                presetId = preset.id,
                fileName = t.fileName,
                displayName = t.displayName,
                x = t.x, y = t.y, z = t.z,
                volume = t.volume,
                reverbBlend = t.reverbBlend,
                animationModeOrdinal = t.animationMode.ordinal,
                animationSpeed = t.animationSpeed,
                animationDepth = t.animationDepth
            )
        }
        return preset to atoms
    }
}
