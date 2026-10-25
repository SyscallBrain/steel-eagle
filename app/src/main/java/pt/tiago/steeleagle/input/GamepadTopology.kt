package pt.tiago.steeleagle.input

/**
 * Android InputDevice source bits used to classify pads without touching the
 * Android framework in JVM tests. Values match android.view.InputDevice.
 */
object GamepadSources {
    const val GAMEPAD = 0x00000401
    const val JOYSTICK = 0x01000010

    /**
     * Source constants are descriptors, not independent bits (SOURCE_GAMEPAD
     * contains SOURCE_CLASS_BUTTON). A plain "and != 0" would classify a
     * keyboard as a gamepad.
     */
    fun isGamepad(sources: Int): Boolean =
        (sources and GAMEPAD) == GAMEPAD || (sources and JOYSTICK) == JOYSTICK
}

/**
 * Tracks which input-device ids are gamepads. Existing devices are seeded so
 * attaching the view does not look like a controller connection.
 */
class GamepadTopology {

    private val ids = mutableSetOf<Int>()

    fun reset() {
        ids.clear()
    }

    fun seed(sourcesById: Map<Int, Int>) {
        for ((deviceId, sources) in sourcesById) {
            if (GamepadSources.isGamepad(sources)) ids.add(deviceId)
        }
    }

    /** Returns true when a new gamepad id was added. */
    fun add(deviceId: Int, sources: Int): Boolean {
        if (!GamepadSources.isGamepad(sources)) return false
        return ids.add(deviceId)
    }

    /** Returns true when a known gamepad id was removed. */
    fun remove(deviceId: Int): Boolean = ids.remove(deviceId)

    fun contains(deviceId: Int): Boolean = deviceId in ids

    /** Connection order, used for the first/second pad assignment. */
    fun playerFor(deviceId: Int): Int? =
        ids.indexOf(deviceId).takeIf { it in 0..1 }

    val size: Int
        get() = ids.size
}
