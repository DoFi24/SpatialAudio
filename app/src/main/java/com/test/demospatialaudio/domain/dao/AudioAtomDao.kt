package com.test.demospatialaudio.domain.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.test.demospatialaudio.domain.model.AudioAtomEntity

@Dao
interface AudioAtomDao {

    @Insert
    suspend fun insert(atom: AudioAtomEntity)

    @Update
    suspend fun update(atom: AudioAtomEntity)

    @Delete
    suspend fun delete(atom: AudioAtomEntity)

    @Query("DELETE FROM audio_atoms WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM audio_atoms WHERE presetId = :presetId")
    suspend fun getAtomsForPreset(presetId: String): List<AudioAtomEntity>
}
