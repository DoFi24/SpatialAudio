package com.test.demospatialaudio.domain.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.test.demospatialaudio.domain.model.PresetWithAtoms
import com.test.demospatialaudio.domain.model.SoundscapePresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundscapePresetDao {

    @Query("SELECT * FROM soundscape_presets ORDER BY createdAt DESC")
    fun getAllPresets(): Flow<List<SoundscapePresetEntity>>

    @Transaction
    @Query("SELECT * FROM soundscape_presets WHERE id = :id")
    fun getPresetWithAtoms(id: String): Flow<PresetWithAtoms>

    @Transaction
    @Query("SELECT * FROM soundscape_presets WHERE id = :id")
    suspend fun getPresetWithAtomsOnce(id: String): PresetWithAtoms?

    @Insert
    suspend fun insert(preset: SoundscapePresetEntity)

    @Update
    suspend fun update(preset: SoundscapePresetEntity)

    @Delete
    suspend fun delete(preset: SoundscapePresetEntity)

    @Query("SELECT COUNT(*) FROM soundscape_presets")
    suspend fun count(): Int

    @Transaction
    @Query("SELECT * FROM soundscape_presets ORDER BY createdAt DESC")
    fun getAllPresetsWithAtoms(): Flow<List<PresetWithAtoms>>
}
