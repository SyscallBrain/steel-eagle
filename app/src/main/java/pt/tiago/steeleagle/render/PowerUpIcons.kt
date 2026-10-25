package pt.tiago.steeleagle.render

import pt.tiago.steeleagle.core.rules.PowerUpType

/** Sprite names for the eight power-up icons. */
object PowerUpIcons {
    fun name(type: PowerUpType): String = when (type) {
        PowerUpType.STAR -> "power_star"
        PowerUpType.GRENADE -> "power_grenade"
        PowerUpType.HELMET -> "power_helmet"
        PowerUpType.SHOVEL -> "power_shovel"
        PowerUpType.CLOCK -> "power_clock"
        PowerUpType.TANK -> "power_tank"
        PowerUpType.BOAT -> "power_boat"
        PowerUpType.GUN -> "power_gun"
    }
}
