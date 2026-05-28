package com.test.demospatialaudio.domain

object SeedDataLoader {

    suspend fun seedIfNeeded(db: AppDatabase) {
        if (db.presetDao().count() > 0) return

        val starterKeys = listOf("forest", "cosmos")
        for (key in starterKeys) {
            val template = SoundscapeTemplates.all.firstOrNull { it.key == key } ?: continue
            val (preset, atoms) = SoundscapeTemplates.materialize(template)
            db.presetDao().insert(preset)
            for (atom in atoms) db.atomDao().insert(atom)
        }
    }
}
