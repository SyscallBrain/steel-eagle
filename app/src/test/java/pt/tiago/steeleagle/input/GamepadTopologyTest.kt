package pt.tiago.steeleagle.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadTopologyTest {

    @Test
    fun `classifies gamepad and joystick source masks`() {
        assertTrue(GamepadSources.isGamepad(GamepadSources.GAMEPAD))
        assertTrue(GamepadSources.isGamepad(GamepadSources.JOYSTICK))
        assertTrue(GamepadSources.isGamepad(GamepadSources.GAMEPAD or 0x1))
        assertFalse(GamepadSources.isGamepad(0))
        assertFalse(GamepadSources.isGamepad(0x1))
        assertFalse(GamepadSources.isGamepad(0x101)) // keyboard
        assertFalse(GamepadSources.isGamepad(0x201)) // D-pad remote
        assertTrue(GamepadSources.isGamepad(0x401 or 0x101)) // pad with keyboard
    }

    @Test
    fun `only gamepads enter the topology`() {
        val topology = GamepadTopology()

        assertFalse(topology.add(deviceId = 1, sources = 0x1))
        assertFalse(topology.contains(1))
        assertTrue(topology.add(deviceId = 2, sources = GamepadSources.GAMEPAD))
        assertTrue(topology.contains(2))
        assertEquals(1, topology.size)
    }

    @Test
    fun `known gamepads trigger once and removals only for known ids`() {
        val topology = GamepadTopology()
        assertTrue(topology.add(2, GamepadSources.JOYSTICK))
        assertFalse(topology.add(2, GamepadSources.JOYSTICK))

        assertFalse(topology.remove(3))
        assertTrue(topology.remove(2))
        assertFalse(topology.remove(2))
    }

    @Test
    fun `seeding adds existing devices without duplicates`() {
        val topology = GamepadTopology()
        topology.add(9, GamepadSources.GAMEPAD)

        topology.seed(
            mapOf(
                3 to GamepadSources.GAMEPAD,
                5 to GamepadSources.JOYSTICK,
                7 to 0x1,
            ),
        )

        assertTrue(topology.contains(3))
        assertTrue(topology.contains(5))
        assertTrue(topology.contains(9))
        assertFalse(topology.contains(7))
        assertFalse(topology.add(3, GamepadSources.GAMEPAD))
        assertEquals(3, topology.size)
    }

    @Test
    fun `pads are assigned by connection order`() {
        val topology = GamepadTopology()
        topology.add(30, GamepadSources.GAMEPAD)
        topology.add(10, GamepadSources.GAMEPAD)
        topology.add(20, GamepadSources.GAMEPAD)

        assertEquals(0, topology.playerFor(30))
        assertEquals(1, topology.playerFor(10))
        assertEquals(null, topology.playerFor(20))
        assertEquals(null, topology.playerFor(99))
    }

    @Test
    fun `removing the first pad promotes the second`() {
        val topology = GamepadTopology()
        topology.add(30, GamepadSources.GAMEPAD)
        topology.add(10, GamepadSources.GAMEPAD)

        topology.remove(30)

        assertEquals(0, topology.playerFor(10))
    }

    @Test
    fun `reset forgets previous devices`() {
        val topology = GamepadTopology()
        topology.add(3, GamepadSources.GAMEPAD)

        topology.reset()

        assertEquals(0, topology.size)
        assertTrue(topology.add(3, GamepadSources.GAMEPAD))
    }
}
