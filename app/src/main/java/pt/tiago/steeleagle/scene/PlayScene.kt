package pt.tiago.steeleagle.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.SparseArray
import pt.tiago.steeleagle.audio.AudioEngine
import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.game.GameSession
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.PowerUpType
import pt.tiago.steeleagle.core.sprite.EnemySprites
import pt.tiago.steeleagle.core.sprite.TankSprites
import pt.tiago.steeleagle.core.world.Bullet
import pt.tiago.steeleagle.core.world.InputFrame
import pt.tiago.steeleagle.core.world.Tank
import pt.tiago.steeleagle.core.world.World
import pt.tiago.steeleagle.core.world.WorldState
import pt.tiago.steeleagle.render.EnemyColors
import pt.tiago.steeleagle.render.PixelFont
import pt.tiago.steeleagle.render.PowerUpIcons
import pt.tiago.steeleagle.render.SpriteAtlas
import pt.tiago.steeleagle.render.TerrainRenderer

/**
 * One stage of a [GameSession]: field, side panel, explosions and audio.
 * Screen flow (stage curtain, tally, pause, game over) belongs to GameFlow.
 */
class PlayScene(
    val session: GameSession,
    private val atlas: SpriteAtlas,
    private val font: PixelFont,
    private val audio: AudioEngine? = null,
    /** Guest mode: the net layer applies snapshots, so never step the world. */
    private val remote: Boolean = false,
) : Scene {

    /** Host hook: every event is forwarded to the network before it is cleared. */
    var eventSink: ((GameEvent) -> Unit)? = null

    private val world: World get() = session.world
    private val terrainRenderer = TerrainRenderer(atlas)

    // Every sprite and colour filter is resolved once; the draw path only
    // indexes arrays, so it makes no material allocations per frame.
    private val tankSprites = Array(Dir.entries.size) { index ->
        Array(2) { frame -> atlas.bitmap(TankSprites.name(Dir.entries[index], frame)) }
    }
    private val enemySprites = Array(Dir.entries.size) { index ->
        Array(2) { frame -> atlas.bitmap(EnemySprites.name(Dir.entries[index], frame)) }
    }
    private val shieldSprites = arrayOf(atlas.bitmap("shield_0"), atlas.bitmap("shield_1"))
    private val boatRing = atlas.bitmap("boat_ring")
    private val bulletSprite = atlas.bitmap("bullet")
    private val eagleAlive = atlas.bitmap("eagle_alive")
    private val eagleDead = atlas.bitmap("eagle_dead")
    private val hudEnemy = atlas.bitmap("hud_enemy")
    private val hudFlag = atlas.bitmap("hud_flag")
    private val smallExplosions = Array(3) { atlas.bitmap("explosion_small_$it") }
    private val largeExplosions = Array(2) { atlas.bitmap("explosion_large_$it") }
    private val spawnStars = Array(4) { atlas.bitmap("spawn_star_$it") }
    private val powerIcons = PowerUpType.entries.associateWith {
        atlas.bitmap(PowerUpIcons.name(it))
    }
    private val p2Filter = PorterDuffColorFilter(P2_GREEN, PorterDuff.Mode.MULTIPLY)
    private val enemyFilters = SparseArray<PorterDuffColorFilter>(8)
    private val paint = Paint().apply { isAntiAlias = false }
    private val enemyPaint = Paint().apply { isAntiAlias = false }
    private val playerPaint = Paint().apply { isAntiAlias = false }
    private val p2TextPaint = Paint().apply {
        isAntiAlias = false
        colorFilter = PorterDuffColorFilter(P2_GREEN, PorterDuff.Mode.MULTIPLY)
    }

    @Volatile
    private var pendingDebugPowerUp: PowerUpType? = null

    private val explosions = mutableListOf<Explosion>()

    // HUD strings are rebuilt only when their value changes, so the gameplay
    // draw path performs no string allocation per frame.
    private val hudValues = IntArray(HUD_SLOTS) { Int.MIN_VALUE }
    private val hudWidths = IntArray(HUD_SLOTS) { -1 }
    private val hudStrings = arrayOfNulls<String>(HUD_SLOTS)

    override fun update(input: PlayerInput, second: PlayerInput) {
        val pending = pendingDebugPowerUp
        pendingDebugPowerUp = null
        if (pending != null && world.state == WorldState.PLAYING) {
            world.debugSpawnPowerUp(pending)
        }

        if (!remote && world.state == WorldState.PLAYING) {
            world.step(InputFrame(input, second))
        }

        updateExplosions()
        drainEvents()
        audio?.setEngine(
            active = world.state == WorldState.PLAYING &&
                world.players.any { it.alive },
            moving = world.players.any { it.alive && it.isMoving },
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        terrainRenderer.update(world.terrain, world.tick)
        terrainRenderer.drawBase(canvas)
        drawShovelBlink(canvas)

        drawEagle(canvas)
        drawSpawnAnimations(canvas)
        drawPowerUp(canvas)
        for (tank in world.tanks) drawTank(canvas, tank)
        for (bullet in world.bullets) drawBullet(canvas, bullet)
        drawExplosions(canvas)

        terrainRenderer.drawTrees(canvas)
        drawFieldBorder(canvas)
        drawPanel(canvas)
    }

    /** Debug-only hook used by the top-row power-up menu (UI thread -> render thread). */
    fun debugSpawnPowerUp(type: PowerUpType) {
        pendingDebugPowerUp = type
    }

    private fun drainEvents() {
        if (world.events.isEmpty()) return

        for (event in world.events) {
            eventSink?.invoke(event)
            audio?.onEvent(event)
            when (event) {
                is GameEvent.EnemyExploded -> addExplosion(event.x, event.y, big = false)
                is GameEvent.BrickBroken ->
                    addExplosion(event.x - 6, event.y - 6, big = false)
                is GameEvent.SteelHit ->
                    addExplosion(event.x - 6, event.y - 6, big = false)
                is GameEvent.PlayerExploded -> addExplosion(event.x, event.y, big = true)
                is GameEvent.EagleDestroyed -> addExplosion(event.x, event.y, big = true)
                is GameEvent.ShotFired,
                is GameEvent.PowerUpAppeared,
                is GameEvent.PowerUpPicked,
                GameEvent.ExtraLife,
                GameEvent.LevelClear,
                GameEvent.GameOver,
                -> Unit
            }
        }
        world.events.clear()
    }

    private fun addExplosion(x: Int, y: Int, big: Boolean) {
        val total = if (big) LARGE_EXPLOSION_TICKS else SMALL_EXPLOSION_TICKS
        explosions += Explosion(x, y, big, total, total)
    }

    private fun updateExplosions() {
        val iterator = explosions.iterator()
        while (iterator.hasNext()) {
            val explosion = iterator.next()
            explosion.ticksRemaining--
            if (explosion.ticksRemaining <= 0) iterator.remove()
        }
    }

    private fun drawExplosions(canvas: Canvas) {
        for (explosion in explosions) {
            val elapsed = explosion.totalTicks - explosion.ticksRemaining
            val frame = if (explosion.big) {
                (elapsed / LARGE_FRAME_TICKS).coerceAtMost(1)
            } else {
                (elapsed / SMALL_FRAME_TICKS).coerceAtMost(2)
            }
            val sprite = if (explosion.big) {
                largeExplosions[frame]
            } else {
                smallExplosions[frame]
            }
            canvas.drawBitmap(
                sprite,
                explosion.x.toFloat(),
                explosion.y.toFloat(),
                null,
            )
        }
    }

    private fun drawEagle(canvas: Canvas) {
        val sprite = if (world.eagle.alive) eagleAlive else eagleDead
        canvas.drawBitmap(
            sprite,
            (world.eagle.x / Fixed.SUBPIXELS).toFloat(),
            (world.eagle.y / Fixed.SUBPIXELS).toFloat(),
            null,
        )
    }

    private fun drawSpawnAnimations(canvas: Canvas) {
        if (world.spawnAnimations.isEmpty()) return
        val sprite = spawnStars[((world.tick / 8) % 4).toInt()]
        for (animation in world.spawnAnimations) {
            canvas.drawBitmap(
                sprite,
                (animation.blockX * BLOCK_PIXELS).toFloat(),
                (animation.blockY * BLOCK_PIXELS).toFloat(),
                null,
            )
        }
    }

    private fun drawShovelBlink(canvas: Canvas) {
        if (!world.shovelBlinking) return
        if ((world.tick / 8) % 2 != 0L) return

        val brick = atlas.bitmap("terrain_brick")
        for ((blockX, blockY) in World.EAGLE_WALL_BLOCKS) {
            for (dy in 0..1) {
                for (dx in 0..1) {
                    canvas.drawBitmap(
                        brick,
                        (blockX * 8 + dx * 4).toFloat(),
                        (blockY * 8 + dy * 4).toFloat(),
                        null,
                    )
                }
            }
        }
    }

    private fun drawPowerUp(canvas: Canvas) {
        val powerUp = world.powerUp ?: return
        if (!powerUp.isVisible(world.tick)) return

        canvas.drawBitmap(
            powerIcons.getValue(powerUp.type),
            powerUp.x.toFloat(),
            powerUp.y.toFloat(),
            null,
        )
    }

    private fun drawTank(canvas: Canvas, tank: Tank) {
        if (!tank.alive) return
        if (tank.stunTicks > 0 && (world.tick / 4) % 2 == 0L) return

        val x = (tank.x / Fixed.SUBPIXELS).toFloat()
        val y = (tank.y / Fixed.SUBPIXELS).toFloat()

        if (tank.isPlayer) {
            val playerIndex = world.players.indexOf(tank)
            playerPaint.colorFilter = if (playerIndex == 1) p2Filter else null
            canvas.drawBitmap(
                tankSprites[tank.dir.ordinal][tank.trackFrame],
                x,
                y,
                if (playerIndex == 1) playerPaint else null,
            )
            if (tank.hasBoat) {
                canvas.drawBitmap(boatRing, x, y, null)
            }
            if (tank.shieldTicks > 0) {
                canvas.drawBitmap(shieldSprites[((world.tick / 8) % 2).toInt()], x, y, null)
            }
        } else {
            // A carrier stops flashing once its power-up has dropped.
            val flashing = tank.flashing && !tank.droppedPowerUp &&
                (world.tick / 4) % 2 == 0L
            val tint = if (flashing) {
                0xFF00B8F8.toInt()
            } else {
                EnemyColors.tint(tank.type, tank.hp)
            }
            var filter = enemyFilters.get(tint)
            if (filter == null) {
                filter = PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY)
                enemyFilters.put(tint, filter)
            }
            enemyPaint.colorFilter = filter
            canvas.drawBitmap(
                enemySprites[tank.dir.ordinal][tank.trackFrame],
                x,
                y,
                enemyPaint,
            )
        }
    }

    private fun drawBullet(canvas: Canvas, bullet: Bullet) {
        canvas.drawBitmap(
            bulletSprite,
            (bullet.x / Fixed.SUBPIXELS).toFloat(),
            (bullet.y / Fixed.SUBPIXELS).toFloat(),
            null,
        )
    }

    private fun drawFieldBorder(canvas: Canvas) {
        paint.color = BORDER_GREY
        canvas.drawRect(207f, 0f, 208f, 208f, paint)
        canvas.drawRect(0f, 207f, 208f, 208f, paint)
    }

    private fun hud(slot: Int, value: Int, width: Int = 0): String {
        if (hudValues[slot] != value || hudWidths[slot] != width) {
            hudValues[slot] = value
            hudWidths[slot] = width
            hudStrings[slot] = if (width > 0) {
                value.toString().padStart(width, '0')
            } else {
                value.toString()
            }
        }
        return hudStrings[slot] ?: value.toString()
    }

    private fun drawPanel(canvas: Canvas) {
        paint.color = PANEL_GREY
        canvas.drawRect(208f, 0f, 256f, 224f, paint)
        paint.color = Color.BLACK
        canvas.drawRect(208f, 0f, 209f, 224f, paint)

        font.draw(canvas, "STEEL", 212, 8)
        font.draw(canvas, "EAGLE", 212, 20)

        if (world.playerCount == 1) {
            canvas.drawBitmap(hudEnemy, 212f, 36f, null)
            font.draw(canvas, hud(HUD_ENEMIES, world.enemiesRemaining, 2), 224, 36)

            font.draw(canvas, "IP", 212, 52)
            font.draw(canvas, hud(HUD_LIVES_P1, world.lives, 2), 228, 52)

            canvas.drawBitmap(hudFlag, 212f, 68f, null)
            font.draw(canvas, hud(HUD_LEVEL, world.levelNumber), 224, 68)

            font.draw(canvas, "SCORE", 212, 88)
            font.draw(canvas, hud(HUD_SCORE_P1, world.score, 6), SCORE_X, 100)
            return
        }

        canvas.drawBitmap(hudEnemy, 212f, 34f, null)
        font.draw(canvas, hud(HUD_ENEMIES, world.enemiesRemaining, 2), 224, 34)

        font.draw(canvas, "P1", 212, 48)
        font.draw(canvas, hud(HUD_LIVES_P1, world.livesOf(0), 2), 228, 48)
        font.draw(canvas, "P2", 212, 60, p2TextPaint)
        font.draw(
            canvas,
            hud(HUD_LIVES_P2, world.livesOf(1), 2),
            228,
            60,
            p2TextPaint,
        )

        canvas.drawBitmap(hudFlag, 212f, 74f, null)
        font.draw(canvas, hud(HUD_LEVEL, world.levelNumber), 224, 74)

        font.draw(canvas, "P1", 212, 90)
        font.draw(canvas, hud(HUD_SCORE_P1, world.scoreOf(0), 6), SCORE_X, 102)
        font.draw(canvas, "P2", 212, 116, p2TextPaint)
        font.draw(
            canvas,
            hud(HUD_SCORE_P2, world.scoreOf(1), 6),
            SCORE_X,
            128,
            p2TextPaint,
        )
    }

    private data class Explosion(
        val x: Int,
        val y: Int,
        val big: Boolean,
        val totalTicks: Int,
        var ticksRemaining: Int,
    )

    private companion object {
        const val SCORE_X = 210
        const val HUD_ENEMIES = 0
        const val HUD_LIVES_P1 = 1
        const val HUD_LIVES_P2 = 2
        const val HUD_SCORE_P1 = 3
        const val HUD_SCORE_P2 = 4
        const val HUD_LEVEL = 5
        const val HUD_SLOTS = 6

        const val PANEL_GREY = 0xFF7C7C7C.toInt()
        const val BORDER_GREY = 0xFF3C3C3C.toInt()
        const val P2_GREEN = 0xFF68D868.toInt()
        const val BLOCK_PIXELS = 16
        const val SMALL_EXPLOSION_TICKS = 24
        const val SMALL_FRAME_TICKS = 8
        const val LARGE_EXPLOSION_TICKS = 24
        const val LARGE_FRAME_TICKS = 12
    }
}
