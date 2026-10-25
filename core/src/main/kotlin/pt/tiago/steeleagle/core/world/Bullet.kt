package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed

/** A 4x4 px bullet moving in one direction. */
class Bullet(
    var x: Int,
    var y: Int,
    val dir: Dir,
    val owner: Tank,
    val weaponLevel: Int = owner.weaponLevel,
    val speed: Int = Fixed.BULLET_SPEED,
) {
    val sizeSub: Int = Fixed.BULLET_SUBPIXELS

    val left: Int get() = x
    val top: Int get() = y
    val right: Int get() = x + sizeSub
    val bottom: Int get() = y + sizeSub
}
