package pt.tiago.steeleagle

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import pt.tiago.steeleagle.audio.AudioEngine
import pt.tiago.steeleagle.core.level.UserLevelStore
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.time.FixedStepClock
import pt.tiago.steeleagle.debug.DebugPowerUpMenu
import pt.tiago.steeleagle.flow.GameFlow
import pt.tiago.steeleagle.input.CombinedInput
import pt.tiago.steeleagle.input.GamepadInput
import pt.tiago.steeleagle.input.GamepadSources
import pt.tiago.steeleagle.input.GamepadTopology
import pt.tiago.steeleagle.input.KeyboardInput
import pt.tiago.steeleagle.input.SwitchableTouchInput
import pt.tiago.steeleagle.input.TouchDpadInput
import pt.tiago.steeleagle.input.TouchJoystickInput
import pt.tiago.steeleagle.input.TouchLayout
import pt.tiago.steeleagle.net.NetworkDiscovery
import pt.tiago.steeleagle.level.LevelAssets
import pt.tiago.steeleagle.render.GameRenderer
import pt.tiago.steeleagle.render.PixelFont
import pt.tiago.steeleagle.render.SpriteAtlas
import pt.tiago.steeleagle.render.Viewport
import pt.tiago.steeleagle.scene.SceneManager
import pt.tiago.steeleagle.storage.FireSide
import pt.tiago.steeleagle.storage.GameSettings
import pt.tiago.steeleagle.storage.Prefs
import pt.tiago.steeleagle.storage.TouchControl
import java.io.File
import java.util.concurrent.locks.LockSupport

/**
 * Render surface: fixed 60 Hz simulation, integer-scaled 256x224 image, the
 * input sources, audio and the full GameFlow of screens.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val atlas = SpriteAtlas.load(context)
    private val font = PixelFont(atlas)
    private val prefs = Prefs(context)
    private val audio = AudioEngine(context)
    private val levels = LevelAssets.loadAll(context)
    private val userLevels = UserLevelStore(File(context.filesDir, "levels"))
    private val network = NetworkDiscovery(context)
    private val flow = GameFlow(
        levels = levels,
        atlas = atlas,
        font = font,
        audio = audio,
        prefs = prefs,
        debug = isDebuggable(context),
        userStore = userLevels,
        discovery = network,
    )
    private val sceneManager = SceneManager(flow)
    private val renderer = GameRenderer(font, showFps = isDebuggable(context))
    private val debugMenu = if (isDebuggable(context)) {
        DebugPowerUpMenu(atlas, resources.displayMetrics.density)
    } else {
        null
    }
    private val pauseButton = PauseButton(resources.displayMetrics.density)
    private val density = resources.displayMetrics.density
    private val clock = FixedStepClock()

    private val keyboardInput = KeyboardInput()
    private val gamepadP1 = GamepadInput { deviceId -> acceptsP1(deviceId) }
    private val gamepadP2 = GamepadInput { deviceId -> acceptsP2(deviceId) }
    private val touchDpad = TouchDpadInput()
    private val touchJoystick = TouchJoystickInput()
    private val touchInput = SwitchableTouchInput(touchDpad, touchJoystick)
    private val touchDpadP2 = TouchDpadInput()
    private val p1Input = CombinedInput(listOf(gamepadP1, keyboardInput, touchInput))
    private val p2Input = CombinedInput(listOf(gamepadP2, touchDpadP2))

    @Volatile
    private var viewport: Viewport? = null

    @Volatile
    private var screenWidth = 0

    @Volatile
    private var screenHeight = 0

    @Volatile
    private var appliedSettingsVersion = -1

    @Volatile
    private var appliedPlayerCount = -1

    @Volatile
    private var touchSettings = GameSettings()

    @Volatile
    private var renderThread: RenderThread? = null

    private var touchCapture = false

    /**
     * Settings/layout use their own lock, never the GameView monitor: the
     * render thread refreshes settings every frame, while stopRendering()
     * holds the GameView monitor and joins that same thread.
     */
    private val settingsLock = Any()

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val gamepads = GamepadTopology()
    private var listeningForDevices = false

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            if (gamepads.add(deviceId, sourcesOf(deviceId))) {
                resetInputs()
                flow.onGamepadChanged(true)
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            if (gamepads.remove(deviceId)) {
                resetInputs()
                flow.onGamepadChanged(false)
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            val sources = sourcesOf(deviceId)
            if (GamepadSources.isGamepad(sources)) {
                if (gamepads.add(deviceId, sources)) {
                    resetInputs()
                    flow.onGamepadChanged(true)
                }
            } else if (gamepads.remove(deviceId)) {
                resetInputs()
                flow.onGamepadChanged(false)
            }
        }
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val frame = holder.surfaceFrame
        updateViewport(frame.width(), frame.height())
        startRendering()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        updateViewport(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRendering()
    }

    /** Stops simulation and rendering; a running game returns to its pause menu. */
    fun pause() {
        flow.autoPause()
        stopRendering()
        resetInputs()
        audio.pause()
    }

    /** Restarts rendering if the surface is ready; safe to call more than once. */
    fun resume() {
        audio.start()
        flow.onResumeAudio()
        if (holder.surface.isValid) startRendering()
    }

    /** Back button and gamepad Select; false on the title so the Activity exits. */
    fun onBack(): Boolean = flow.onBack()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (listeningForDevices) return

        gamepads.reset()
        inputManager?.registerInputDeviceListener(inputDeviceListener, null)
        listeningForDevices = true

        val existing = InputDevice.getDeviceIds().associateWith { deviceId ->
            sourcesOf(deviceId)
        }
        gamepads.seed(existing)
    }

    override fun onDetachedFromWindow() {
        flow.close()
        if (listeningForDevices) {
            inputManager?.unregisterInputDeviceListener(inputDeviceListener)
            listeningForDevices = false
        }
        gamepads.reset()
        stopRendering()
        audio.release()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) resetInputs()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_MENU,
                -> if (flow.onStart()) return true

                KeyEvent.KEYCODE_P -> if (flow.onPauseKey()) return true

                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                -> if (flow.onBack()) return true
            }
        }

        val fromGamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val gamepadHandled = fromGamepad &&
            (gamepadP1.onKeyDown(keyCode, event) || gamepadP2.onKeyDown(keyCode, event))
        val keyboardHandled = !fromGamepad && keyboardInput.onKeyDown(keyCode, event)
        return gamepadHandled || keyboardHandled || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val fromGamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val gamepadHandled = fromGamepad &&
            (
                gamepadP1.onKeyUp(keyCode, event.deviceId) ||
                    gamepadP2.onKeyUp(keyCode, event.deviceId)
                )
        val keyboardHandled = !fromGamepad && keyboardInput.onKeyUp(keyCode)
        return gamepadHandled || keyboardHandled || super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        gamepadP1.onGenericMotionEvent(event) ||
            gamepadP2.onGenericMotionEvent(event) ||
            super.onGenericMotionEvent(event)

    private fun acceptsP1(deviceId: Int): Boolean =
        flow.playerCount == 1 || gamepads.playerFor(deviceId) == 0

    private fun acceptsP2(deviceId: Int): Boolean =
        flow.playerCount == 2 && gamepads.playerFor(deviceId) == 1

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (flow.showDebugPowerUps &&
            debugMenu?.onTouch(event) { flow.debugSpawnPowerUp(it) } == true
        ) {
            return true
        }

        if (flow.showPauseButton && isPointerDown(event) && pauseButton.contains(
                event.getX(event.actionIndex).toInt(),
                event.getY(event.actionIndex).toInt(),
            )
        ) {
            flow.togglePause()
            touchInput.reset()
            return true
        }

        if (flow.usesTouchControls) {
            touchInput.onTouchEvent(event)
            if (flow.playerCount == 2) touchDpadP2.onTouchEvent(event)
            return true
        }

        touchInput.reset()
        touchDpadP2.reset()
        val current = viewport ?: return true
        if (flow.isEditing) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val x = event.getX(0).toInt()
                    val y = event.getY(0).toInt()
                    if (current.contains(x, y)) {
                        val logicalX = (x - current.left) * Viewport.LOGICAL_WIDTH / current.width
                        val logicalY = (y - current.top) * Viewport.LOGICAL_HEIGHT / current.height
                        flow.onEditorDrag(logicalX, logicalY)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> flow.onEditorRelease()
                else -> Unit
            }
        }
        if (isPointerDown(event)) {
            val x = event.getX(event.actionIndex).toInt()
            val y = event.getY(event.actionIndex).toInt()
            if (current.contains(x, y)) {
                val logicalX = (x - current.left) * Viewport.LOGICAL_WIDTH / current.width
                val logicalY = (y - current.top) * Viewport.LOGICAL_HEIGHT / current.height
                flow.onTap(logicalX, logicalY)
            }
        }
        return true
    }

    private fun isPointerDown(event: MotionEvent): Boolean =
        event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN

    private fun updateViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        screenWidth = width
        screenHeight = height
        val newViewport = Viewport.fit(width, height)
        viewport = newViewport
        val relaid = refreshSettingsIfNeeded()
        if (!relaid) updateTouchLayout()
        debugMenu?.layout(width, height)
        pauseButton.layout(width, height, newViewport)
    }

    /** Applies pending settings/player-count changes; true if the touch layout was rebuilt. */
    private fun refreshSettingsIfNeeded(): Boolean {
        val settingsChanged = synchronized(settingsLock) {
            val version = flow.settingsVersion
            if (version == appliedSettingsVersion) {
                false
            } else {
                appliedSettingsVersion = version
                touchSettings = flow.settingsSnapshot()
                true
            }
        }
        val playersChanged = synchronized(settingsLock) {
            val count = flow.playerCount
            if (count == appliedPlayerCount) {
                false
            } else {
                appliedPlayerCount = count
                true
            }
        }
        if (!settingsChanged && !playersChanged) return false
        if (settingsChanged) {
            renderer.scanlines = synchronized(settingsLock) { touchSettings.scanlines }
        }
        if (playersChanged) resetInputs()
        updateTouchLayout()
        return true
    }

    private fun updateTouchLayout() {
        synchronized(settingsLock) {
            val currentViewport = viewport ?: return
            val width = screenWidth
            val height = screenHeight
            if (width <= 0 || height <= 0) return

            val settings = touchSettings
            if (flow.playerCount == 2) {
                touchInput.select(TouchControl.DPAD)
                touchInput.setLayout(
                    TouchLayout.computeTwoPlayer(
                        screenWidth = width,
                        screenHeight = height,
                        viewport = currentViewport,
                        density = density,
                        player = 0,
                        sizeStep = settings.touchSize,
                        opacity = settings.touchOpacity,
                    ),
                )
                touchDpadP2.setLayout(
                    TouchLayout.computeTwoPlayer(
                        screenWidth = width,
                        screenHeight = height,
                        viewport = currentViewport,
                        density = density,
                        player = 1,
                        sizeStep = settings.touchSize,
                        opacity = settings.touchOpacity,
                    ),
                )
            } else {
                touchInput.select(settings.touchControl)
                touchInput.setLayout(
                    TouchLayout.compute(
                        screenWidth = width,
                        screenHeight = height,
                        viewport = currentViewport,
                        density = density,
                        joystick = settings.touchControl.joystick,
                        sizeStep = settings.touchSize,
                        opacity = settings.touchOpacity,
                        fireLeft = settings.fireSide == FireSide.LEFT,
                    ).copy(abButtons = settings.touchControl.abButtons),
                )
                touchDpadP2.reset()
            }
        }
    }

    private fun sourcesOf(deviceId: Int): Int =
        InputDevice.getDevice(deviceId)?.sources ?: 0

    private fun resetInputs() {
        keyboardInput.reset()
        gamepadP1.reset()
        gamepadP2.reset()
        touchInput.reset()
        touchDpadP2.reset()
    }

    @Synchronized
    private fun startRendering() {
        val existing = renderThread
        if (existing != null && existing.isAlive) return
        renderThread = RenderThread().apply { start() }
    }

    @Synchronized
    private fun stopRendering() {
        val thread = renderThread ?: return
        thread.running = false
        thread.interrupt()

        // SurfaceHolder requires the render thread to be gone before returning.
        // The loop always exits within one frame; restore an interrupt aimed at
        // the caller afterwards so we do not swallow it.
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        renderThread = null
        if (interrupted) Thread.currentThread().interrupt()
    }

    private inner class RenderThread : Thread("SteelEagleRender") {

        @Volatile
        var running = true

        override fun run() {
            val frameNanos = 1_000_000_000L / TARGET_FPS
            var nextFrame = System.nanoTime()
            var lastTick = System.nanoTime()

            while (running) {
                val now = System.nanoTime()
                val steps = clock.advance(now - lastTick)
                lastTick = now

                refreshSettingsIfNeeded()
                if (flow.usesTouchControls != touchCapture) {
                    touchInput.reset()
                    touchDpadP2.reset()
                    touchCapture = flow.usesTouchControls
                }

                val p1 = p1Input.sample()
                val p2 = if (flow.playerCount == 2) {
                    p2Input.sample()
                } else {
                    PlayerInput.NONE
                }
                repeat(steps) {
                    sceneManager.update(p1, p2)
                }

                val currentViewport = viewport
                val canvas = lockCanvasSafely()
                if (canvas != null) {
                    try {
                        if (currentViewport != null) {
                            val touch = if (flow.usesTouchControls) touchInput else null
                            val touch2 = if (flow.usesTouchControls && flow.playerCount == 2) {
                                touchDpadP2
                            } else {
                                null
                            }
                            renderer.draw(canvas, sceneManager, currentViewport, touch, touch2)
                            if (flow.showDebugPowerUps) debugMenu?.draw(canvas)
                            if (flow.showPauseButton) pauseButton.draw(canvas)
                        } else {
                            canvas.drawColor(Color.BLACK)
                        }
                    } finally {
                        unlockSafely(canvas)
                    }
                }

                nextFrame += frameNanos
                val sleepNanos = nextFrame - System.nanoTime()
                if (sleepNanos > 0) {
                    LockSupport.parkNanos(sleepNanos)
                } else {
                    nextFrame = System.nanoTime()
                }
            }
        }

        private fun lockCanvasSafely(): Canvas? = try {
            holder.lockCanvas()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }

        private fun unlockSafely(canvas: Canvas) {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (_: IllegalArgumentException) {
                // Surface vanished between lock and post; the loop stops on the next lifecycle callback.
            } catch (_: IllegalStateException) {
                // Same as above.
            }
        }
    }

    private companion object {
        const val TARGET_FPS = 60L

        fun isDebuggable(context: Context): Boolean =
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
