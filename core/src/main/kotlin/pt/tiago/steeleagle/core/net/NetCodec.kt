package pt.tiago.steeleagle.core.net

import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import pt.tiago.steeleagle.core.world.BulletState
import pt.tiago.steeleagle.core.world.PowerUpState
import pt.tiago.steeleagle.core.world.TankState
import pt.tiago.steeleagle.core.world.Terrain
import pt.tiago.steeleagle.core.world.WorldSnapshot
import pt.tiago.steeleagle.core.world.WorldState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Compact binary codec for [NetMessage]. Every message is self-delimiting:
 * one type byte followed by fixed fields, so the sockets can read directly
 * from a [DataInputStream] without extra framing.
 */
object NetCodec {

    fun encode(message: NetMessage): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { write(it, message) }
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): NetMessage =
        DataInputStream(ByteArrayInputStream(bytes)).use { read(it) }

    fun write(out: DataOutputStream, message: NetMessage) {
        when (message) {
            is NetMessage.Hello -> {
                out.writeByte(TYPE_HELLO)
                out.writeByte(message.version)
                out.writeUTF(message.name.take(MAX_NAME))
            }

            is NetMessage.Welcome -> {
                out.writeByte(TYPE_WELCOME)
                out.writeByte(message.version)
                out.writeInt(message.seed)
                out.writeInt(message.level)
                out.writeByte(message.difficulty)
                out.writeByte(message.players)
            }

            is NetMessage.Input -> {
                out.writeByte(TYPE_INPUT)
                out.writeInt(message.seq)
                out.writeByte(message.direction?.ordinal ?: NetProtocol.PLAYER_NONE)
                out.writeBoolean(message.fire)
            }

            is NetMessage.Snapshot -> {
                out.writeByte(TYPE_SNAPSHOT)
                out.writeInt(message.seq)
                out.writeInt(message.ackInput)
                writeWorld(out, message.state)
                writeTerrain(out, message.terrain)
            }

            is NetMessage.Event -> {
                out.writeByte(TYPE_EVENT)
                writeEvent(out, message.event)
            }

            is NetMessage.Pause -> {
                out.writeByte(TYPE_PAUSE)
                out.writeBoolean(message.paused)
            }

            is NetMessage.Bye -> {
                out.writeByte(TYPE_BYE)
                out.writeUTF(message.reason.take(MAX_REASON))
            }

            is NetMessage.Ack -> {
                out.writeByte(TYPE_ACK)
                out.writeInt(message.snapshotSeq)
            }
        }
        out.flush()
    }

    fun read(input: DataInputStream): NetMessage {
        return when (val type = input.readUnsignedByte()) {
            TYPE_HELLO -> NetMessage.Hello(input.readUnsignedByte(), input.readUTF())
            TYPE_WELCOME -> NetMessage.Welcome(
                version = input.readUnsignedByte(),
                seed = input.readInt(),
                level = input.readInt(),
                difficulty = input.readUnsignedByte(),
                players = input.readUnsignedByte(),
            )

            TYPE_INPUT -> {
                val seq = input.readInt()
                val dir = input.readUnsignedByte()
                NetMessage.Input(
                    seq = seq,
                    direction = if (dir == NetProtocol.PLAYER_NONE) null else direction(dir),
                    fire = input.readBoolean(),
                )
            }

            TYPE_SNAPSHOT -> NetMessage.Snapshot(
                seq = input.readInt(),
                ackInput = input.readInt(),
                state = readWorld(input),
                terrain = readTerrain(input),
            )

            TYPE_EVENT -> NetMessage.Event(readEvent(input))
            TYPE_PAUSE -> NetMessage.Pause(input.readBoolean())
            TYPE_BYE -> NetMessage.Bye(input.readUTF())
            TYPE_ACK -> NetMessage.Ack(input.readInt())
            else -> throw IOException("unknown message type $type")
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun writeWorld(out: DataOutputStream, state: WorldSnapshot) {
        out.writeLong(state.tick)
        out.writeByte(state.state.ordinal)
        out.writeInt(state.levelNumber)
        out.writeByte(state.difficulty.ordinal)
        out.writeByte(state.playerCount)
        for (index in 0 until state.playerCount) {
            out.writeByte(state.lives[index])
            out.writeInt(state.scores[index])
        }
        out.writeShort(state.enemiesRemaining)
        out.writeShort(state.totalEnemies)
        out.writeBoolean(state.eagleAlive)

        out.writeByte(state.players.size)
        state.players.forEach { writeTank(out, it) }

        out.writeByte(state.enemies.size)
        state.enemies.forEach { writeTank(out, it) }

        out.writeByte(state.bullets.size)
        state.bullets.forEach { bullet ->
            out.writeShort(bullet.x)
            out.writeShort(bullet.y)
            out.writeByte(bullet.dir.ordinal)
            out.writeByte(
                when {
                    bullet.ownerPlayer != null -> bullet.ownerPlayer
                    bullet.ownerEnemy != null -> 2 + bullet.ownerEnemy
                    else -> NetProtocol.PLAYER_NONE
                },
            )
            out.writeByte(bullet.weaponLevel)
            out.writeShort(bullet.speed)
        }

        val powerUp = state.powerUp
        out.writeBoolean(powerUp != null)
        if (powerUp != null) {
            out.writeByte(powerUp.type.ordinal)
            out.writeShort(powerUp.x)
            out.writeShort(powerUp.y)
            out.writeShort(powerUp.ticksRemaining)
        }
        out.writeShort(state.freezeTicks)
        out.writeShort(state.shovelTicks)
    }

    private fun readWorld(input: DataInputStream): WorldSnapshot {
        val tick = input.readLong()
        val state = worldState(input.readUnsignedByte())
        val level = input.readInt()
        val difficulty = difficulty(input.readUnsignedByte())
        val playerCount = input.readUnsignedByte()
        if (playerCount !in 1..2) throw IOException("bad player count $playerCount")
        val lives = mutableListOf<Int>()
        val scores = mutableListOf<Int>()
        repeat(playerCount) {
            lives += input.readUnsignedByte()
            scores += input.readInt()
        }
        val remaining = input.readUnsignedShort()
        val total = input.readUnsignedShort()
        val eagleAlive = input.readBoolean()

        val playerStates = input.readUnsignedByte()
        if (playerStates != playerCount) throw IOException("bad player state count")
        val players = List(playerStates) { readTank(input) }
        val enemyCount = input.readUnsignedByte()
        if (enemyCount > MAX_TANKS) throw IOException("too many enemies")
        val enemies = List(enemyCount) { readTank(input) }
        val bulletCount = input.readUnsignedByte()
        if (bulletCount > MAX_BULLETS) throw IOException("too many bullets")
        val bullets = List(bulletCount) { readBullet(input) }
        val powerUp = if (input.readBoolean()) {
            PowerUpState(
                type = powerUpType(input.readUnsignedByte()),
                x = input.readUnsignedShort(),
                y = input.readUnsignedShort(),
                ticksRemaining = input.readUnsignedShort(),
            )
        } else {
            null
        }
        val freeze = input.readUnsignedShort()
        val shovel = input.readUnsignedShort()

        return WorldSnapshot(
            tick = tick,
            state = state,
            levelNumber = level,
            difficulty = difficulty,
            playerCount = playerCount,
            lives = lives,
            scores = scores,
            enemiesRemaining = remaining,
            totalEnemies = total,
            eagleAlive = eagleAlive,
            players = players,
            enemies = enemies,
            bullets = bullets,
            powerUp = powerUp,
            freezeTicks = freeze,
            shovelTicks = shovel,
        )
    }

    private fun writeTank(out: DataOutputStream, tank: TankState) {
        out.writeShort(tank.x)
        out.writeShort(tank.y)
        out.writeByte(tank.dir.ordinal)
        out.writeByte(tank.trackFrame)
        out.writeByte(tank.weaponLevel)
        out.writeBoolean(tank.alive)
        out.writeShort(tank.shieldTicks)
        out.writeShort(tank.stunTicks)
        out.writeBoolean(tank.hasBoat)
        out.writeBoolean(tank.escapingWater)
        out.writeShort(tank.slideRemaining)
        out.writeBoolean(tank.wasMoving)
        out.writeShort(tank.bulletSpeed)
        out.writeByte(tank.type?.ordinal ?: NetProtocol.PLAYER_NONE)
        out.writeByte(tank.hp)
        out.writeBoolean(tank.flashing)
    }

    private fun readTank(input: DataInputStream): TankState {
        val x = input.readUnsignedShort()
        val y = input.readUnsignedShort()
        val dir = direction(input.readUnsignedByte())
        val trackFrame = input.readUnsignedByte()
        val weaponLevel = input.readUnsignedByte()
        val alive = input.readBoolean()
        val shield = input.readUnsignedShort()
        val stun = input.readUnsignedShort()
        val boat = input.readBoolean()
        val escaping = input.readBoolean()
        val slide = input.readUnsignedShort()
        val moving = input.readBoolean()
        val bulletSpeed = input.readUnsignedShort()
        val typeOrdinal = input.readUnsignedByte()
        val type = if (typeOrdinal == NetProtocol.PLAYER_NONE) {
            null
        } else {
            EnemyType.entries[typeOrdinal]
        }
        val hp = input.readUnsignedByte()
        val flashing = input.readBoolean()
        return TankState(
            x = x,
            y = y,
            dir = dir,
            trackFrame = trackFrame,
            weaponLevel = weaponLevel,
            alive = alive,
            shieldTicks = shield,
            stunTicks = stun,
            hasBoat = boat,
            escapingWater = escaping,
            slideRemaining = slide,
            wasMoving = moving,
            bulletSpeed = bulletSpeed,
            type = type,
            hp = hp,
            flashing = flashing,
        )
    }

    private fun readBullet(input: DataInputStream): BulletState {
        val x = input.readUnsignedShort()
        val y = input.readUnsignedShort()
        val dir = direction(input.readUnsignedByte())
        val owner = input.readUnsignedByte()
        val weaponLevel = input.readUnsignedByte()
        val speed = input.readUnsignedShort()
        return BulletState(
            x = x,
            y = y,
            dir = dir,
            ownerPlayer = if (owner < 2) owner else null,
            ownerEnemy = if (owner >= 2 && owner != NetProtocol.PLAYER_NONE) owner - 2 else null,
            weaponLevel = weaponLevel,
            speed = speed,
        )
    }

    private fun writeTerrain(out: DataOutputStream, terrain: NetProtocol.TerrainState) {
        val full = terrain.full
        out.writeBoolean(full != null)
        if (full != null) {
            require(full.size == NetProtocol.TERRAIN_CELLS) {
                "full terrain must have ${NetProtocol.TERRAIN_CELLS} cells"
            }
            out.write(full)
        } else {
            out.writeShort(terrain.changes.size)
            terrain.changes.forEach { change ->
                out.writeShort(change.index)
                out.writeByte(change.terrain.ordinal)
            }
        }
    }

    private fun readTerrain(input: DataInputStream): NetProtocol.TerrainState {
        val full = if (input.readBoolean()) {
            ByteArray(NetProtocol.TERRAIN_CELLS).also { input.readFully(it) }
        } else {
            null
        }
        val count = if (full == null) input.readUnsignedShort() else 0
        val changes = List(count) {
            NetProtocol.TerrainChange(
                index = input.readUnsignedShort(),
                terrain = Terrain.entries[input.readUnsignedByte()],
            )
        }
        return NetProtocol.TerrainState(full, changes)
    }

    private fun writeEvent(out: DataOutputStream, event: GameEvent) {
        when (event) {
            is GameEvent.ShotFired -> {
                out.writeByte(EVENT_SHOT)
                out.writeShort(event.x)
                out.writeShort(event.y)
                out.writeBoolean(event.isPlayer)
            }

            is GameEvent.SteelHit -> {
                out.writeByte(EVENT_STEEL)
                out.writeShort(event.x)
                out.writeShort(event.y)
            }

            is GameEvent.BrickBroken -> {
                out.writeByte(EVENT_BRICK)
                out.writeShort(event.x)
                out.writeShort(event.y)
            }

            is GameEvent.EnemyExploded -> {
                out.writeByte(EVENT_ENEMY)
                out.writeShort(event.x)
                out.writeShort(event.y)
            }

            is GameEvent.PlayerExploded -> {
                out.writeByte(EVENT_PLAYER)
                out.writeShort(event.x)
                out.writeShort(event.y)
            }

            is GameEvent.EagleDestroyed -> {
                out.writeByte(EVENT_EAGLE)
                out.writeShort(event.x)
                out.writeShort(event.y)
            }

            is GameEvent.PowerUpAppeared -> {
                out.writeByte(EVENT_POWER_APPEARED)
                out.writeShort(event.x)
                out.writeShort(event.y)
                out.writeByte(event.type.ordinal)
            }

            is GameEvent.PowerUpPicked -> {
                out.writeByte(EVENT_POWER_PICKED)
                out.writeShort(event.x)
                out.writeShort(event.y)
                out.writeByte(event.type.ordinal)
            }

            GameEvent.ExtraLife -> out.writeByte(EVENT_EXTRA_LIFE)
            GameEvent.LevelClear -> out.writeByte(EVENT_LEVEL_CLEAR)
            GameEvent.GameOver -> out.writeByte(EVENT_GAME_OVER)
        }
    }

    private fun readEvent(input: DataInputStream): GameEvent =
        when (val type = input.readUnsignedByte()) {
            EVENT_SHOT -> GameEvent.ShotFired(
                x = input.readUnsignedShort(),
                y = input.readUnsignedShort(),
                isPlayer = input.readBoolean(),
            )

            EVENT_STEEL -> GameEvent.SteelHit(input.readUnsignedShort(), input.readUnsignedShort())
            EVENT_BRICK -> GameEvent.BrickBroken(input.readUnsignedShort(), input.readUnsignedShort())
            EVENT_ENEMY -> GameEvent.EnemyExploded(input.readUnsignedShort(), input.readUnsignedShort())
            EVENT_PLAYER -> GameEvent.PlayerExploded(input.readUnsignedShort(), input.readUnsignedShort())
            EVENT_EAGLE -> GameEvent.EagleDestroyed(input.readUnsignedShort(), input.readUnsignedShort())
            EVENT_POWER_APPEARED -> GameEvent.PowerUpAppeared(
                x = input.readUnsignedShort(),
                y = input.readUnsignedShort(),
                type = powerUpType(input.readUnsignedByte()),
            )

            EVENT_POWER_PICKED -> GameEvent.PowerUpPicked(
                x = input.readUnsignedShort(),
                y = input.readUnsignedShort(),
                type = powerUpType(input.readUnsignedByte()),
            )

            EVENT_EXTRA_LIFE -> GameEvent.ExtraLife
            EVENT_LEVEL_CLEAR -> GameEvent.LevelClear
            EVENT_GAME_OVER -> GameEvent.GameOver
            else -> throw IOException("unknown event type $type")
        }

    private fun direction(ordinal: Int): Dir =
        Dir.entries.getOrNull(ordinal) ?: throw IOException("bad direction $ordinal")

    private fun worldState(ordinal: Int): WorldState =
        WorldState.entries.getOrNull(ordinal) ?: throw IOException("bad world state $ordinal")

    private fun difficulty(ordinal: Int): Difficulty =
        Difficulty.entries.getOrNull(ordinal) ?: throw IOException("bad difficulty $ordinal")

    private fun powerUpType(ordinal: Int): PowerUpType =
        PowerUpType.entries.getOrNull(ordinal) ?: throw IOException("bad power-up $ordinal")

    private const val MAX_TANKS = 64
    private const val MAX_BULLETS = 64
    private const val MAX_NAME = 24
    private const val MAX_REASON = 40

    private const val TYPE_HELLO = 1
    private const val TYPE_WELCOME = 2
    private const val TYPE_INPUT = 3
    private const val TYPE_SNAPSHOT = 4
    private const val TYPE_EVENT = 5
    private const val TYPE_PAUSE = 6
    private const val TYPE_BYE = 7
    private const val TYPE_ACK = 8

    private const val EVENT_SHOT = 0
    private const val EVENT_STEEL = 1
    private const val EVENT_BRICK = 2
    private const val EVENT_ENEMY = 3
    private const val EVENT_PLAYER = 4
    private const val EVENT_EAGLE = 5
    private const val EVENT_POWER_APPEARED = 6
    private const val EVENT_POWER_PICKED = 7
    private const val EVENT_EXTRA_LIFE = 8
    private const val EVENT_LEVEL_CLEAR = 9
    private const val EVENT_GAME_OVER = 10
}
