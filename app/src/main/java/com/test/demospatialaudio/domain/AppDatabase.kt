package com.test.demospatialaudio.domain

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.test.demospatialaudio.domain.dao.AudioAtomDao
import com.test.demospatialaudio.domain.dao.SoundscapePresetDao
import com.test.demospatialaudio.domain.model.AudioAtomEntity
import com.test.demospatialaudio.domain.model.SoundscapePresetEntity

@Database(
    entities = [SoundscapePresetEntity::class, AudioAtomEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun presetDao(): SoundscapePresetDao
    abstract fun atomDao(): AudioAtomDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_atoms ADD COLUMN sourceUrl TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE audio_atoms_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        presetId TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        sourceUrl TEXT,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        volume REAL NOT NULL,
                        isMuted INTEGER NOT NULL,
                        reverbBlend REAL NOT NULL,
                        animationModeOrdinal INTEGER NOT NULL,
                        animationSpeed REAL NOT NULL,
                        animationDepth REAL NOT NULL,
                        FOREIGN KEY(presetId) REFERENCES soundscape_presets(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO audio_atoms_new (
                        id, presetId, fileName, displayName, sourceUrl,
                        x, y, z, volume, isMuted, reverbBlend,
                        animationModeOrdinal, animationSpeed, animationDepth
                    )
                    SELECT id, presetId, fileName, displayName, sourceUrl,
                           x, y, z, volume, isMuted, reverbBlend,
                           animationModeOrdinal, animationSpeed, animationDepth
                    FROM audio_atoms
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE audio_atoms")
                db.execSQL("ALTER TABLE audio_atoms_new RENAME TO audio_atoms")
                db.execSQL("CREATE INDEX index_audio_atoms_presetId ON audio_atoms(presetId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spatial_audio.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
