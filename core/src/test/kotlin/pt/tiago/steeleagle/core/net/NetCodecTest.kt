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
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetCodecTest {

    @Test
    fun `handshake and control messages round trip`() {
        assertRoundTrip(NetMessage.Hello(NetProtocol.VERSION, "TIAGO"))
        assertRoundTrip(
            NetMessage.Welcome(
                version = NetProtocol.VERSION,
                seed = 7,
                level = 12,
                difficulty = Difficulty.HARD.ordinal,
                players = 2,
            ),
        )
        assertRoundTrip(NetMessage.Input(42, Dir.LEFT, fire = true))
        assertRoundTrip(NetMessage.Input(43, null, fire = false))
        assertRoundTrip(NetMessage.Pause(true))
        assertRoundTrip(NetMessage.Pause(false))
        assertRoundTrip(NetMessage.Bye("HOST LEFT"))
        assertRoundTrip(NetMessage.Ack(99))
    }

    @Test
    fun `every direction and player input round trip`() {
        for (dir in Dir.entries) {
            val decoded = NetCodec.decode(NetCodec.encode(NetMessage.Input(1, dir, true)))
            assertEquals(NetMessage.Input(1, dir, true), decoded)
        }
        val none = NetCodec.decode(NetCodec.encode(NetMessage.Input(2, null, false)))
        assertEquals(NetMessage.Input(2, null, false), none)
    }

    @Test
    fun `a snapshot with a full terrain round trips`() {
        val snapshot = sampleSnapshot()
        val terrain = ByteArray(NetProtocol.TERRAIN_CELLS) { (it % 6).toByte() }
        val message = NetMessage.Snapshot(3, 12, snapshot, NetProtocol.TerrainState(terrain, emptyList()))

        val decoded = NetCodec.decode(NetCodec.encode(message)) as NetMessage.Snapshot

        assertEquals(3, decoded.seq)
        assertEquals(12, decoded.ackInput)
        assertEquals(snapshot, decoded.state)
        assertContentEquals(terrain, decoded.terrain.full)
        assertTrue(decoded.terrain.changes.isEmpty())
    }

    @Test
    fun `terrain deltas round trip`() {
        val changes = listOf(
            NetProtocol.TerrainChange(0, Terrain.BRICK),
            NetProtocol.TerrainChange(2703, Terrain.ICE),
            NetProtocol.TerrainChange(1351, Terrain.EMPTY),
        )
        val message = NetMessage.Snapshot(
            4,
            0,
            sampleSnapshot(),
            NetProtocol.TerrainState(null, changes),
        )

        val decoded = NetCodec.decode(NetCodec.encode(message)) as NetMessage.Snapshot

        assertEquals(changes, decoded.terrain.changes)
        assertNull(decoded.terrain.full)
    }

    @Test
    fun `all game events round trip`() {
        val events = listOf(
            GameEvent.ShotFired(10, 20, true),
            GameEvent.ShotFired(30, 40, false),
            GameEvent.SteelHit(1, 2),
            GameEvent.BrickBroken(3, 4),
            GameEvent.EnemyExploded(5, 6),
            GameEvent.PlayerExploded(7, 8),
            GameEvent.EagleDestroyed(96, 192),
            GameEvent.PowerUpAppeared(9, 10, PowerUpType.STAR),
            GameEvent.PowerUpPicked(11, 12, PowerUpType.GRENADE),
            GameEvent.ExtraLife,
            GameEvent.LevelClear,
            GameEvent.GameOver,
        )

        for (event in events) {
            val decoded = NetCodec.decode(NetCodec.encode(NetMessage.Event(event)))
            assertEquals(NetMessage.Event(event), decoded)
        }
    }

    @Test
    fun `truncated and unknown messages are rejected`() {
        assertFailsWith<IOException> { NetCodec.decode(byteArrayOf()) }
        assertFailsWith<IOException> { NetCodec.decode(byteArrayOf(99)) }
        val full = NetCodec.encode(NetMessage.Hello(1, "ABC"))
        assertFailsWith<IOException> { NetCodec.decode(full.copyOf(full.size - 2)) }
    }

    @Test
    fun `long names are cut to the protocol limit`() {
        val decoded = NetCodec.decode(
            NetCodec.encode(NetMessage.Hello(1, "A".repeat(80))),
        ) as NetMessage.Hello

        assertEquals(24, decoded.name.length)
    }

    @Test
    fun `wire enum ordinals stay stable`() {
        assertEquals(
            listOf(Dir.UP, Dir.RIGHT, Dir.DOWN, Dir.LEFT),
            Dir.entries,
        )
        assertEquals(
            listOf(WorldState.PLAYING, WorldState.LEVEL_CLEAR, WorldState.GAME_OVER),
            WorldState.entries,
        )
        assertEquals(
            listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD),
            Difficulty.entries,
        )
        assertEquals(
            listOf(EnemyType.BASIC, EnemyType.FAST, EnemyType.POWER, EnemyType.ARMORED),
            EnemyType.entries,
        )
        assertEquals(
            listOf(
                PowerUpType.STAR,
                PowerUpType.GRENADE,
                PowerUpType.HELMET,
                PowerUpType.SHOVEL,
                PowerUpType.CLOCK,
                PowerUpType.TANK,
                PowerUpType.BOAT,
                PowerUpType.GUN,
            ),
            PowerUpType.entries,
        )
        assertEquals(
            listOf(
                Terrain.EMPTY,
                Terrain.BRICK,
                Terrain.STEEL,
                Terrain.WATER,
                Terrain.TREES,
                Terrain.ICE,
            ),
            Terrain.entries,
        )
    }

    private fun assertRoundTrip(message: NetMessage) {
        assertEquals(message, NetCodec.decode(NetCodec.encode(message)))
    }

    private fun sampleSnapshot() = WorldSnapshot(
        tick = 1234L,
        state = WorldState.PLAYING,
        levelNumber = 7,
        difficulty = Difficulty.NORMAL,
        playerCount = 2,
        lives = listOf(3, 2),
        scores = listOf(12_300, 4_500),
        enemiesRemaining = 9,
        totalEnemies = 20,
        eagleAlive = true,
        players = listOf(
            tank(Dir.UP, alive = true, shield = 120, type = null, hp = 1),
            tank(Dir.LEFT, alive = false, shield = 0, type = null, hp = 1),
        ),
        enemies = listOf(
            tank(Dir.DOWN, alive = true, type = EnemyType.BASIC, hp = 1, flashing = true),
            tank(Dir.RIGHT, alive = true, type = EnemyType.ARMORED, hp = 3),
        ),
        bullets = listOf(
            BulletState(100, 200, Dir.UP, ownerPlayer = 0, ownerEnemy = null, 1, 192),
            BulletState(300, 400, Dir.DOWN, ownerPlayer = null, ownerEnemy = 1, 3, 128),
        ),
        powerUp = PowerUpState(PowerUpType.STAR, 96, 48, 700),
        freezeTicks = 30,
        shovelTicks = 0,
    )

    private fun tank(
        dir: Dir,
        alive: Boolean,
        type: EnemyType?,
        hp: Int,
        shield: Int = 0,
        flashing: Boolean = false,
    ) = TankState(
        x = 64 * 16,
        y = 192 * 16,
        dir = dir,
        trackFrame = 1,
        weaponLevel = 2,
        alive = alive,
        shieldTicks = shield,
        stunTicks = 0,
        hasBoat = false,
        escapingWater = false,
        slideRemaining = 0,
        wasMoving = true,
        bulletSpeed = 192,
        type = type,
        hp = hp,
        flashing = flashing,
    )
}
