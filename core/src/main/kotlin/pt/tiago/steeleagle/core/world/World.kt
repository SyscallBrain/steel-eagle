package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.ai.EnemyBrain
import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.model.Rng
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemyRoster
import pt.tiago.steeleagle.core.rules.EnemySpawn
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import pt.tiago.steeleagle.core.rules.Scoring
import pt.tiago.steeleagle.core.rules.SpawnDirector
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WorldState {
    PLAYING,
    LEVEL_CLEAR,
    GAME_OVER,
}

data class InputFrame(
    val p1: PlayerInput,
    val p2: PlayerInput = PlayerInput.NONE,
)

/**
 * Phase 10 simulation: one or two players, enemies, terrain, bullets,
 * power-ups and the eagle. Emits GameEvents for the app's audio/effects.
 * Deterministic; all positions are integer 1/16 px units.
 */
class World(
    val terrain: TerrainGrid,
    seed: Int = 1,
    spawnPixelX: Int = 64,
    spawnPixelY: Int = 192,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val levelNumber: Int = 1,
    val cycle: Int = 0,
    enemyRoster: List<EnemySpawn> = emptyList(),
    initialLives: Int = difficulty.initialLives,
    initialScore: Int = 0,
    initialNextExtraLife: Int = Scoring.EXTRA_LIFE_STEP,
    val playerCount: Int = 1,
    spawnPixelX2: Int = 128,
    spawnPixelY2: Int = 192,
    initialLivesP2: Int = difficulty.initialLives,
    initialScoreP2: Int = 0,
    initialNextExtraLifeP2: Int = Scoring.EXTRA_LIFE_STEP,
    initialWeaponLevel: Int = 0,
    initialWeaponLevelP2: Int = 0,
) {
    init {
        require(playerCount in 1..2) { "player count must be 1 or 2" }
    }

    private val respawnPixelX = spawnPixelX
    private val respawnPixelY = spawnPixelY
    private val respawnPixelX2 = spawnPixelX2
    private val respawnPixelY2 = spawnPixelY2

    /** Drives enemy AI and, from phase 5, power-up placement. */
    val rng = Rng(seed)

    val player = Tank(
        x = spawnPixelX * Fixed.SUBPIXELS,
        y = spawnPixelY * Fixed.SUBPIXELS,
    )

    val player2: Tank? = if (playerCount == 2) {
        Tank(
            x = spawnPixelX2 * Fixed.SUBPIXELS,
            y = spawnPixelY2 * Fixed.SUBPIXELS,
        )
    } else {
        null
    }

    val players: List<Tank> = listOfNotNull(player, player2)

    private val playerLives = IntArray(playerCount).also { lives ->
        lives[0] = initialLives
        if (playerCount == 2) lives[1] = initialLivesP2
    }
    private val playerScores = IntArray(playerCount).also { scores ->
        scores[0] = initialScore
        if (playerCount == 2) scores[1] = initialScoreP2
    }
    private val playerNextExtraLife = IntArray(playerCount).also { thresholds ->
        thresholds[0] = initialNextExtraLife
        if (playerCount == 2) thresholds[1] = initialNextExtraLifeP2
    }
    private val playerRespawnTicks = IntArray(playerCount)
    private val playerKills = Array(playerCount) { IntArray(EnemyType.entries.size) }
    private val playerScoreByType = Array(playerCount) { IntArray(EnemyType.entries.size) }
    private var remoteEnemiesRemaining: Int? = null
    private var remoteTotalEnemies: Int? = null
    private var netBulletOwner: Tank? = null

    val tanks = mutableListOf<Tank>().also { it.addAll(players) }
    val bullets = mutableListOf<Bullet>()
    val eagle = Eagle()
    val spawnDirector = SpawnDirector(
        enemyRoster,
        difficulty,
        levelNumber,
        players = playerCount,
    )
    val spawnAnimations = mutableListOf<SpawnAnimation>()
    val events = mutableListOf<GameEvent>()

    val kills: IntArray
        get() = playerKills[0]
    val scoreByType: IntArray
        get() = playerScoreByType[0]

    fun killsFor(type: EnemyType): Int = killsFor(0, type)

    fun killsFor(playerIndex: Int, type: EnemyType): Int =
        playerKills[playerIndex][type.ordinal]

    fun scoreFor(type: EnemyType): Int = scoreFor(0, type)

    fun scoreFor(playerIndex: Int, type: EnemyType): Int =
        playerScoreByType[playerIndex][type.ordinal]

    fun killsOf(playerIndex: Int): IntArray = playerKills[playerIndex].copyOf()

    fun scoreByTypeOf(playerIndex: Int): IntArray =
        playerScoreByType[playerIndex].copyOf()

    fun totalKills(playerIndex: Int): Int = playerKills[playerIndex].sum()

    fun livesOf(playerIndex: Int): Int = playerLives[playerIndex]

    fun scoreOf(playerIndex: Int): Int = playerScores[playerIndex]

    fun nextExtraLifeScoreOf(playerIndex: Int): Int =
        playerNextExtraLife[playerIndex]

    private val enemyBrain = EnemyBrain(difficulty, cycle)

    var state: WorldState = WorldState.PLAYING
        private set

    var tick: Long = 0L
        private set

    /** Player-one aliases kept for the single-player flow and tests. */
    var lives: Int
        get() = playerLives[0]
        set(value) {
            playerLives[0] = value
        }

    var score: Int
        get() = playerScores[0]
        set(value) {
            playerScores[0] = value
        }

    val respawnTicks: Int
        get() = playerRespawnTicks[0]

    var pendingPowerUpDrops: Int = 0
        private set

    var powerUp: PowerUp? = null
        private set

    var freezeTicks: Int = 0
        private set

    var shovelTicks: Int = 0
        private set

    var nextExtraLifeScore: Int
        get() = playerNextExtraLife[0]
        set(value) {
            playerNextExtraLife[0] = value
        }

    val enemiesRemaining: Int
        get() = remoteEnemiesRemaining ?: spawnDirector.remaining

    val totalEnemies: Int
        get() = remoteTotalEnemies ?: spawnDirector.total

    init {
        players.forEachIndexed { index, tank ->
            if (playerLives[index] <= 0) {
                tank.alive = false
                tank.shieldTicks = 0
            } else {
                tank.shieldTicks = SHIELD_TICKS
                // The star survives a stage clear; only death resets it.
                setWeaponLevel(tank, if (index == 0) initialWeaponLevel else initialWeaponLevelP2)
            }
        }
    }

    fun step(input: InputFrame) {
        if (state != WorldState.PLAYING) return

        for (index in players.indices) {
            val tank = players[index]
            if (!tank.alive) continue
            if (tank.stunTicks > 0) {
                tank.stunTicks--
                tank.wasMoving = false
                continue
            }
            updatePlayer(index, if (index == 0) input.p1 else input.p2)
        }
        if (freezeTicks > 0) {
            freezeTicks--
        } else {
            updateEnemies()
            updateSpawner()
        }
        updateBullets()
        updatePowerUp()
        updateShovel()
        updateRespawn()
        updateShield()
        tick++

        if (state == WorldState.PLAYING &&
            spawnDirector.total > 0 &&
            spawnDirector.defeated >= spawnDirector.total
        ) {
            state = WorldState.LEVEL_CLEAR
            events += GameEvent.LevelClear
        }
    }

    /** Stable textual state used by determinism tests; the net snapshot comes in phase 12. */
    fun snapshot(): String = buildString {
        append("tick=").append(tick)
        append(";state=").append(state)
        append(";difficulty=").append(difficulty)
        append(";level=").append(levelNumber)
        append(";cycle=").append(cycle)
        append(";players=").append(playerCount)
        append(";rng=").append(rng.state)
        append(";lives=").append(lives)
        append(";score=").append(score)
        append(";nextExtraLife=").append(nextExtraLifeScore)
        append(";respawn=").append(respawnTicks)
        if (player2 != null) {
            append(";lives2=").append(playerLives[1])
            append(";score2=").append(playerScores[1])
            append(";nextExtraLife2=").append(playerNextExtraLife[1])
            append(";respawn2=").append(playerRespawnTicks[1])
        }
        append(";drops=").append(pendingPowerUpDrops)
        append(";defeated=").append(spawnDirector.defeated)
        append(";spawned=").append(spawnDirector.spawnedCount)
        append(";rotation=").append(spawnDirector.rotation)
        append(";nextSpawn=").append(spawnDirector.nextSpawnTick)
        append(";player=").append(player.x).append(',').append(player.y)
            .append(',').append(player.dir).append(',').append(player.trackFrame)
            .append(',').append(player.trackTicks).append(',').append(player.weaponLevel)
            .append(',').append(player.speed)
            .append(',').append(player.bulletSpeed).append(',').append(player.hasBoat)
            .append(',').append(player.escapingWater)
            .append(',').append(player.slideRemaining).append(',').append(player.wasMoving)
            .append(',').append(player.alive).append(',').append(player.shieldTicks)
            .append(',').append(player.stunTicks)
        append(";eagle=").append(eagle.alive)
        append(";terrain=").append(terrain.contentHash())
        append(";bullets=")
        for (bullet in bullets) {
            append(bullet.x).append(',').append(bullet.y)
                .append(',').append(bullet.dir)
                .append(',').append(bullet.weaponLevel)
                .append(',').append(bullet.speed)
                .append(',').append(players.indexOf(bullet.owner))
                .append(';')
        }
        for (index in players.indices) {
            append(";kills").append(index).append('=')
            for (kills in playerKills[index]) append(kills).append(',')
            append(";points").append(index).append('=')
            for (points in playerScoreByType[index]) append(points).append(',')
        }
        append(";tanks=")
        for (tank in tanks) {
            append(tank.isPlayer).append(',').append(tank.x).append(',').append(tank.y)
                .append(',').append(tank.dir).append(',').append(tank.trackFrame)
                .append(',').append(tank.trackTicks)
                .append(',').append(tank.aiTimer)
                .append(',').append(tank.shieldTicks)
                .append(',').append(tank.droppedPowerUp)
                .append(',').append(tank.hasBoat)
                .append(',').append(tank.escapingWater)
                .append(',').append(tank.speed)
                .append(',').append(tank.bulletSpeed)
                .append(',').append(tank.alive).append(',').append(tank.hp)
                .append(',').append(tank.stunTicks)
                .append(',').append(tank.weaponLevel)
                .append(',').append(tank.slideRemaining)
                .append(',').append(tank.wasMoving)
            if (!tank.isPlayer) {
                append(',').append(tank.type).append(',').append(tank.flashing)
            }
            append(';')
        }
        append(";spawns=")
        for (animation in spawnAnimations) {
            append(animation.blockX).append(',').append(animation.blockY)
                .append(',').append(animation.type)
                .append(',').append(animation.flashing)
                .append(',').append(animation.ticksRemaining)
                .append(';')
        }
        append(";powerUp=")
        powerUp?.let {
            append(it.type).append(',').append(it.x).append(',').append(it.y)
                .append(',').append(it.ticksRemaining)
        }
        append(";freeze=").append(freezeTicks)
        append(";shovel=").append(shovelTicks)
    }

    private fun updatePlayer(index: Int, input: PlayerInput) {
        val tank = players[index]
        val direction = input.direction
        if (direction != null) {
            tank.slideRemaining = 0
            if (direction != tank.dir && isPerpendicular(tank.dir, direction)) {
                alignForTurn(tank, direction)
            }
            tank.dir = direction
            tank.wasMoving = moveTank(tank, direction)
        } else if (tank.slideRemaining > 0) {
            val moved = moveSlide(tank)
            tank.wasMoving = tank.slideRemaining > 0 && moved
        } else if (tank.wasMoving && isOnIce(tank)) {
            tank.slideRemaining = Fixed.ICE_SLIDE_SUBPIXELS
            val moved = moveSlide(tank)
            tank.wasMoving = tank.slideRemaining > 0 && moved
        } else {
            tank.wasMoving = false
        }

        if (tank.escapingWater && !tankOverlapsWater(tank)) {
            tank.escapingWater = false
        }

        if (input.fire) tryFire(tank)
    }

    private fun tankOverlapsWater(tank: Tank): Boolean {
        val leftPx = tank.x / Fixed.SUBPIXELS
        val topPx = tank.y / Fixed.SUBPIXELS
        val rightPx = (tank.x + tank.sizeSub - 1) / Fixed.SUBPIXELS
        val bottomPx = (tank.y + tank.sizeSub - 1) / Fixed.SUBPIXELS
        for (cellY in topPx / 4..bottomPx / 4) {
            for (cellX in leftPx / 4..rightPx / 4) {
                if (terrain.get(cellX, cellY) == Terrain.WATER) return true
            }
        }
        return false
    }

    private fun updateEnemies() {
        for (tank in tanks) {
            if (!tank.isPlayer && tank.alive && tank.aiEnabled) {
                enemyBrain.update(this, tank)
            }
        }
    }

    /** Nearest living player, or null while both are waiting to respawn. */
    fun nearestPlayer(enemy: Tank): Tank? = players
        .asSequence()
        .filter { it.alive }
        .minByOrNull { abs(it.x - enemy.x) + abs(it.y - enemy.y) }

    private fun updateSpawner() {
        spawnDirector.update(this)

        val iterator = spawnAnimations.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            animation.ticksRemaining--
            if (animation.ticksRemaining <= 0) {
                if (isSpawnAreaFree(animation.blockX, animation.blockY, animation)) {
                    iterator.remove()
                    spawnEnemy(animation)
                } else {
                    animation.ticksRemaining = 1
                }
            }
        }
    }

    private fun spawnEnemy(animation: SpawnAnimation) {
        val cycleSpeed = (
            animation.type.speedFor(difficulty) * (1.0f + cycle * CYCLE_SPEED_STEP)
            ).roundToInt().coerceAtLeast(1)

        val enemy = Tank(
            x = animation.blockX * Fixed.TANK_SUBPIXELS,
            y = animation.blockY * Fixed.TANK_SUBPIXELS,
            dir = Dir.DOWN,
            isPlayer = false,
            speed = cycleSpeed,
            type = animation.type,
            hp = animation.type.hitPoints,
            flashing = animation.flashing,
        )
        enemy.bulletSpeed = animation.type.bulletSpeed
        enemy.aiTimer = 30 + rng.nextInt(60)
        tanks += enemy
    }

    private fun updatePowerUp() {
        if (pendingPowerUpDrops > 0) {
            pendingPowerUpDrops--
            spawnPowerUp(PowerUpType.entries[rng.nextInt(PowerUpType.entries.size)])
        }

        val current = powerUp ?: return
        current.ticksRemaining--
        if (current.ticksRemaining <= 0) {
            powerUp = null
            return
        }
        for (index in players.indices) {
            val tank = players[index]
            if (tank.alive && overlapsPowerUp(current, tank)) {
                powerUp = null
                collectPowerUp(index, current.type, current.x, current.y)
                return
            }
        }
    }

    private fun overlapsPowerUp(powerUp: PowerUp, tank: Tank): Boolean {
        val left = powerUp.x * Fixed.SUBPIXELS
        val top = powerUp.y * Fixed.SUBPIXELS
        val size = PowerUp.SIZE_PIXELS * Fixed.SUBPIXELS
        return tank.x < left + size && tank.x + tank.sizeSub > left &&
            tank.y < top + size && tank.y + tank.sizeSub > top
    }

    private fun collectPowerUp(
        playerIndex: Int,
        type: PowerUpType,
        x: Int,
        y: Int,
    ) {
        val tank = players[playerIndex]
        events += GameEvent.PowerUpPicked(x, y, type)
        addScore(playerIndex, Scoring.POWER_UP)
        when (type) {
            PowerUpType.STAR -> setWeaponLevel(tank, tank.weaponLevel + 1)
            PowerUpType.GRENADE -> destroyAllEnemies(playerIndex)
            PowerUpType.HELMET ->
                tank.shieldTicks = maxOf(tank.shieldTicks, scaledTicks(HELMET_TICKS))
            PowerUpType.SHOVEL -> activateShovel()
            PowerUpType.CLOCK ->
                freezeTicks = maxOf(freezeTicks, scaledTicks(CLOCK_TICKS))
            PowerUpType.TANK -> {
                playerLives[playerIndex]++
                events += GameEvent.ExtraLife
            }
            PowerUpType.BOAT -> {
                tank.hasBoat = true
                tank.escapingWater = false
            }
            PowerUpType.GUN -> setWeaponLevel(tank, 3)
        }
    }

    private fun setWeaponLevel(tank: Tank, level: Int) {
        tank.weaponLevel = level.coerceIn(0, 3)
        tank.bulletSpeed = if (tank.weaponLevel >= 1) {
            FAST_BULLET_SPEED
        } else {
            Fixed.BULLET_SPEED
        }
    }

    private fun destroyAllEnemies(playerIndex: Int) {
        for (enemy in tanks) {
            if (!enemy.isPlayer && enemy.alive) {
                enemy.alive = false
                val type = enemy.type ?: EnemyType.BASIC
                playerKills[playerIndex][type.ordinal]++
                events += GameEvent.EnemyExploded(
                    enemy.x / Fixed.SUBPIXELS,
                    enemy.y / Fixed.SUBPIXELS,
                )
                spawnDirector.registerDefeat()
            }
        }
    }

    private fun activateShovel() {
        shovelTicks = scaledTicks(SHOVEL_TICKS)
        setEagleWall(Terrain.STEEL)
    }

    val shovelBlinking: Boolean
        get() = shovelTicks in 1 until SHOVEL_BLINK_TICKS

    private fun updateShovel() {
        if (shovelTicks <= 0) return
        shovelTicks--
        if (shovelTicks <= 0) {
            setEagleWall(Terrain.BRICK)
        }
    }

    private fun setEagleWall(material: Terrain) {
        for ((blockX, blockY) in EAGLE_WALL_BLOCKS) {
            if (isWallCellFree(blockX, blockY)) {
                terrain.set8(blockX, blockY, material)
            }
        }
    }

    private fun isWallCellFree(blockX: Int, blockY: Int): Boolean {
        val x = blockX * 8 * Fixed.SUBPIXELS
        val y = blockY * 8 * Fixed.SUBPIXELS
        val size = 8 * Fixed.SUBPIXELS
        for (tank in tanks) {
            if (!tank.alive) continue
            if (x < tank.right && x + size > tank.left &&
                y < tank.bottom && y + size > tank.top
            ) {
                return false
            }
        }
        return true
    }

    private fun spawnPowerUp(type: PowerUpType) {
        repeat(200) {
            val x = rng.nextInt(25) * 8
            val y = rng.nextInt(25) * 8
            if (isPowerUpAreaFree(x, y)) {
                powerUp = PowerUp(type, x, y, scaledTicks(POWER_UP_LIFETIME))
                events += GameEvent.PowerUpAppeared(x, y, type)
                return
            }
        }
    }

    /** Debug-only helper: spawn the chosen type next to the player. */
    fun debugSpawnPowerUp(type: PowerUpType) {
        val playerX = player.x / Fixed.SUBPIXELS
        val playerY = player.y / Fixed.SUBPIXELS
        val candidates = listOf(
            playerX to playerY - 32,
            playerX + 32 to playerY,
            playerX - 32 to playerY,
            playerX to playerY + 32,
        )
        for ((x, y) in candidates) {
            if (isPowerUpAreaFree(x, y)) {
                powerUp = PowerUp(type, x, y, scaledTicks(POWER_UP_LIFETIME))
                events += GameEvent.PowerUpAppeared(x, y, type)
                return
            }
        }
        spawnPowerUp(type)
    }

    private fun isPowerUpAreaFree(x: Int, y: Int): Boolean {
        val size = PowerUp.SIZE_PIXELS
        if (x < 0 || y < 0 ||
            x + size > Fixed.FIELD_PIXELS ||
            y + size > Fixed.FIELD_PIXELS
        ) {
            return false
        }

        val rightPx = x + size - 1
        val bottomPx = y + size - 1
        for (cellY in y / 4..bottomPx / 4) {
            for (cellX in x / 4..rightPx / 4) {
                when (terrain.get(cellX, cellY)) {
                    Terrain.EMPTY, Terrain.ICE -> Unit
                    else -> return false
                }
            }
        }

        val left = x * Fixed.SUBPIXELS
        val top = y * Fixed.SUBPIXELS
        val sizeSub = size * Fixed.SUBPIXELS

        for (tank in tanks) {
            if (!tank.alive) continue
            if (left < tank.right && left + sizeSub > tank.left &&
                top < tank.bottom && top + sizeSub > tank.top
            ) {
                return false
            }
        }

        if (left < eagle.right && left + sizeSub > eagle.left &&
            top < eagle.bottom && top + sizeSub > eagle.top
        ) {
            return false
        }

        for (animation in spawnAnimations) {
            val ax = animation.blockX * Fixed.TANK_SUBPIXELS
            val ay = animation.blockY * Fixed.TANK_SUBPIXELS
            if (left < ax + Fixed.TANK_SUBPIXELS && left + sizeSub > ax &&
                top < ay + Fixed.TANK_SUBPIXELS && top + sizeSub > ay
            ) {
                return false
            }
        }

        return true
    }

    private fun scaledTicks(base: Int): Int =
        (base * difficulty.powerUpDurationMultiplier).toInt().coerceAtLeast(1)

    private fun updateRespawn() {
        if (state != WorldState.PLAYING) return
        for (index in players.indices) {
            val tank = players[index]
            if (tank.alive || playerLives[index] <= 0) continue
            if (playerRespawnTicks[index] > 0) playerRespawnTicks[index]--
            if (playerRespawnTicks[index] == 0) respawnPlayer(index)
        }
    }

    private fun updateShield() {
        for (tank in players) {
            if (tank.shieldTicks > 0) tank.shieldTicks--
        }
    }

    private fun respawnPlayer(index: Int) {
        val tank = players[index]
        val spawnX = if (index == 0) respawnPixelX else respawnPixelX2
        val spawnY = if (index == 0) respawnPixelY else respawnPixelY2
        val spawnSubX = spawnX * Fixed.SUBPIXELS
        val spawnSubY = spawnY * Fixed.SUBPIXELS

        if (!canOccupy(tank, spawnSubX, spawnSubY)) {
            playerRespawnTicks[index] = 1
            return
        }

        tank.x = spawnSubX
        tank.y = spawnSubY
        tank.dir = Dir.UP
        tank.alive = true
        tank.shieldTicks = SHIELD_TICKS
        tank.stunTicks = 0
        tank.weaponLevel = 0
        tank.bulletSpeed = Fixed.BULLET_SPEED
        tank.hasBoat = false
        tank.escapingWater = false
        tank.slideRemaining = 0
        tank.wasMoving = false
        tank.resetTracks()
        playerRespawnTicks[index] = 0
    }

    private fun isPerpendicular(first: Dir, second: Dir): Boolean =
        (first.dx != 0 && second.dy != 0) || (first.dy != 0 && second.dx != 0)

    private fun alignForTurn(tank: Tank, newDirection: Dir) {
        tank.x = turnX(tank, newDirection)
        tank.y = turnY(tank, newDirection)
    }

    /** X after a 90-degree turn: snapped to the 8 px grid when that spot is free. */
    private fun turnX(tank: Tank, newDirection: Dir): Int {
        if (tank.dir.dx == 0 || newDirection.dy == 0) return tank.x
        val snapped = snapToBlock(tank.x)
        return if (canOccupy(tank, snapped, tank.y)) snapped else tank.x
    }

    private fun turnY(tank: Tank, newDirection: Dir): Int {
        if (tank.dir.dy == 0 || newDirection.dx == 0) return tank.y
        val snapped = snapToBlock(tank.y)
        return if (canOccupy(tank, tank.x, snapped)) snapped else tank.y
    }

    /** Whether an enemy turning to [direction] (with grid alignment) can move. */
    internal fun canTurn(tank: Tank, direction: Dir): Boolean =
        canOccupy(
            tank,
            turnX(tank, direction) + direction.dx,
            turnY(tank, direction) + direction.dy,
        )

    /** Enemies align to the grid on 90-degree turns, like the player. */
    internal fun turnEnemy(tank: Tank, direction: Dir) {
        if (direction != tank.dir && isPerpendicular(tank.dir, direction)) {
            alignForTurn(tank, direction)
        }
        tank.dir = direction
    }

    private fun snapToBlock(value: Int): Int {
        val block = Fixed.SUBPIXELS * 8
        val half = Fixed.SUBPIXELS * 4
        val snapped = ((value + half) / block) * block
        return snapped.coerceIn(0, Fixed.FIELD_SUBPIXELS - Fixed.TANK_SUBPIXELS)
    }

    private fun moveTank(tank: Tank, direction: Dir): Boolean {
        var remaining = tank.speed
        var moved = false

        while (remaining > 0) {
            val nextX = tank.x + direction.dx
            val nextY = tank.y + direction.dy
            if (!canOccupy(tank, nextX, nextY)) break

            tank.x = nextX
            tank.y = nextY
            remaining--
            moved = true
        }

        if (moved) tank.advanceTrack()
        return moved
    }

    internal fun moveEnemy(enemy: Tank) {
        moveTank(enemy, enemy.dir)
    }

    internal fun canMove(tank: Tank, direction: Dir): Boolean =
        canOccupy(tank, tank.x + direction.dx, tank.y + direction.dy)

    private fun moveSlide(tank: Tank): Boolean {
        var budget = minOf(tank.speed, tank.slideRemaining)
        var moved = false

        while (budget > 0) {
            val nextX = tank.x + tank.dir.dx
            val nextY = tank.y + tank.dir.dy
            if (!canOccupy(tank, nextX, nextY)) {
                tank.slideRemaining = 0
                break
            }

            tank.x = nextX
            tank.y = nextY
            tank.slideRemaining--
            budget--
            moved = true
        }

        if (moved) tank.advanceTrack()
        return moved
    }

    private fun canOccupy(tank: Tank, x: Int, y: Int): Boolean {
        if (x < 0 || y < 0) return false
        if (x + tank.sizeSub > Fixed.FIELD_SUBPIXELS ||
            y + tank.sizeSub > Fixed.FIELD_SUBPIXELS
        ) {
            return false
        }
        if (overlapsEagle(x, y, tank.sizeSub)) return false

        for (animation in spawnAnimations) {
            val ax = animation.blockX * Fixed.TANK_SUBPIXELS
            val ay = animation.blockY * Fixed.TANK_SUBPIXELS
            if (x < ax + Fixed.TANK_SUBPIXELS && x + tank.sizeSub > ax &&
                y < ay + Fixed.TANK_SUBPIXELS && y + tank.sizeSub > ay
            ) {
                return false
            }
        }

        val leftPx = x / Fixed.SUBPIXELS
        val topPx = y / Fixed.SUBPIXELS
        val rightPx = (x + tank.sizeSub - 1) / Fixed.SUBPIXELS
        val bottomPx = (y + tank.sizeSub - 1) / Fixed.SUBPIXELS

        for (cellY in topPx / 4..bottomPx / 4) {
            for (cellX in leftPx / 4..rightPx / 4) {
                val cell = terrain.get(cellX, cellY)
                if (terrain.blocksTank(cellX, cellY)) {
                    val boatOnWater = tank.isPlayer &&
                        (tank.hasBoat || tank.escapingWater) &&
                        cell == Terrain.WATER
                    if (!boatOnWater) return false
                }
            }
        }

        for (other in tanks) {
            if (other === tank || !other.alive) continue
            if (x < other.right && x + tank.sizeSub > other.left &&
                y < other.bottom && y + tank.sizeSub > other.top
            ) {
                return false
            }
        }

        return true
    }

    private fun overlapsEagle(x: Int, y: Int, size: Int): Boolean =
        x < eagle.right && x + size > eagle.left &&
            y < eagle.bottom && y + size > eagle.top

    private fun isOnIce(tank: Tank): Boolean {
        val centerX = (tank.x + tank.sizeSub / 2) / Fixed.SUBPIXELS
        val centerY = (tank.y + tank.sizeSub / 2) / Fixed.SUBPIXELS
        return terrain.get(centerX / 4, centerY / 4) == Terrain.ICE
    }

    internal fun fire(tank: Tank) {
        tryFire(tank)
    }

    private fun tryFire(tank: Tank) {
        val maxBullets = if (tank.weaponLevel >= 2) 2 else 1
        if (bullets.count { it.owner === tank } >= maxBullets) return

        val halfTank = tank.sizeSub / 2
        val halfBullet = Fixed.BULLET_SUBPIXELS / 2
        val offset = halfTank + halfBullet
        val bulletX = tank.x + halfTank - halfBullet + tank.dir.dx * offset
        val bulletY = tank.y + halfTank - halfBullet + tank.dir.dy * offset

        bullets += Bullet(
            x = bulletX,
            y = bulletY,
            dir = tank.dir,
            owner = tank,
            weaponLevel = tank.weaponLevel,
            speed = tank.bulletSpeed,
        )
        events += GameEvent.ShotFired(
            x = bulletX / Fixed.SUBPIXELS,
            y = bulletY / Fixed.SUBPIXELS,
            isPlayer = tank.isPlayer,
        )
    }

    internal fun isSpawnAreaFree(
        blockX: Int,
        blockY: Int,
        ignore: SpawnAnimation? = null,
    ): Boolean {
        val x = blockX * Fixed.TANK_SUBPIXELS
        val y = blockY * Fixed.TANK_SUBPIXELS
        val size = Fixed.TANK_SUBPIXELS

        val leftPx = x / Fixed.SUBPIXELS
        val topPx = y / Fixed.SUBPIXELS
        val rightPx = (x + size - 1) / Fixed.SUBPIXELS
        val bottomPx = (y + size - 1) / Fixed.SUBPIXELS
        for (cellY in topPx / 4..bottomPx / 4) {
            for (cellX in leftPx / 4..rightPx / 4) {
                if (terrain.blocksTank(cellX, cellY)) return false
            }
        }

        for (tank in tanks) {
            if (!tank.alive) continue
            if (x < tank.right && x + size > tank.left &&
                y < tank.bottom && y + size > tank.top
            ) {
                return false
            }
        }

        for (animation in spawnAnimations) {
            if (animation === ignore) continue
            val ax = animation.blockX * size
            val ay = animation.blockY * size
            if (x < ax + size && x + size > ax &&
                y < ay + size && y + size > ay
            ) {
                return false
            }
        }

        return true
    }

    private fun updateBullets() {
        if (bullets.isEmpty()) return

        // Resolve annulment before applying any impact: two bullets that cancel
        // in this tick must not damage terrain or the eagle.
        val paths = bullets.map { simulate(it) }

        for (first in paths.indices) {
            for (second in first + 1 until paths.size) {
                if (paths[first].annulled || paths[second].annulled) continue
                if (paths[first].bullet.owner === paths[second].bullet.owner) continue
                if (pathsCollide(paths[first], paths[second])) {
                    paths[first].annulled = true
                    paths[second].annulled = true
                }
            }
        }

        val survivors = mutableListOf<Bullet>()
        for (path in paths) {
            if (path.annulled) continue

            path.impact?.let { applyImpact(it) }

            if (path.alive) {
                path.bullet.x = path.endX
                path.bullet.y = path.endY
                survivors += path.bullet
            }
        }

        bullets.clear()
        bullets.addAll(survivors)
        for (tank in players) {
            if (!tank.alive) bullets.removeAll { it.owner === tank }
        }
    }

    private fun simulate(bullet: Bullet): BulletPath {
        val path = BulletPath(
            bullet = bullet,
            startX = bullet.x,
            startY = bullet.y,
            endX = bullet.x,
            endY = bullet.y,
            alive = true,
        )

        var x = bullet.x
        var y = bullet.y

        val muzzleImpact = impactAt(bullet, x, y)
        if (muzzleImpact != null) {
            path.impact = muzzleImpact
            path.alive = false
            return path
        }

        var steps = bullet.speed / BULLET_STEP_SUBPIXELS

        while (steps > 0) {
            val nextX = x + bullet.dir.dx * BULLET_STEP_SUBPIXELS
            val nextY = y + bullet.dir.dy * BULLET_STEP_SUBPIXELS

            if (nextX < 0 || nextY < 0 ||
                nextX + bullet.sizeSub > Fixed.FIELD_SUBPIXELS ||
                nextY + bullet.sizeSub > Fixed.FIELD_SUBPIXELS
            ) {
                path.alive = false
                break
            }

            x = nextX
            y = nextY

            val impact = impactAt(bullet, x, y)
            if (impact != null) {
                path.impact = impact
                path.alive = false
                break
            }

            steps--
        }

        path.endX = x
        path.endY = y
        return path
    }

    private fun impactAt(bullet: Bullet, x: Int, y: Int): Impact? {
        for (tank in tanks) {
            if (!tank.alive || tank === bullet.owner) continue
            val overlaps = x < tank.right && x + bullet.sizeSub > tank.left &&
                y < tank.bottom && y + bullet.sizeSub > tank.top
            if (!overlaps) continue

            if (tank.isPlayer) {
                val friendly = bullet.owner.isPlayer
                return Impact.TankHit(tank, bullet.owner, friendly)
            }
            if (!bullet.owner.isPlayer) continue
            return Impact.TankHit(tank, bullet.owner, friendly = false)
        }

        val leftPx = x / Fixed.SUBPIXELS
        val topPx = y / Fixed.SUBPIXELS
        val rightPx = (x + bullet.sizeSub - 1) / Fixed.SUBPIXELS
        val bottomPx = (y + bullet.sizeSub - 1) / Fixed.SUBPIXELS

        for (cellY in topPx / 4..bottomPx / 4) {
            for (cellX in leftPx / 4..rightPx / 4) {
                when (terrain.get(cellX, cellY)) {
                    Terrain.BRICK -> {
                        val hitX = if (bullet.dir.dy != 0) {
                            brickStripX(bullet, x, cellX, cellY)
                        } else {
                            cellX
                        }
                        val hitY = if (bullet.dir.dx != 0) {
                            brickStripY(bullet, y, cellX, cellY)
                        } else {
                            cellY
                        }
                        return Impact.Brick(hitX, hitY, bullet.dir)
                    }

                    Terrain.STEEL -> return if (bullet.weaponLevel >= 3) {
                        Impact.Cell8(cellX, cellY, Terrain.STEEL)
                    } else {
                        Impact.Stop(cellX, cellY)
                    }

                    Terrain.TREES -> {
                        if (bullet.weaponLevel >= 3) {
                            return Impact.Cell8(cellX, cellY, Terrain.TREES)
                        }
                    }

                    else -> Unit
                }
            }
        }

        if (x < eagle.right && x + bullet.sizeSub > eagle.left &&
            y < eagle.bottom && y + bullet.sizeSub > eagle.top
        ) {
            return Impact.Eagle
        }

        return null
    }

    private fun brickStripX(bullet: Bullet, x: Int, hitX: Int, hitY: Int): Int {
        val centerX = (x + bullet.sizeSub / 2) / Fixed.SUBPIXELS / 4
        return if (terrain.get(centerX, hitY) == Terrain.BRICK) centerX else hitX
    }

    private fun brickStripY(bullet: Bullet, y: Int, hitX: Int, hitY: Int): Int {
        val centerY = (y + bullet.sizeSub / 2) / Fixed.SUBPIXELS / 4
        return if (terrain.get(hitX, centerY) == Terrain.BRICK) centerY else hitY
    }

    private fun applyImpact(impact: Impact) {
        when (impact) {
            is Impact.Brick -> {
                events += GameEvent.BrickBroken(impact.cellX * 4, impact.cellY * 4)
                terrain.destroyBrick(impact.cellX, impact.cellY, impact.direction)
            }

            is Impact.Cell8 -> {
                events += if (impact.material == Terrain.STEEL) {
                    GameEvent.SteelHit(impact.cellX * 4, impact.cellY * 4)
                } else {
                    GameEvent.BrickBroken(impact.cellX * 4, impact.cellY * 4)
                }
                terrain.destroyCell8(impact.cellX, impact.cellY)
            }

            is Impact.TankHit ->
                hitTank(impact.tank, impact.owner, impact.friendly)

            is Impact.Stop -> {
                events += GameEvent.SteelHit(impact.cellX * 4, impact.cellY * 4)
            }

            Impact.Eagle -> {
                eagle.alive = false
                events += GameEvent.EagleDestroyed(
                    eagle.x / Fixed.SUBPIXELS,
                    eagle.y / Fixed.SUBPIXELS,
                )
                if (state != WorldState.GAME_OVER) {
                    state = WorldState.GAME_OVER
                    events += GameEvent.GameOver
                }
            }
        }
    }

    private fun hitTank(tank: Tank, owner: Tank, friendly: Boolean) {
        if (friendly) {
            if (!tank.alive || tank.shieldTicks > 0) return
            tank.stunTicks = maxOf(tank.stunTicks, STUN_TICKS)
            tank.slideRemaining = 0
            tank.wasMoving = false
            return
        }

        if (tank.isPlayer) {
            val index = players.indexOf(tank)
            if (index < 0) return
            if (!tank.alive || tank.shieldTicks > 0) return
            if (tank.hasBoat) {
                tank.hasBoat = false
                tank.escapingWater = tankOverlapsWater(tank)
                return
            }
            killPlayer(index)
            return
        }

        if (!tank.alive) return

        if (tank.flashing && !tank.droppedPowerUp) {
            tank.droppedPowerUp = true
            pendingPowerUpDrops++
        }

        tank.hp--
        if (tank.hp <= 0) {
            tank.alive = false
            val type = tank.type ?: EnemyType.BASIC
            val killer = players.indexOf(owner).takeIf { it >= 0 } ?: 0
            playerKills[killer][type.ordinal]++
            events += GameEvent.EnemyExploded(
                tank.x / Fixed.SUBPIXELS,
                tank.y / Fixed.SUBPIXELS,
            )
            val points = Scoring.pointsFor(type)
            playerScoreByType[killer][type.ordinal] += points
            addScore(killer, points)
            spawnDirector.registerDefeat()
        }
    }

    /** Player-one alias kept for the single-player flow and tests. */
    fun addScore(points: Int) = addScore(0, points)

    /** Single score entry point so future power-up points also grant extra lives. */
    fun addScore(playerIndex: Int, points: Int) {
        require(playerIndex in 0 until playerCount) { "player index out of range" }
        playerScores[playerIndex] += points
        while (playerScores[playerIndex] >= playerNextExtraLife[playerIndex]) {
            playerLives[playerIndex]++
            playerNextExtraLife[playerIndex] += Scoring.EXTRA_LIFE_STEP
            events += GameEvent.ExtraLife
        }
    }

    private fun killPlayer(index: Int) {
        val tank = players[index]
        if (!tank.alive) return
        tank.alive = false
        tank.weaponLevel = 0
        tank.bulletSpeed = Fixed.BULLET_SPEED
        tank.hasBoat = false
        tank.escapingWater = false
        tank.stunTicks = 0
        tank.slideRemaining = 0
        tank.wasMoving = false
        bullets.removeAll { it.owner === tank }
        events += GameEvent.PlayerExploded(
            tank.x / Fixed.SUBPIXELS,
            tank.y / Fixed.SUBPIXELS,
        )
        playerLives[index]--
        playerRespawnTicks[index] = if (playerLives[index] > 0) RESPAWN_TICKS else 0
        if (playerLives.all { it <= 0 } && state != WorldState.GAME_OVER) {
            state = WorldState.GAME_OVER
            events += GameEvent.GameOver
        }
    }

    /**
     * Samples both linear paths so crossing bullets annul, while bullets flying
     * side by side in the same direction do not.
     */
    private fun pathsCollide(first: BulletPath, second: BulletPath): Boolean {
        for (sample in 0..COLLISION_SAMPLES) {
            val firstX = first.startX +
                (first.endX - first.startX) * sample / COLLISION_SAMPLES
            val firstY = first.startY +
                (first.endY - first.startY) * sample / COLLISION_SAMPLES
            val secondX = second.startX +
                (second.endX - second.startX) * sample / COLLISION_SAMPLES
            val secondY = second.startY +
                (second.endY - second.startY) * sample / COLLISION_SAMPLES

            if (firstX < secondX + Fixed.BULLET_SUBPIXELS &&
                firstX + Fixed.BULLET_SUBPIXELS > secondX &&
                firstY < secondY + Fixed.BULLET_SUBPIXELS &&
                firstY + Fixed.BULLET_SUBPIXELS > secondY
            ) {
                return true
            }
        }
        return false
    }

    /** Captures everything the guest needs to draw this tick. */
    fun captureSnapshot(): WorldSnapshot {
        val enemies = tanks.filter { !it.isPlayer }
        return WorldSnapshot(
            tick = tick,
            state = state,
            levelNumber = levelNumber,
            difficulty = difficulty,
            playerCount = playerCount,
            lives = List(playerCount) { playerLives[it] },
            scores = List(playerCount) { playerScores[it] },
            enemiesRemaining = enemiesRemaining,
            totalEnemies = totalEnemies,
            eagleAlive = eagle.alive,
            players = players.map { it.captureState() },
            enemies = enemies.map { it.captureState() },
            bullets = bullets.map { bullet ->
                val ownerPlayer = players.indexOf(bullet.owner).takeIf { it >= 0 }
                val ownerEnemy = if (ownerPlayer == null) {
                    enemies.indexOf(bullet.owner).takeIf { it >= 0 }
                } else {
                    null
                }
                BulletState(
                    x = bullet.x,
                    y = bullet.y,
                    dir = bullet.dir,
                    ownerPlayer = ownerPlayer,
                    ownerEnemy = ownerEnemy,
                    weaponLevel = bullet.weaponLevel,
                    speed = bullet.speed,
                )
            },
            powerUp = powerUp?.let { PowerUpState(it.type, it.x, it.y, it.ticksRemaining) },
            freezeTicks = freezeTicks,
            shovelTicks = shovelTicks,
        )
    }

    /**
     * Replaces the visible state with a host snapshot. The guest never steps
     * the simulation; it only renders (and drains events for sound).
     */
    fun applySnapshot(snapshot: WorldSnapshot) {
        require(snapshot.playerCount == playerCount) {
            "snapshot has ${snapshot.playerCount} players, world has $playerCount"
        }
        tick = snapshot.tick
        state = snapshot.state
        for (index in players.indices) {
            playerLives[index] = snapshot.lives[index]
            playerScores[index] = snapshot.scores[index]
            players[index].applyState(snapshot.players[index])
        }
        remoteEnemiesRemaining = snapshot.enemiesRemaining
        remoteTotalEnemies = snapshot.totalEnemies
        eagle.alive = snapshot.eagleAlive

        tanks.removeAll { !it.isPlayer }
        val enemies = snapshot.enemies.map { state ->
            Tank(
                x = state.x,
                y = state.y,
                dir = state.dir,
                isPlayer = false,
                weaponLevel = state.weaponLevel,
                type = state.type ?: EnemyType.BASIC,
                hp = state.hp,
                flashing = state.flashing,
            ).also { it.applyState(state) }
        }
        tanks.addAll(enemies)
        spawnAnimations.clear()

        bullets.clear()
        for (state in snapshot.bullets) {
            val owner = when {
                state.ownerPlayer != null -> players.getOrNull(state.ownerPlayer)
                state.ownerEnemy != null -> enemies.getOrNull(state.ownerEnemy)
                else -> null
            } ?: dummyNetBulletOwner()
            bullets += Bullet(
                x = state.x,
                y = state.y,
                dir = state.dir,
                owner = owner,
                weaponLevel = state.weaponLevel,
                speed = state.speed,
            )
        }

        powerUp = snapshot.powerUp?.let {
            PowerUp(it.type, it.x, it.y, it.ticksRemaining)
        }
        freezeTicks = snapshot.freezeTicks
        shovelTicks = snapshot.shovelTicks
    }

    private fun dummyNetBulletOwner(): Tank {
        val existing = netBulletOwner
        if (existing != null) return existing
        return Tank(x = 0, y = 0, isPlayer = false).also {
            it.alive = false
            it.aiEnabled = false
            netBulletOwner = it
        }
    }

    private fun Tank.captureState(): TankState = TankState(
        x = x,
        y = y,
        dir = dir,
        trackFrame = trackFrame,
        weaponLevel = weaponLevel,
        alive = alive,
        shieldTicks = shieldTicks,
        stunTicks = stunTicks,
        hasBoat = hasBoat,
        escapingWater = escapingWater,
        slideRemaining = slideRemaining,
        wasMoving = wasMoving,
        bulletSpeed = bulletSpeed,
        type = type,
        hp = hp,
        // The guest only needs to know whether the tank still carries a drop.
        flashing = flashing && !droppedPowerUp,
    )

    private fun Tank.applyState(state: TankState) {
        x = state.x
        y = state.y
        dir = state.dir
        restoreTrack(state.trackFrame)
        weaponLevel = state.weaponLevel
        alive = state.alive
        shieldTicks = state.shieldTicks
        stunTicks = state.stunTicks
        hasBoat = state.hasBoat
        escapingWater = state.escapingWater
        slideRemaining = state.slideRemaining
        wasMoving = state.wasMoving
        bulletSpeed = state.bulletSpeed
        hp = state.hp
    }

    private sealed class Impact {
        class Brick(val cellX: Int, val cellY: Int, val direction: Dir) : Impact()
        class Cell8(
            val cellX: Int,
            val cellY: Int,
            val material: Terrain,
        ) : Impact()
        class TankHit(
            val tank: Tank,
            val owner: Tank,
            val friendly: Boolean,
        ) : Impact()
        class Stop(val cellX: Int, val cellY: Int) : Impact()
        data object Eagle : Impact()
    }

    private class BulletPath(
        val bullet: Bullet,
        val startX: Int,
        val startY: Int,
        var endX: Int,
        var endY: Int,
        var alive: Boolean,
        var impact: Impact? = null,
        var annulled: Boolean = false,
    )

    companion object {
        const val RESPAWN_TICKS = 60
        const val SHIELD_TICKS = 180
        const val STUN_TICKS = 120
        const val POWER_UP_LIFETIME = 900
        const val HELMET_TICKS = 600
        const val SHOVEL_TICKS = 1_200
        const val CLOCK_TICKS = 600
        const val FAST_BULLET_SPEED = 96
        private const val SHOVEL_BLINK_TICKS = 120
        private const val COLLISION_SAMPLES = 16
        private const val BULLET_STEP_SUBPIXELS = Fixed.SUBPIXELS
        private const val CYCLE_SPEED_STEP = 0.1f

        val EAGLE_WALL_BLOCKS = listOf(
            11 to 23,
            12 to 23,
            13 to 23,
            14 to 23,
            11 to 24,
            11 to 25,
            14 to 24,
            14 to 25,
        )

        fun fromLevel(
            level: Level,
            seed: Int = 1,
            difficulty: Difficulty = Difficulty.NORMAL,
            levelNumber: Int = 1,
            cycle: Int = 0,
            initialLives: Int = difficulty.initialLives,
            initialScore: Int = 0,
            initialNextExtraLife: Int = Scoring.EXTRA_LIFE_STEP,
            playerCount: Int = 1,
            initialLivesP2: Int = difficulty.initialLives,
            initialScoreP2: Int = 0,
            initialNextExtraLifeP2: Int = Scoring.EXTRA_LIFE_STEP,
            initialWeaponLevel: Int = 0,
            initialWeaponLevelP2: Int = 0,
        ): World {
            val roster = if (level.enemies.isBlank()) {
                emptyList()
            } else {
                EnemyRoster.parse(level.enemies, seed * 31 + levelNumber)
            }
            return World(
                terrain = TerrainGrid.fromRows(level.map),
                seed = seed,
                difficulty = difficulty,
                levelNumber = levelNumber,
                cycle = cycle,
                enemyRoster = roster,
                initialLives = initialLives,
                initialScore = initialScore,
                initialNextExtraLife = initialNextExtraLife,
                playerCount = playerCount,
                initialLivesP2 = initialLivesP2,
                initialScoreP2 = initialScoreP2,
                initialNextExtraLifeP2 = initialNextExtraLifeP2,
                initialWeaponLevel = initialWeaponLevel,
                initialWeaponLevelP2 = initialWeaponLevelP2,
            )
        }
    }
}
