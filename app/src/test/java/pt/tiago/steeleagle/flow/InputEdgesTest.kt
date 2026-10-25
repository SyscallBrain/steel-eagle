package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputEdgesTest {

    @Test
    fun `direction only fires on a change`() {
        val edges = InputEdges()

        assertEquals(Dir.DOWN, edges.update(PlayerInput(Dir.DOWN, false)).direction)
        assertEquals(null, edges.update(PlayerInput(Dir.DOWN, false)).direction)
        assertEquals(null, edges.update(PlayerInput(null, false)).direction)
        assertEquals(Dir.DOWN, edges.update(PlayerInput(Dir.DOWN, false)).direction)
    }

    @Test
    fun `fire fires once until released`() {
        val edges = InputEdges()

        assertTrue(edges.update(PlayerInput(null, true)).fire)
        assertFalse(edges.update(PlayerInput(null, true)).fire)
        assertFalse(edges.update(PlayerInput(null, false)).fire)
        assertTrue(edges.update(PlayerInput(null, true)).fire)
    }

    @Test
    fun `lock fire swallows a held press until release`() {
        val edges = InputEdges()
        assertTrue(edges.update(PlayerInput(null, true)).fire)

        edges.lockFire()
        assertFalse(edges.update(PlayerInput(null, true)).fire)
        assertFalse(edges.update(PlayerInput(null, false)).fire)
        assertTrue(edges.update(PlayerInput(null, true)).fire)
    }
}
