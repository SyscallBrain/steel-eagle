package pt.tiago.steeleagle.core.sprite

import pt.tiago.steeleagle.core.model.Dir

/** Single source of truth for tank sprite names shared by atlas and scenes. */
object TankSprites {
    fun name(dir: Dir, frame: Int): String {
        require(frame >= 0) { "frame must be non-negative" }
        return "tank_${dir.name.lowercase()}_$frame"
    }
}
