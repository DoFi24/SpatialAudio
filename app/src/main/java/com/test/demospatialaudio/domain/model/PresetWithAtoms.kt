package com.test.demospatialaudio.domain.model

import androidx.room.Embedded
import androidx.room.Relation

data class PresetWithAtoms(
    @Embedded val preset: SoundscapePresetEntity,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val atoms: List<AudioAtomEntity>
)
