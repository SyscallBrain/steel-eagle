package pt.tiago.steeleagle.storage

/**
 * Touch control schemes. NES is a fixed cross pad and JOY_AB the floating
 * joystick; both draw NES-style A and B buttons, and both buttons fire.
 */
enum class TouchControl(
    val joystick: Boolean,
    val crossPad: Boolean,
    val abButtons: Boolean,
) {
    DPAD(joystick = false, crossPad = false, abButtons = false),
    JOYSTICK(joystick = true, crossPad = false, abButtons = false),
    NES(joystick = false, crossPad = true, abButtons = true),
    JOY_AB(joystick = true, crossPad = false, abButtons = true),
}

enum class FireSide {
    RIGHT,
    LEFT,
}

/** Player options persisted across launches. */
data class GameSettings(
    var touchControl: TouchControl = TouchControl.DPAD,
    var touchOpacity: Int = 70,
    var touchSize: Int = 2,
    var fireSide: FireSide = FireSide.RIGHT,
    var musicVolume: Int = 5,
    var sfxVolume: Int = 7,
    var scanlines: Boolean = false,
    var vibration: Boolean = true,
) {
    fun sanitized(): GameSettings = copy(
        touchOpacity = touchOpacity.coerceIn(30, 100),
        touchSize = touchSize.coerceIn(0, 2),
        musicVolume = musicVolume.coerceIn(0, 10),
        sfxVolume = sfxVolume.coerceIn(0, 10),
    )
}
