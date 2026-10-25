package pt.tiago.steeleagle.storage

import android.content.Context
import android.content.SharedPreferences
import pt.tiago.steeleagle.core.rules.Difficulty

/** SharedPreferences-backed settings, high scores and per-difficulty progress. */
class Prefs(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
    )

    fun loadSettings(): GameSettings = GameSettings(
        touchControl = enumValue(
            prefs.getString(KEY_TOUCH_CONTROL, null),
            TouchControl.DPAD,
        ),
        touchOpacity = prefs.getInt(KEY_TOUCH_OPACITY, 70),
        touchSize = prefs.getInt(KEY_TOUCH_SIZE, 2),
        fireSide = enumValue(prefs.getString(KEY_FIRE_SIDE, null), FireSide.RIGHT),
        musicVolume = prefs.getInt(KEY_MUSIC, 5),
        sfxVolume = prefs.getInt(KEY_SFX, 7),
        scanlines = prefs.getBoolean(KEY_SCANLINES, false),
        vibration = prefs.getBoolean(KEY_VIBRATION, true),
    ).sanitized()

    fun saveSettings(settings: GameSettings) {
        val clean = settings.sanitized()
        prefs.edit()
            .putString(KEY_TOUCH_CONTROL, clean.touchControl.name)
            .putInt(KEY_TOUCH_OPACITY, clean.touchOpacity)
            .putInt(KEY_TOUCH_SIZE, clean.touchSize)
            .putString(KEY_FIRE_SIDE, clean.fireSide.name)
            .putInt(KEY_MUSIC, clean.musicVolume)
            .putInt(KEY_SFX, clean.sfxVolume)
            .putBoolean(KEY_SCANLINES, clean.scanlines)
            .putBoolean(KEY_VIBRATION, clean.vibration)
            .apply()
    }

    fun maxUnlocked(difficulty: Difficulty): Int =
        prefs.getInt("$KEY_MAX_UNLOCKED${difficulty.name}", 1).coerceAtLeast(1)

    fun unlockLevel(difficulty: Difficulty, levelNumber: Int) {
        if (levelNumber > maxUnlocked(difficulty)) {
            prefs.edit()
                .putInt("$KEY_MAX_UNLOCKED${difficulty.name}", levelNumber)
                .apply()
        }
    }

    fun loadHighScores(): HighScores =
        HighScores.parse(prefs.getString(KEY_HIGH_SCORES, "") ?: "")

    fun saveHighScores(scores: HighScores) {
        prefs.edit().putString(KEY_HIGH_SCORES, scores.serialize()).apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)

    private companion object {
        const val FILE_NAME = "steel_eagle"
        const val KEY_TOUCH_CONTROL = "touch_control"
        const val KEY_TOUCH_OPACITY = "touch_opacity"
        const val KEY_TOUCH_SIZE = "touch_size"
        const val KEY_FIRE_SIDE = "fire_side"
        const val KEY_MUSIC = "music_volume"
        const val KEY_SFX = "sfx_volume"
        const val KEY_SCANLINES = "scanlines"
        const val KEY_VIBRATION = "vibration"
        const val KEY_HIGH_SCORES = "high_scores"
        const val KEY_MAX_UNLOCKED = "max_unlocked_"
    }
}
