package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.storage.FireSide
import pt.tiago.steeleagle.storage.GameSettings
import pt.tiago.steeleagle.storage.TouchControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsModelTest {

    @Test
    fun `cycles touch control in both directions`() {
        val settings = GameSettings(touchControl = TouchControl.DPAD)
        val model = OptionsModel(settings)

        assertTrue(model.change(1))
        assertEquals(TouchControl.JOYSTICK, settings.touchControl)
        assertTrue(model.change(1))
        assertEquals(TouchControl.NES, settings.touchControl)
        assertEquals("NES PAD", model.valueLabel())
        assertTrue(model.change(1))
        assertEquals(TouchControl.JOY_AB, settings.touchControl)
        assertEquals("JOY + AB", model.valueLabel())
        assertTrue(model.change(1))
        assertEquals(TouchControl.DPAD, settings.touchControl)
        assertTrue(model.change(-1))
        assertEquals(TouchControl.JOY_AB, settings.touchControl)
    }

    @Test
    fun `clamps opacity and volumes`() {
        val settings = GameSettings(touchOpacity = 40, musicVolume = 9, sfxVolume = 3)
        val model = OptionsModel(settings)

        model.select(OptionRow.OPACITY.ordinal)
        model.change(-1)
        assertEquals(30, settings.touchOpacity)
        model.change(-1)
        assertEquals(30, settings.touchOpacity)
        model.change(1)
        assertEquals(40, settings.touchOpacity)

        model.select(OptionRow.MUSIC.ordinal)
        model.change(1)
        assertEquals(10, settings.musicVolume)

        model.select(OptionRow.SFX.ordinal)
        model.change(-1)
        assertEquals(2, settings.sfxVolume)
    }

    @Test
    fun `cycles size and toggles switches`() {
        val settings = GameSettings(touchSize = 2)
        val model = OptionsModel(settings)

        model.select(OptionRow.SIZE.ordinal)
        model.change(1)
        assertEquals(0, settings.touchSize)

        model.select(OptionRow.FIRE_SIDE.ordinal)
        model.change(1)
        assertEquals(FireSide.LEFT, settings.fireSide)

        model.select(OptionRow.SCANLINES.ordinal)
        model.activate()
        assertTrue(settings.scanlines)

        model.select(OptionRow.VIBRATION.ordinal)
        model.activate()
        assertFalse(settings.vibration)
    }

    @Test
    fun `back row does not change settings and is not an activation`() {
        val model = OptionsModel(GameSettings())
        model.select(OptionRow.BACK.ordinal)

        assertFalse(model.activate())
        assertFalse(model.change(-1))
    }

    @Test
    fun `wrap the cursor around the rows`() {
        val model = OptionsModel(GameSettings())

        model.move(-1)
        assertEquals(OptionRow.BACK, model.row)
        model.move(1)
        assertEquals(OptionRow.TOUCH_CONTROL, model.row)
    }

    @Test
    fun `every row shows its own value whatever row is selected`() {
        val settings = GameSettings(
            touchControl = TouchControl.NES,
            touchOpacity = 70,
            touchSize = 2,
            fireSide = FireSide.LEFT,
            musicVolume = 3,
            sfxVolume = 9,
            scanlines = true,
            vibration = false,
        )
        val model = OptionsModel(settings)
        model.select(OptionRow.SIZE.ordinal)

        assertEquals("NES PAD", model.valueLabel(OptionRow.TOUCH_CONTROL))
        assertEquals("70 PCT", model.valueLabel(OptionRow.OPACITY))
        assertEquals("LARGE", model.valueLabel(OptionRow.SIZE))
        assertEquals("LEFT", model.valueLabel(OptionRow.FIRE_SIDE))
        assertEquals("3", model.valueLabel(OptionRow.MUSIC))
        assertEquals("9", model.valueLabel(OptionRow.SFX))
        assertEquals("ON", model.valueLabel(OptionRow.SCANLINES))
        assertEquals("OFF", model.valueLabel(OptionRow.VIBRATION))
    }
}
