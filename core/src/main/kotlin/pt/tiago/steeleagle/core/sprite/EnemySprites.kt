package pt.tiago.steeleagle.core.sprite

import pt.tiago.steeleagle.core.model.Dir

/** Single source of truth for enemy tank sprite names. */
object EnemySprites {
    fun name(dir: Dir, frame: Int): String {
        require(frame >= 0) { "frame must be non-negative" }
        return "enemy_${dir.name.lowercase()}_$frame"
    }
}
