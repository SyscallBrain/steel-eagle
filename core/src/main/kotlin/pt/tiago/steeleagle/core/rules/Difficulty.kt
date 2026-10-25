package pt.tiago.steeleagle.core.rules

/** Difficulty tuning. */
enum class Difficulty(
    val initialLives: Int,
    val enemySpeedMultiplier: Float,
    val fireChanceMultiplier: Float,
    val eagleWeight: Int,
    val spawnIntervalMultiplier: Float,
    val powerUpDurationMultiplier: Float,
) {
    EASY(
        initialLives = 5,
        enemySpeedMultiplier = 0.85f,
        fireChanceMultiplier = 0.7f,
        eagleWeight = 10,
        spawnIntervalMultiplier = 1.25f,
        powerUpDurationMultiplier = 1.2f,
    ),
    NORMAL(
        initialLives = 3,
        enemySpeedMultiplier = 1.0f,
        fireChanceMultiplier = 1.0f,
        eagleWeight = 25,
        spawnIntervalMultiplier = 1.0f,
        powerUpDurationMultiplier = 1.0f,
    ),
    HARD(
        initialLives = 2,
        enemySpeedMultiplier = 1.15f,
        fireChanceMultiplier = 1.4f,
        eagleWeight = 45,
        spawnIntervalMultiplier = 0.8f,
        powerUpDurationMultiplier = 0.85f,
    ),
}
