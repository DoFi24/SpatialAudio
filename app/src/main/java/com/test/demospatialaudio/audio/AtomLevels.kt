package com.test.demospatialaudio.audio

import java.util.concurrent.ConcurrentHashMap

class AtomLevels {

    private val levels = ConcurrentHashMap<String, Float>()

    fun update(id: String, level: Float) {
        levels[id] = level
    }

    fun snapshot(): Map<String, Float> = HashMap(levels)

    fun remove(id: String) {
        levels.remove(id)
    }

    fun clear() {
        levels.clear()
    }
}
