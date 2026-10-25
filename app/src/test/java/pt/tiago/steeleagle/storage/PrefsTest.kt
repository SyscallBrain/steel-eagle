package pt.tiago.steeleagle.storage

import android.content.SharedPreferences
import pt.tiago.steeleagle.core.rules.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {

    private fun prefs() = Prefs(FakeSharedPreferences())

    @Test
    fun `defaults match the arcade settings`() {
        val settings = prefs().loadSettings()

        assertEquals(TouchControl.DPAD, settings.touchControl)
        assertEquals(70, settings.touchOpacity)
        assertEquals(2, settings.touchSize)
        assertEquals(FireSide.RIGHT, settings.fireSide)
        assertEquals(5, settings.musicVolume)
        assertEquals(7, settings.sfxVolume)
        assertFalse(settings.scanlines)
        assertTrue(settings.vibration)
    }

    @Test
    fun `settings survive a save and load`() {
        val store = prefs()
        store.saveSettings(
            GameSettings(
                touchControl = TouchControl.JOYSTICK,
                touchOpacity = 45,
                touchSize = 0,
                fireSide = FireSide.LEFT,
                musicVolume = 2,
                sfxVolume = 9,
                scanlines = true,
                vibration = false,
            ),
        )

        val restored = store.loadSettings()

        assertEquals(TouchControl.JOYSTICK, restored.touchControl)
        assertEquals(45, restored.touchOpacity)
        assertEquals(0, restored.touchSize)
        assertEquals(FireSide.LEFT, restored.fireSide)
        assertEquals(2, restored.musicVolume)
        assertEquals(9, restored.sfxVolume)
        assertTrue(restored.scanlines)
        assertFalse(restored.vibration)
    }

    @Test
    fun `saved settings are sanitized`() {
        val store = prefs()
        store.saveSettings(GameSettings(touchOpacity = 5, musicVolume = 99))

        val restored = store.loadSettings()

        assertEquals(30, restored.touchOpacity)
        assertEquals(10, restored.musicVolume)
    }

    @Test
    fun `max unlocked is kept per difficulty`() {
        val store = prefs()

        assertEquals(1, store.maxUnlocked(Difficulty.EASY))
        store.unlockLevel(Difficulty.EASY, 4)
        store.unlockLevel(Difficulty.EASY, 2)
        store.unlockLevel(Difficulty.HARD, 2)

        assertEquals(4, store.maxUnlocked(Difficulty.EASY))
        assertEquals(2, store.maxUnlocked(Difficulty.HARD))
        assertEquals(1, store.maxUnlocked(Difficulty.NORMAL))
    }

    @Test
    fun `high scores survive a save and load`() {
        val store = prefs()
        val scores = HighScores()
        scores.add(Difficulty.NORMAL, "ABC", 1_200)
        scores.add(Difficulty.HARD, "XYZ", 4_000)

        store.saveHighScores(scores)
        val restored = store.loadHighScores()

        assertEquals(
            scores.entries(Difficulty.NORMAL).map { it.display to it.score },
            restored.entries(Difficulty.NORMAL).map { it.display to it.score },
        )
        assertEquals(
            scores.entries(Difficulty.HARD).map { it.display to it.score },
            restored.entries(Difficulty.HARD).map { it.display to it.score },
        )
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val backing = FakeSharedPreferences()
        backing.edit().putString("touch_control", "TRACKBALL").apply()

        val settings = Prefs(backing).loadSettings()

        assertEquals(TouchControl.DPAD, settings.touchControl)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String, defValue: String?): String? =
            if (values.containsKey(key)) values[key] as String? else defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? =
            if (values.containsKey(key)) values[key] as MutableSet<String>? else defValues

        override fun getInt(key: String, defValue: Int): Int =
            if (values.containsKey(key)) values[key] as Int else defValue

        override fun getLong(key: String, defValue: Long): Long =
            if (values.containsKey(key)) values[key] as Long else defValue

        override fun getFloat(key: String, defValue: Float): Float =
            if (values.containsKey(key)) values[key] as Float else defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            if (values.containsKey(key)) values[key] as Boolean else defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(values)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
    }

    private class FakeEditor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var cleared = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            pending[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removed += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            cleared = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (cleared) values.clear()
            values.putAll(pending)
            removed.forEach(values::remove)
            pending.clear()
            removed.clear()
            cleared = false
        }
    }
}
