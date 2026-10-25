package pt.tiago.steeleagle.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class GameSettingsTest {

    @Test
    fun `defaults are arcade friendly`() {
        val settings = GameSettings()

        assertEquals(TouchControl.DPAD, settings.touchControl)
        assertEquals(70, settings.touchOpacity)
        assertEquals(2, settings.touchSize)
        assertEquals(FireSide.RIGHT, settings.fireSide)
        assertEquals(5, settings.musicVolume)
        assertEquals(7, settings.sfxVolume)
        assertEquals(true, settings.vibration)
        assertEquals(false, settings.scanlines)
    }

    @Test
    fun `sanitized clamps volumes and sizes`() {
        val settings = GameSettings(
            touchOpacity = 5,
            touchSize = 9,
            musicVolume = -2,
            sfxVolume = 42,
        ).sanitized()

        assertEquals(30, settings.touchOpacity)
        assertEquals(2, settings.touchSize)
        assertEquals(0, settings.musicVolume)
        assertEquals(10, settings.sfxVolume)
    }
}
