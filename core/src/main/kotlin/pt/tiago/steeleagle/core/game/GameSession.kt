package pt.tiago.steeleagle.core.game

import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.Scoring
import pt.tiago.steeleagle.core.world.World

/**
 * Session-wide state across level clears: per-player lives, scores, extra-life
 * thresholds, campaign level and cycle survive each new World. [restart] resets
 * it for a new run; the UI creates a new session instead.
 */
class GameSession(
    private val levels: List<Level>,
    private val seed: Int = 1,
    val difficulty: Difficulty = Difficulty.NORMAL,
    startLevelIndex: Int = 0,
    val playerCount: Int = 1,
) {
    init {
        require(levels.isNotEmpty()) { "a session needs at least one level" }
        require(startLevelIndex in levels.indices) { "level index out of range" }
        require(playerCount in 1..2) { "player count must be 1 or 2" }
    }

    private var levelIndex: Int = startLevelIndex

    var cycle: Int = 0
        private set

    private val playerLives = IntArray(playerCount) { difficulty.initialLives }
    private val playerScores = IntArray(playerCount)
    private val playerNextExtraLife = IntArray(playerCount) {
        Scoring.EXTRA_LIFE_STEP
    }
    private val playerWeaponLevels = IntArray(playerCount)

    /** Player-one aliases kept for the single-player flow and tests. */
    val lives: Int
        get() = playerLives[0]

    val score: Int
        get() = playerScores[0]

    val nextExtraLifeScore: Int
        get() = playerNextExtraLife[0]

    fun livesOf(playerIndex: Int): Int = playerLives[playerIndex]

    fun scoreOf(playerIndex: Int): Int = playerScores[playerIndex]

    fun nextExtraLifeScoreOf(playerIndex: Int): Int =
        playerNextExtraLife[playerIndex]

    /** Star level carried into the next stage (0 after a death). */
    fun weaponLevelOf(playerIndex: Int): Int = playerWeaponLevels[playerIndex]

    val levelCount: Int
        get() = levels.size

    val levelNumber: Int
        get() = levelIndex + 1

    val level: Level
        get() = levels[levelIndex]

    var world: World = newWorld()
        private set

    fun nextLevel() {
        syncFromWorld()
        levelIndex++
        if (levelIndex >= levels.size) {
            levelIndex = 0
            cycle++
        }
        world = newWorld()
    }

    fun restart() {
        levelIndex = 0
        cycle = 0
        for (index in 0 until playerCount) {
            playerLives[index] = difficulty.initialLives
            playerScores[index] = 0
            playerNextExtraLife[index] = Scoring.EXTRA_LIFE_STEP
            playerWeaponLevels[index] = 0
        }
        world = newWorld()
    }

    fun syncFromWorld() {
        for (index in 0 until playerCount) {
            playerLives[index] = world.livesOf(index)
            playerScores[index] = world.scoreOf(index)
            playerNextExtraLife[index] = world.nextExtraLifeScoreOf(index)
            val tank = world.players[index]
            playerWeaponLevels[index] = if (tank.alive) tank.weaponLevel else 0
        }
    }

    private fun newWorld(): World = World.fromLevel(
        level = level,
        seed = seed,
        difficulty = difficulty,
        levelNumber = levelNumber,
        cycle = cycle,
        initialLives = playerLives[0],
        initialScore = playerScores[0],
        initialNextExtraLife = playerNextExtraLife[0],
        playerCount = playerCount,
        initialLivesP2 = playerLives.getOrElse(1) { difficulty.initialLives },
        initialScoreP2 = playerScores.getOrElse(1) { 0 },
        initialNextExtraLifeP2 = playerNextExtraLife.getOrElse(1) {
            Scoring.EXTRA_LIFE_STEP
        },
        initialWeaponLevel = playerWeaponLevels[0],
        initialWeaponLevelP2 = playerWeaponLevels.getOrElse(1) { 0 },
    )

    constructor(
        level: Level,
        seed: Int = 1,
        difficulty: Difficulty = Difficulty.NORMAL,
    ) : this(listOf(level), seed, difficulty, 0, 1)
}
