package pt.tiago.steeleagle.core.game

import pt.tiago.steeleagle.core.rules.PowerUpType

/**
 * One-shot simulation event consumed by the app for sound, explosions and
 * haptics. The core never calls the app; it only appends to World.events.
 * Positions are in field pixels.
 */
sealed interface GameEvent {
    data class ShotFired(val x: Int, val y: Int, val isPlayer: Boolean) : GameEvent
    data class SteelHit(val x: Int, val y: Int) : GameEvent
    data class BrickBroken(val x: Int, val y: Int) : GameEvent
    data class EnemyExploded(val x: Int, val y: Int) : GameEvent
    data class PlayerExploded(val x: Int, val y: Int) : GameEvent
    data class EagleDestroyed(val x: Int, val y: Int) : GameEvent
    data class PowerUpAppeared(
        val x: Int,
        val y: Int,
        val type: PowerUpType,
    ) : GameEvent

    data class PowerUpPicked(
        val x: Int,
        val y: Int,
        val type: PowerUpType,
    ) : GameEvent

    data object ExtraLife : GameEvent
    data object LevelClear : GameEvent
    data object GameOver : GameEvent
}
