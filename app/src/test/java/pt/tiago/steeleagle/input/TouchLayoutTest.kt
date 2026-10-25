package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.render.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchLayoutTest {

    @Test
    fun `puts the controls in the side margins on a wide phone`() {
        val viewport = Viewport.fit(2400, 1080)
        val layout = TouchLayout.compute(2400, 1080, viewport, density = 2.75f)

        assertFalse(layout.overlay)
        assertTrue(layout.dpad.centerX + layout.dpad.radius <= viewport.left)
        assertTrue(layout.fire.centerX - layout.fire.radius >= viewport.left + viewport.width)
        assertEquals(540, layout.dpad.centerY)
        assertEquals(540, layout.fire.centerY)
        assertTrue(layout.dpad.radius > layout.fire.radius)
    }

    @Test
    fun `fits the controls in the margins of a 4 by 3 tablet`() {
        val viewport = Viewport.fit(2048, 1536)
        val layout = TouchLayout.compute(2048, 1536, viewport, density = 2f)

        assertFalse(layout.overlay)
        assertTrue(layout.dpad.centerX - layout.dpad.radius >= 0)
        assertTrue(layout.dpad.centerX + layout.dpad.radius <= viewport.left)
        assertTrue(layout.fire.centerX - layout.fire.radius >= viewport.left + viewport.width)
        assertTrue(layout.fire.centerX + layout.fire.radius <= 2048)
    }

    @Test
    fun `overlays the game when the margins are too narrow`() {
        val viewport = Viewport.fit(800, 480)
        val layout = TouchLayout.compute(800, 480, viewport, density = 1.5f)

        assertTrue(layout.overlay)
        assertTrue(layout.dpad.centerX - layout.dpad.radius >= viewport.left)
        assertTrue(layout.dpad.centerX + layout.dpad.radius <= viewport.left + viewport.width)
        assertTrue(layout.fire.centerX + layout.fire.radius <= viewport.left + viewport.width)
        assertTrue(layout.dpad.centerX < layout.fire.centerX)
        assertTrue(layout.dpad.centerY + layout.dpad.radius <= viewport.top + viewport.height)
        assertTrue(layout.fire.centerY + layout.fire.radius <= viewport.top + viewport.height)
    }

    @Test
    fun `size and opacity are carried into the layout`() {
        val viewport = Viewport.fit(2400, 1080)
        val small = TouchLayout.compute(
            2400, 1080, viewport, density = 2.75f, sizeStep = 0, opacity = 40,
        )
        val large = TouchLayout.compute(
            2400, 1080, viewport, density = 2.75f, sizeStep = 2, opacity = 100,
        )

        assertTrue(large.dpad.radius > small.dpad.radius)
        assertTrue(large.fire.radius > small.fire.radius)
        assertEquals(40, small.opacity)
        assertEquals(100, large.opacity)
        assertEquals(0, small.sizeStep)
        assertEquals(2, large.sizeStep)
    }

    @Test
    fun `fire left mirrors the fixed controls`() {
        val viewport = Viewport.fit(2400, 1080)
        val right = TouchLayout.compute(2400, 1080, viewport, density = 2.75f)
        val left = TouchLayout.compute(
            2400, 1080, viewport, density = 2.75f, fireLeft = true,
        )

        assertTrue(right.dpad.centerX < right.fire.centerX)
        assertTrue(left.fire.centerX < left.dpad.centerX)
        assertEquals(2400 - right.dpad.centerX, left.dpad.centerX)
        assertEquals(2400 - right.fire.centerX, left.fire.centerX)
    }

    @Test
    fun `joystick zone stays opposite the fire button`() {
        val viewport = Viewport.fit(2400, 1080)
        val right = TouchLayout.compute(
            2400, 1080, viewport, density = 2.75f, joystick = true,
        )
        val left = TouchLayout.compute(
            2400, 1080, viewport, density = 2.75f, joystick = true, fireLeft = true,
        )

        assertFalse(right.overlay)
        assertTrue(right.joystickZone.contains(right.dpad.centerX, right.dpad.centerY))
        assertTrue(right.joystickZone.right <= right.fire.centerX - right.fire.radius)
        assertFalse(right.joystickZone.contains(right.fire.centerX, right.fire.centerY))

        assertFalse(left.overlay)
        assertTrue(left.joystickZone.contains(left.dpad.centerX, left.dpad.centerY))
        assertTrue(left.joystickZone.left >= left.fire.centerX + left.fire.radius)
        assertTrue(left.fire.centerX < left.dpad.centerX)
    }

    @Test
    fun `joystick fits a tablet and a narrow overlay screen`() {
        val tabletViewport = Viewport.fit(2048, 1536)
        val tablet = TouchLayout.compute(
            2048, 1536, tabletViewport, density = 2f, joystick = true,
        )
        assertFalse(tablet.overlay)
        assertTrue(tablet.joystickZone.contains(tablet.dpad.centerX, tablet.dpad.centerY))
        assertTrue(tablet.fire.centerX > tablet.joystickZone.right)

        val narrowViewport = Viewport.fit(800, 480)
        val narrow = TouchLayout.compute(
            800, 480, narrowViewport, density = 1.5f, joystick = true,
        )
        assertTrue(narrow.overlay)
        assertTrue(narrow.joystickZone.contains(narrow.dpad.centerX, narrow.dpad.centerY))
        assertTrue(narrow.fire.centerX > narrow.joystickZone.right)
        assertTrue(narrow.fire.centerX + narrow.fire.radius <= 800)
    }

    @Test
    fun `large controls still fit the margins of phones and tablets`() {
        val cases = listOf(
            Triple(2400, 1080, 2.75f),
            Triple(1280, 720, 2f),
            Triple(2048, 1536, 2f),
        )
        for ((width, height, density) in cases) {
            val viewport = Viewport.fit(width, height)
            for (joystick in listOf(false, true)) {
                for (fireLeft in listOf(false, true)) {
                    val layout = TouchLayout.compute(
                        width, height, viewport,
                        density = density,
                        joystick = joystick,
                        sizeStep = 2,
                        opacity = 70,
                        fireLeft = fireLeft,
                    )

                    assertFalse("$width x $height joystick=$joystick left=$fireLeft", layout.overlay)
                    assertTrue(
                        "dpad in screen",
                        layout.dpad.centerX - layout.dpad.radius >= 0 &&
                            layout.dpad.centerX + layout.dpad.radius <= width &&
                            layout.dpad.centerY - layout.dpad.radius >= 0 &&
                            layout.dpad.centerY + layout.dpad.radius <= height,
                    )
                    assertTrue(
                        "fire in screen",
                        layout.fire.centerX - layout.fire.radius >= 0 &&
                            layout.fire.centerX + layout.fire.radius <= width &&
                            layout.fire.centerY - layout.fire.radius >= 0 &&
                            layout.fire.centerY + layout.fire.radius <= height,
                    )
                    if (fireLeft) {
                        assertTrue(layout.fire.centerX < layout.dpad.centerX)
                    } else {
                        assertTrue(layout.dpad.centerX < layout.fire.centerX)
                    }
                    if (joystick) {
                        assertTrue(
                            layout.joystickZone.contains(
                                layout.dpad.centerX,
                                layout.dpad.centerY,
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `overlay controls keep the translucent cap`() {
        val viewport = Viewport.fit(800, 480)
        val overlay = TouchLayout.compute(
            800, 480, viewport, density = 1.5f, opacity = 70,
        )
        val side = TouchLayout.compute(
            2400, 1080, Viewport.fit(2400, 1080), density = 2.75f, opacity = 70,
        )

        assertTrue(overlay.overlay)
        assertTrue(overlay.alpha <= 120)
        assertFalse(side.overlay)
        assertEquals(178, side.alpha)
    }

    @Test
    fun `compute clamps opacity and size`() {
        val viewport = Viewport.fit(2400, 1080)
        val low = TouchLayout.compute(
            2400, 1080, viewport, density = 2.75f, sizeStep = 9, opacity = 5,
        )
        val high = TouchLayout.compute(
            2400, 1080, viewport, density = 2.75f, sizeStep = 9, opacity = 500,
        )

        assertEquals(30, low.opacity)
        assertEquals(2, low.sizeStep)
        assertEquals(100, high.opacity)
        assertEquals(2, high.sizeStep)
    }

    @Test
    fun `left handed joystick works on a narrow overlay`() {
        val viewport = Viewport.fit(800, 480)
        val layout = TouchLayout.compute(
            800, 480, viewport,
            density = 1.5f,
            joystick = true,
            sizeStep = 2,
            fireLeft = true,
        )

        assertTrue(layout.overlay)
        assertTrue(layout.fire.centerX < layout.dpad.centerX)
        assertTrue(layout.fire.centerX + layout.fire.radius <= 800)
        assertTrue(layout.joystickZone.contains(layout.dpad.centerX, layout.dpad.centerY))
        assertTrue(layout.joystickZone.left >= layout.fire.centerX + layout.fire.radius)
        assertEquals(0, layout.joystickZone.top)
        assertEquals(480, layout.joystickZone.bottom)
    }

    @Test
    fun `two player controls stay in their own margins`() {
        val cases = listOf(
            Triple(2400, 1080, 2.75f),
            Triple(2048, 1536, 2f),
            Triple(2560, 1600, 2f),
        )
        for ((width, height, density) in cases) {
            val viewport = Viewport.fit(width, height)
            for (player in 0..1) {
                val layout = TouchLayout.computeTwoPlayer(
                    width, height, viewport,
                    density = density,
                    player = player,
                    sizeStep = 2,
                    opacity = 70,
                )

                assertFalse("two players $width x $height p$player", layout.overlay)
                assertControlsInsideScreen(layout, width, height)
                assertTrue(
                    "controls overlap",
                    controlDistance(layout) >=
                        layout.dpad.radius + layout.fire.radius,
                )
                if (player == 0) {
                    assertTrue(layout.dpad.centerX + layout.dpad.radius <= viewport.left)
                    assertTrue(layout.fire.centerX + layout.fire.radius <= viewport.left)
                } else {
                    assertTrue(
                        layout.dpad.centerX - layout.dpad.radius >=
                            viewport.left + viewport.width,
                    )
                    assertTrue(
                        layout.fire.centerX - layout.fire.radius >=
                            viewport.left + viewport.width,
                    )
                }
            }
        }
    }

    @Test
    fun `narrow two player controls fall back to the overlay`() {
        val width = 800
        val height = 480
        val viewport = Viewport.fit(width, height)
        for (player in 0..1) {
            val layout = TouchLayout.computeTwoPlayer(
                width, height, viewport,
                density = 1.5f,
                player = player,
                sizeStep = 2,
                opacity = 70,
            )

            assertTrue(layout.overlay)
            assertControlsInsideScreen(layout, width, height)
            assertTrue(
                "controls overlap",
                controlDistance(layout) >= layout.dpad.radius + layout.fire.radius,
            )
            assertTrue(layout.alpha <= 120)
            if (player == 0) {
                assertTrue(layout.dpad.centerX < width / 2)
                assertTrue(layout.fire.centerX < width / 2)
            } else {
                assertTrue(layout.fire.centerX > width / 2)
                assertTrue(layout.dpad.centerX > width / 2)
            }
        }
    }

    @Test
    fun `two player overlay fits a short split screen`() {
        val width = 1030
        val height = 224
        val viewport = Viewport.fit(width, height)

        for (player in 0..1) {
            val layout = TouchLayout.computeTwoPlayer(
                width, height, viewport,
                density = 4f,
                player = player,
                sizeStep = 2,
            )

            assertTrue(layout.overlay)
            assertControlsInsideScreen(layout, width, height)
            assertTrue(layout.dpad.centerY - layout.dpad.radius >= 0)
            assertTrue(layout.fire.centerY - layout.fire.radius >= 0)
        }
    }

    @Test
    fun `two player margin layout needs vertical room`() {
        val width = 2000
        val height = 300
        val viewport = Viewport.fit(width, height)

        val layout = TouchLayout.computeTwoPlayer(
            width, height, viewport,
            density = 3f,
            player = 0,
            sizeStep = 2,
        )

        assertTrue(layout.overlay)
        assertControlsInsideScreen(layout, width, height)
    }

    @Test
    fun `two player overlay stays inside a portrait viewport`() {
        val width = 1080
        val height = 2400
        val viewport = Viewport.fit(width, height)
        val layout = TouchLayout.computeTwoPlayer(
            width, height, viewport,
            density = 2f,
            player = 0,
            sizeStep = 2,
        )

        assertTrue(layout.overlay)
        assertControlsInsideScreen(layout, width, height)
        assertTrue(layout.dpad.centerY + layout.dpad.radius <= viewport.top + viewport.height)
        assertTrue(layout.fire.centerY + layout.fire.radius <= viewport.top + viewport.height)
    }

    private fun assertControlsInsideScreen(layout: TouchLayout, width: Int, height: Int) {
        assertTrue(
            "dpad in screen",
            layout.dpad.centerX - layout.dpad.radius >= 0 &&
                layout.dpad.centerX + layout.dpad.radius <= width &&
                layout.dpad.centerY - layout.dpad.radius >= 0 &&
                layout.dpad.centerY + layout.dpad.radius <= height,
        )
        assertTrue(
            "fire in screen",
            layout.fire.centerX - layout.fire.radius >= 0 &&
                layout.fire.centerX + layout.fire.radius <= width &&
                layout.fire.centerY - layout.fire.radius >= 0 &&
                layout.fire.centerY + layout.fire.radius <= height,
        )
    }

    private fun controlDistance(layout: TouchLayout): Double = kotlin.math.hypot(
        (layout.dpad.centerX - layout.fire.centerX).toDouble(),
        (layout.dpad.centerY - layout.fire.centerY).toDouble(),
    )

    @Test
    fun `two player halves are mirrored`() {
        val viewport = Viewport.fit(2400, 1080)
        val p1 = TouchLayout.computeTwoPlayer(
            2400, 1080, viewport, density = 2.75f, player = 0, sizeStep = 2,
        )
        val p2 = TouchLayout.computeTwoPlayer(
            2400, 1080, viewport, density = 2.75f, player = 1, sizeStep = 2,
        )

        assertEquals(2400 - p1.dpad.centerX, p2.dpad.centerX)
        assertEquals(2400 - p1.fire.centerX, p2.fire.centerX)
        assertEquals(p1.dpad.centerY, p2.dpad.centerY)
        assertEquals(p1.fire.centerY, p2.fire.centerY)
    }

    @Test
    fun `two player overlay fits a no-margin screen`() {
        val viewport = Viewport.fit(512, 448)
        val p1 = TouchLayout.computeTwoPlayer(
            512, 448, viewport, density = 1.5f, player = 0, sizeStep = 2,
        )
        val p2 = TouchLayout.computeTwoPlayer(
            512, 448, viewport, density = 1.5f, player = 1, sizeStep = 2,
        )

        assertTrue(p1.overlay)
        assertTrue(p2.overlay)
        assertTrue(p1.dpad.centerX < p1.fire.centerX)
        assertTrue(p2.fire.centerX < p2.dpad.centerX)
        assertTrue(p1.fire.centerX < p2.fire.centerX)
        assertTrue(p1.alpha <= 120)
    }

    @Test
    fun `hit tests control circles`() {
        val circle = ControlCircle(centerX = 100, centerY = 100, radius = 50)

        assertTrue(circle.contains(100, 100))
        assertTrue(circle.contains(150, 100))
        assertFalse(circle.contains(151, 100))
        assertFalse(circle.contains(100, 151))
    }
}
