package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.storage.FireSide
import pt.tiago.steeleagle.storage.GameSettings
import pt.tiago.steeleagle.storage.TouchControl

enum class OptionRow(val label: String) {
    TOUCH_CONTROL("TOUCH"),
    OPACITY("OPACITY"),
    SIZE("SIZE"),
    FIRE_SIDE("FIRE"),
    MUSIC("MUSIC"),
    SFX("SFX"),
    SCANLINES("SCANLINES"),
    VIBRATION("VIBRATE"),
    BACK("BACK"),
}

/**
 * Options cursor and value changes. Values are clamped or cycled here so the
 * drawing layer only has to show labels; every change is persisted by the flow.
 */
class OptionsModel(val settings: GameSettings) {

    var selected: Int = 0
        private set

    val row: OptionRow
        get() = OptionRow.entries[selected]

    fun move(delta: Int) {
        selected = Math.floorMod(selected + delta, OptionRow.entries.size)
    }

    fun select(index: Int) {
        require(index in OptionRow.entries.indices) { "option index out of range" }
        selected = index
    }

    /** Applies one step; returns false for the BACK row. */
    fun change(direction: Int): Boolean {
        if (direction == 0) return row != OptionRow.BACK

        return when (row) {
            OptionRow.TOUCH_CONTROL -> {
                settings.touchControl = cycle(TouchControl.entries, settings.touchControl, direction)
                true
            }

            OptionRow.OPACITY -> {
                settings.touchOpacity = (settings.touchOpacity + direction * OPACITY_STEP)
                    .coerceIn(MIN_OPACITY, MAX_OPACITY)
                true
            }

            OptionRow.SIZE -> {
                settings.touchSize = Math.floorMod(settings.touchSize + direction, 3)
                true
            }

            OptionRow.FIRE_SIDE -> {
                settings.fireSide = if (settings.fireSide == FireSide.RIGHT) {
                    FireSide.LEFT
                } else {
                    FireSide.RIGHT
                }
                true
            }

            OptionRow.MUSIC -> {
                settings.musicVolume = (settings.musicVolume + direction).coerceIn(0, 10)
                true
            }

            OptionRow.SFX -> {
                settings.sfxVolume = (settings.sfxVolume + direction).coerceIn(0, 10)
                true
            }

            OptionRow.SCANLINES -> {
                settings.scanlines = !settings.scanlines
                true
            }

            OptionRow.VIBRATION -> {
                settings.vibration = !settings.vibration
                true
            }

            OptionRow.BACK -> false
        }
    }

    /** Fire on the selected row: advance a value or leave through BACK. */
    fun activate(): Boolean = change(1)

    /** Value shown for [option]; every row is drawn, not just the selected one. */
    fun valueLabel(option: OptionRow = row): String = when (option) {
        OptionRow.TOUCH_CONTROL -> when (settings.touchControl) {
            TouchControl.DPAD -> "D-PAD"
            TouchControl.JOYSTICK -> "JOYSTICK"
            TouchControl.NES -> "NES PAD"
            TouchControl.JOY_AB -> "JOY + AB"
        }

        OptionRow.OPACITY -> "${settings.touchOpacity} PCT"

        OptionRow.SIZE -> when (settings.touchSize) {
            0 -> "SMALL"
            1 -> "MEDIUM"
            else -> "LARGE"
        }

        OptionRow.FIRE_SIDE -> when (settings.fireSide) {
            FireSide.RIGHT -> "RIGHT"
            FireSide.LEFT -> "LEFT"
        }

        OptionRow.MUSIC -> settings.musicVolume.toString()
        OptionRow.SFX -> settings.sfxVolume.toString()
        OptionRow.SCANLINES -> if (settings.scanlines) "ON" else "OFF"
        OptionRow.VIBRATION -> if (settings.vibration) "ON" else "OFF"
        OptionRow.BACK -> ""
    }

    private fun <T> cycle(values: List<T>, current: T, direction: Int): T =
        values[Math.floorMod(values.indexOf(current) + direction, values.size)]

    private companion object {
        const val OPACITY_STEP = 10
        const val MIN_OPACITY = 30
        const val MAX_OPACITY = 100
    }
}
