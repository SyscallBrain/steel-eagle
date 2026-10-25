package pt.tiago.steeleagle.flow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import pt.tiago.steeleagle.audio.AudioEngine
import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.game.GameSession
import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.editor.EditorMessages
import pt.tiago.steeleagle.core.level.LevelValidator
import pt.tiago.steeleagle.core.level.UserLevel
import pt.tiago.steeleagle.core.level.UserLevelStore
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.net.LanClient
import pt.tiago.steeleagle.core.net.NetMessage
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import pt.tiago.steeleagle.core.sprite.TankSprites
import pt.tiago.steeleagle.core.world.WorldSnapshot
import pt.tiago.steeleagle.core.world.WorldState
import pt.tiago.steeleagle.core.world.interpolateSnapshots
import pt.tiago.steeleagle.net.LanGame
import pt.tiago.steeleagle.net.LanRole
import pt.tiago.steeleagle.net.LanService
import pt.tiago.steeleagle.net.NetworkDiscovery
import pt.tiago.steeleagle.render.BrickText
import pt.tiago.steeleagle.render.PixelFont
import pt.tiago.steeleagle.render.SpriteAtlas
import pt.tiago.steeleagle.scene.EditorScene
import pt.tiago.steeleagle.scene.PlayScene
import pt.tiago.steeleagle.scene.Scene
import pt.tiago.steeleagle.storage.GameSettings
import pt.tiago.steeleagle.storage.HighScores
import pt.tiago.steeleagle.storage.Prefs

enum class FlowScreen {
    TITLE,
    MODE,
    TWO_PLAYER_MODE,
    DIFFICULTY,
    LEVEL_SELECT,
    NOTICE,
    CURTAIN,
    PLAY,
    PAUSE,
    TALLY,
    GAME_OVER,
    INITIALS,
    HIGH_SCORES,
    OPTIONS,
    EDITOR,
    LAN_LOBBY,
    LAN_HOST_WAIT,
    LAN_JOIN,
    LAN_IP,
    LAN_CONNECTING,
}

/** Level-select tabs: built-in campaign stages or editor levels. */
enum class LevelTab {
    CAMPAIGN,
    USER,
}

/**
 * Full one- or two-player flow: title, mode, difficulty,
 * stage select, grey stage curtain, play, pause, tally, game over, initials,
 * high scores and options. Menus work with the D-pad/fire and by tapping.
 */
class GameFlow(
    private val levels: List<Level>,
    private val atlas: SpriteAtlas,
    private val font: PixelFont,
    private val audio: AudioEngine?,
    private val prefs: Prefs,
    private val debug: Boolean = false,
    private val userStore: UserLevelStore? = null,
    private val discovery: NetworkDiscovery? = null,
) : Scene, EditorScene.Host {

    init {
        require(levels.isNotEmpty()) { "the flow needs at least one level" }
    }

    @Volatile
    private var screenState: FlowScreen = FlowScreen.TITLE

    private var screenTicks = 0
    private var resultTicks = 0

    private var settings: GameSettings = prefs.loadSettings()
    @Volatile
    private var settingsRevision = 0
    private val options = OptionsModel(settings)
    private var pauseNote: String? = null
    private var highScores: HighScores = prefs.loadHighScores()
    private var highScoresDifficulty = Difficulty.NORMAL
    private var highScoresReturn = FlowScreen.MODE

    private var selectedDifficulty = Difficulty.NORMAL
    private var grid = LevelGrid(levels.size, prefs.maxUnlocked(Difficulty.NORMAL))
    private var levelBackFocused = false

    @Volatile
    private var players = 1

    private var session: GameSession? = null
    private var playScene: PlayScene? = null
    private val levelStartScores = IntArray(2)
    private var tally: TallySummary? = null
    private var tally2p: TallySummary2P? = null
    private var gameOverScore = 0
    private var gameOverScores: IntArray? = null
    private var gameOverDifficulty = Difficulty.NORMAL
    private var initials = InitialsEntry()

    private var editor: EditorScene? = null
    private var returnToEditor = false
    private var playInputLock = 0
    private var singleLevel = false
    private var playedUserIndex = 0

    private val lan = LanGame()
    private val lanLobbyMenu = Menu(listOf("HOST GAME", "JOIN GAME", "BACK"))
    private var lanError: String? = null
    private var lanHostStage = false
    private var lanHadGuest = false
    private var lanGuestLevel = -1
    private var lanSeed = 1
    private var lanDifficulty = Difficulty.NORMAL
    private var peerPauseLast = false
    private var lanSessionConnection = 0
    private var lanHostIp = "NO WI-FI ADDRESS"
    private var prevSnapshot: WorldSnapshot? = null
    private var currSnapshot: WorldSnapshot? = null
    private var prevArrivedAt = 0L
    private var currArrivedAt = 0L
    private var lanJoinFocus = 0
    private var lanJoinGames: List<LanService> = emptyList()
    private var lanAddress = ""
    private var ipEntry = IpEntry()
    private var ipError = false

    private var levelTab = LevelTab.CAMPAIGN
    private var levelTabFocused = false
    private var userList: List<UserLevel> = emptyList()
    private var userSelected = 0

    private var optionsReturn = FlowScreen.MODE
    private var noticeTitle = ""
    private var noticeBody = ""
    private var noticeBack = FlowScreen.MODE
    private var noticeHint = "NOT AVAILABLE YET"
    private var titleMusicOn = true
    private val tapGuard = TapGuard()

    private val modeMenu = Menu(
        listOf("1 PLAYER", "2 PLAYERS", "CONSTRUCTION", "HIGH SCORES", "OPTIONS", "BACK"),
    )
    private val twoPlayerMenu = Menu(
        listOf("SAME DEVICE", "LOCAL NETWORK", "BACK"),
    )
    private val difficultyMenu = Menu(listOf("EASY", "NORMAL", "HARD", "BACK"))
    private val pauseMenu = Menu(listOf("CONTINUE", "OPTIONS", "EXIT TO TITLE"))
    private val testPauseMenu = Menu(listOf("CONTINUE", "BACK TO EDITOR"))
    private val edges = InputEdges()

    private val brickText = BrickText(atlas, font)
    private val tankCursor = atlas.bitmap(TankSprites.name(Dir.RIGHT, 0))

    private val fillPaint = Paint().apply { isAntiAlias = false }
    private val strokePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val shadowPaint = Paint().apply {
        isAntiAlias = false
        colorFilter = PorterDuffColorFilter(SHADOW, PorterDuff.Mode.MULTIPLY)
    }
    private val textPaints = listOf(WHITE, GREY, DARK, YELLOW, BLACK, P2_GREEN)
        .associateWith { color ->
            Paint().apply {
                isAntiAlias = false
                isFilterBitmap = false
                colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
        }

    init {
        applySettings()
        screenState = FlowScreen.TITLE
        screenTicks = 0
        titleMusicOn = true
    }

    @get:Synchronized
    val showPauseButton: Boolean
        get() = screenState == FlowScreen.PLAY &&
            playScene?.session?.world?.state == WorldState.PLAYING

    val showDebugPowerUps: Boolean
        get() = (screenState == FlowScreen.PLAY || screenState == FlowScreen.PAUSE) &&
            lan.role != LanRole.GUEST

    val usesTouchControls: Boolean
        get() = screenState == FlowScreen.PLAY

    val isPlaying: Boolean
        get() = screenState == FlowScreen.PLAY

    val isEditing: Boolean
        get() = screenState == FlowScreen.EDITOR

    val playerCount: Int
        get() = players

    val settingsVersion: Int
        get() = settingsRevision

    @Synchronized
    fun settingsSnapshot(): GameSettings = settings.sanitized()

    @Synchronized
    override fun update(input: PlayerInput, second: PlayerInput) {
        val frame = edges.update(input)
        screenTicks++
        tapGuard.tick()
        val previousScreen = screenState
        val actionFrame = if (tapGuard.blocks()) frame.copy(fire = false) else frame

        when (screenState) {
            FlowScreen.TITLE -> if (actionFrame.fire && screenTicks > TITLE_GUARD_TICKS) {
                openMode()
            }

            FlowScreen.MODE -> handleMenu(modeMenu, actionFrame, ::activateMode)
            FlowScreen.TWO_PLAYER_MODE -> handleMenu(
                twoPlayerMenu,
                actionFrame,
                ::activateTwoPlayerMode,
            )

            FlowScreen.DIFFICULTY -> handleMenu(
                difficultyMenu,
                actionFrame,
                ::activateDifficulty,
            )

            FlowScreen.LEVEL_SELECT -> handleLevelSelect(actionFrame)
            FlowScreen.NOTICE -> if (actionFrame.fire) backFromNotice()
            FlowScreen.CURTAIN -> if (actionFrame.fire || screenTicks >= CURTAIN_TICKS) {
                beginPlay()
            }

            FlowScreen.PLAY -> if (lan.role == LanRole.GUEST) Unit else {
                updatePlay(input, second, actionFrame)
            }
            FlowScreen.PAUSE -> handleMenu(activePauseMenu(), actionFrame, ::activatePause)
            FlowScreen.TALLY -> if (actionFrame.fire && screenTicks > TALLY_GUARD_TICKS) {
                finishTally()
            }

            FlowScreen.GAME_OVER -> {
                if ((actionFrame.fire && screenTicks > GAME_OVER_GUARD_TICKS) ||
                    screenTicks >= GAME_OVER_TICKS
                ) {
                    proceedAfterGameOver()
                }
            }

            FlowScreen.INITIALS -> handleInitials(actionFrame)
            FlowScreen.HIGH_SCORES -> handleHighScores(actionFrame)
            FlowScreen.OPTIONS -> handleOptions(actionFrame)
            FlowScreen.EDITOR -> editor?.update(input)
            FlowScreen.LAN_LOBBY -> handleMenu(lanLobbyMenu, actionFrame, ::activateLanLobby)
            FlowScreen.LAN_HOST_WAIT -> if (actionFrame.fire) cancelHosting()
            FlowScreen.LAN_JOIN -> handleLanJoin(actionFrame)
            FlowScreen.LAN_IP -> handleLanIp(actionFrame)
            FlowScreen.LAN_CONNECTING -> handleLanConnecting(actionFrame)
        }

        if (lan.role == LanRole.HOST) {
            pumpLanHost()
        } else if (lan.role == LanRole.GUEST &&
            (screenState == FlowScreen.PLAY || screenState == FlowScreen.PAUSE)
        ) {
            pumpLanGuest(input)
        }
        if (screenState != previousScreen) tapGuard.arm()
    }

    @Synchronized
    override fun draw(canvas: Canvas) {
        when (screenState) {
            FlowScreen.TITLE -> drawTitle(canvas)
            FlowScreen.MODE -> drawMode(canvas)
            FlowScreen.TWO_PLAYER_MODE -> drawTwoPlayerMode(canvas)
            FlowScreen.DIFFICULTY -> drawDifficulty(canvas)
            FlowScreen.LEVEL_SELECT -> drawLevelSelect(canvas)
            FlowScreen.NOTICE -> drawNotice(canvas)
            FlowScreen.CURTAIN -> drawCurtain(canvas)
            FlowScreen.PLAY -> drawPlay(canvas)
            FlowScreen.PAUSE -> drawPause(canvas)
            FlowScreen.TALLY -> drawTally(canvas)
            FlowScreen.GAME_OVER -> drawGameOver(canvas)
            FlowScreen.INITIALS -> drawInitials(canvas)
            FlowScreen.HIGH_SCORES -> drawHighScores(canvas)
            FlowScreen.OPTIONS -> drawOptions(canvas)
            FlowScreen.EDITOR -> editor?.draw(canvas)
            FlowScreen.LAN_LOBBY -> drawLanLobby(canvas)
            FlowScreen.LAN_HOST_WAIT -> drawLanHostWait(canvas)
            FlowScreen.LAN_JOIN -> drawLanJoin(canvas)
            FlowScreen.LAN_IP -> drawLanIp(canvas)
            FlowScreen.LAN_CONNECTING -> drawLanConnecting(canvas)
        }
    }

    /** Start button: confirm in menus, pause during play, skip prompts. */
    @Synchronized
    fun onStart(): Boolean {
        if (tapGuard.blocks()) return true
        val previousScreen = screenState
        when (screenState) {
            FlowScreen.TITLE -> openMode()
            FlowScreen.MODE -> activateMode(modeMenu.selected)
            FlowScreen.TWO_PLAYER_MODE -> activateTwoPlayerMode(twoPlayerMenu.selected)
            FlowScreen.DIFFICULTY -> activateDifficulty(difficultyMenu.selected)
            FlowScreen.LEVEL_SELECT -> when {
                levelTabFocused -> {
                    levelTabFocused = false
                    edges.lockFire()
                }

                levelBackFocused -> returnToDifficulty()
                else -> activateSelectedLevel()
            }

            FlowScreen.NOTICE -> backFromNotice()
            FlowScreen.CURTAIN -> beginPlay()
            FlowScreen.PLAY, FlowScreen.PAUSE -> togglePause()
            FlowScreen.TALLY -> finishTally()
            FlowScreen.GAME_OVER -> proceedAfterGameOver()
            FlowScreen.EDITOR -> Unit
            FlowScreen.LAN_LOBBY -> activateLanLobby(lanLobbyMenu.selected)
            FlowScreen.LAN_HOST_WAIT -> cancelHosting()
            FlowScreen.LAN_JOIN -> activateLanJoin(lanJoinFocus)
            FlowScreen.LAN_IP -> confirmIp()
            FlowScreen.LAN_CONNECTING -> cancelConnecting()
            FlowScreen.INITIALS -> confirmInitials()
            FlowScreen.HIGH_SCORES -> leaveHighScores()
            FlowScreen.OPTIONS -> leaveOptions()
        }
        if (screenState != previousScreen) tapGuard.arm()
        return true
    }

    /** On-screen pause button, Start during play, or the back button. */
    @Synchronized
    fun togglePause(): Boolean {
        when (screenState) {
            FlowScreen.PLAY -> {
                if (playScene?.session?.world?.state != WorldState.PLAYING) return false
                pauseNote = null
                screenState = FlowScreen.PAUSE
                screenTicks = 0
                pauseMenu.select(0)
                testPauseMenu.select(0)
                audio?.setEngine(false, false)
                audio?.stopMusic()
                audio?.playPause()
                edges.lockFire()
                tapGuard.arm()
                if (lan.role != LanRole.NONE) lan.sendPause(true)
                return true
            }

            FlowScreen.PAUSE -> {
                pauseNote = null
                screenState = FlowScreen.PLAY
                screenTicks = 0
                edges.lockFire()
                tapGuard.arm()
                if (lan.role != LanRole.NONE) lan.sendPause(false)
                return true
            }

            else -> return false
        }
    }

    /** Hardware/keyboard P: pause and resume only while in the game. */
    @Synchronized
    fun onPauseKey(): Boolean =
        if (screenState == FlowScreen.PLAY || screenState == FlowScreen.PAUSE) {
            togglePause()
        } else {
            false
        }

    /** Back button and gamepad Select. Returns false only on the title. */
    @Synchronized
    fun onBack(): Boolean {
        val previousScreen = screenState
        when (screenState) {
            FlowScreen.TITLE -> return false
            FlowScreen.MODE -> goToTitle()
            FlowScreen.TWO_PLAYER_MODE -> backToMode()
            FlowScreen.DIFFICULTY -> backFromDifficulty()
            FlowScreen.LEVEL_SELECT -> returnToDifficulty()
            FlowScreen.NOTICE -> backFromNotice()
            FlowScreen.CURTAIN -> cancelCurtain()
            FlowScreen.PLAY, FlowScreen.PAUSE -> togglePause()
            FlowScreen.EDITOR -> {
                editor?.back()
                true
            }

            FlowScreen.LAN_LOBBY -> {
                backToTwoPlayerMenu()
                true
            }

            FlowScreen.LAN_HOST_WAIT -> {
                cancelHosting()
                true
            }

            FlowScreen.LAN_JOIN -> {
                leaveLanJoin()
                true
            }

            FlowScreen.LAN_IP -> {
                screenState = FlowScreen.LAN_JOIN
                screenTicks = 0
                edges.lockFire()
                true
            }

            FlowScreen.LAN_CONNECTING -> {
                cancelConnecting()
                true
            }

            FlowScreen.TALLY -> finishTally()
            FlowScreen.GAME_OVER -> proceedAfterGameOver()
            FlowScreen.INITIALS -> saveInitials()
            FlowScreen.HIGH_SCORES -> leaveHighScores()
            FlowScreen.OPTIONS -> leaveOptions()
        }
        if (screenState != previousScreen) tapGuard.arm()
        return true
    }

    /** Activity.onPause: a running game must come back showing the pause menu. */
    @Synchronized
    fun autoPause() {
        if (screenState == FlowScreen.PLAY) togglePause()
    }

    /**
     * A gamepad was added or removed. Like the original, disconnect/connect
     * during a run pauses the game so the player can adapt.
     */
    @Synchronized
    fun onGamepadChanged(connected: Boolean): Boolean {
        val note = if (connected) "GAMEPAD CONNECTED" else "GAMEPAD REMOVED"
        if (screenState == FlowScreen.PAUSE) {
            pauseNote = note
            return true
        }
        if (screenState != FlowScreen.PLAY) return false
        if (!togglePause()) return false
        pauseNote = note
        return true
    }

    /** The audio engine was recreated after a lifecycle pause; restore menu music. */
    @Synchronized
    fun onResumeAudio() {
        tapGuard.reset()
        edges.reset()
        lan.resetTimeouts()
        if (titleMusicOn) audio?.playTitle()
    }

    /** View teardown: stop LAN sockets and NSD before the process goes away. */
    @Synchronized
    fun close() {
        stopLanSilently()
        lan.stop()
        discovery?.close()
    }

    @Synchronized
    fun onTap(x: Int, y: Int): Boolean {
        if (tapGuard.blocks()) return true
        val previousScreen = screenState

        val handled = when (screenState) {
            FlowScreen.TITLE -> {
                if (screenTicks > TITLE_GUARD_TICKS) openMode()
                true
            }

            FlowScreen.MODE -> {
                val hit = tapMenu(MENU_TOP_MODE, MENU_ROW_MODE, modeMenu, x, y)
                if (hit) activateMode(modeMenu.selected)
                hit
            }

            FlowScreen.TWO_PLAYER_MODE -> {
                val hit = tapMenu(
                    MENU_TOP_MODE,
                    MENU_ROW_MODE,
                    twoPlayerMenu,
                    x,
                    y,
                )
                if (hit) activateTwoPlayerMode(twoPlayerMenu.selected)
                hit
            }

            FlowScreen.DIFFICULTY -> {
                val hit = tapMenu(
                    MENU_TOP_DIFFICULTY,
                    MENU_ROW_DIFFICULTY,
                    difficultyMenu,
                    x,
                    y,
                )
                if (hit) activateDifficulty(difficultyMenu.selected)
                hit
            }

            FlowScreen.LEVEL_SELECT -> tapLevelSelect(x, y)
            FlowScreen.NOTICE -> {
                backFromNotice()
                true
            }

            FlowScreen.CURTAIN -> {
                beginPlay()
                true
            }

            FlowScreen.PLAY -> false
            FlowScreen.PAUSE -> {
                val menu = activePauseMenu()
                val hit = tapMenu(PAUSE_TOP, PAUSE_ROW, menu, x, y)
                if (hit) activatePause(menu.selected)
                hit
            }

            FlowScreen.TALLY -> {
                if (screenTicks > TALLY_GUARD_TICKS && NEXT_RECT.contains(x, y)) {
                    finishTally()
                    true
                } else {
                    false
                }
            }

            FlowScreen.GAME_OVER -> {
                if (screenTicks > GAME_OVER_GUARD_TICKS && CONTINUE_RECT.contains(x, y)) {
                    proceedAfterGameOver()
                    true
                } else {
                    false
                }
            }

            FlowScreen.INITIALS -> tapInitials(x, y)
            FlowScreen.HIGH_SCORES -> tapHighScores(x, y)
            FlowScreen.OPTIONS -> tapOptions(x, y)
            FlowScreen.EDITOR -> editor?.tap(x, y) ?: false
            FlowScreen.LAN_LOBBY -> {
                val hit = tapMenu(MENU_TOP_MODE, MENU_ROW_MODE, lanLobbyMenu, x, y)
                if (hit) activateLanLobby(lanLobbyMenu.selected)
                hit
            }

            FlowScreen.LAN_HOST_WAIT -> {
                if (BACK_RECT.contains(x, y)) {
                    cancelHosting()
                    true
                } else {
                    false
                }
            }

            FlowScreen.LAN_JOIN -> tapLanJoin(x, y)
            FlowScreen.LAN_IP -> tapLanIp(x, y)
            FlowScreen.LAN_CONNECTING -> {
                if (BACK_RECT.contains(x, y)) {
                    cancelConnecting()
                    true
                } else {
                    false
                }
            }
        }

        if (handled) edges.lockFire()
        if (screenState != previousScreen) tapGuard.arm()
        return handled
    }

    /** Continues an editor stroke while the finger moves. */
    @Synchronized
    fun onEditorDrag(x: Int, y: Int) {
        if (screenState == FlowScreen.EDITOR) editor?.drag(x, y)
    }

    /** Ends an editor stroke. */
    @Synchronized
    fun onEditorRelease() {
        editor?.endDrag()
    }

    /** Debug-only power-up row, active during play and pause. */
    @Synchronized
    fun debugSpawnPowerUp(type: PowerUpType) {
        playScene?.debugSpawnPowerUp(type)
    }

    // ------------------------------------------------------------------- LAN

    private fun openLanLobby() {
        lan.stop()
        discovery?.stopDiscovery()
        discovery?.unregister()
        lanError = null
        lanHostStage = false
        lanHadGuest = false
        lanGuestLevel = -1
        peerPauseLast = false
        prevSnapshot = null
        currSnapshot = null
        singleLevel = false
        players = 1
        screenState = FlowScreen.LAN_LOBBY
        screenTicks = 0
        lanLobbyMenu.select(0)
        edges.lockFire()
    }

    private fun activateLanLobby(index: Int) {
        when (index) {
            0 -> startHosting()
            1 -> openLanJoin()
            else -> backToTwoPlayerMenu()
        }
        edges.lockFire()
    }

    private fun backToTwoPlayerMenu() {
        lan.end("LEFT")
        discovery?.stopDiscovery()
        discovery?.unregister()
        lanHostStage = false
        screenState = FlowScreen.TWO_PLAYER_MODE
        screenTicks = 0
        edges.lockFire()
    }

    private fun startHosting() {
        lan.stop()
        if (!lan.startHost()) {
            lanError = lan.lastError ?: "PORT BUSY"
            screenState = FlowScreen.LAN_LOBBY
            screenTicks = 0
            return
        }
        discovery?.unregister()
        discovery?.register(lan.hostPort)
        lanHostIp = lanIpLabel()
        lanError = null
        lanHostStage = false
        lanHadGuest = false
        screenState = FlowScreen.LAN_HOST_WAIT
        screenTicks = 0
        edges.lockFire()
    }

    private fun cancelHosting() {
        lan.end("HOST LEFT")
        discovery?.unregister()
        lanHostStage = false
        lanHadGuest = false
        screenState = FlowScreen.LAN_LOBBY
        screenTicks = 0
        edges.lockFire()
    }

    private fun openLanJoin() {
        lan.stop()
        discovery?.startDiscovery()
        lanError = null
        ipError = false
        lanJoinGames = discovery?.games().orEmpty()
        lanJoinFocus = 0
        screenState = FlowScreen.LAN_JOIN
        screenTicks = 0
        edges.lockFire()
    }

    private fun leaveLanJoin() {
        lan.stop()
        discovery?.stopDiscovery()
        screenState = FlowScreen.LAN_LOBBY
        screenTicks = 0
        edges.lockFire()
    }

    private fun lanJoinCount(): Int = lanJoinGames.size + 2

    private fun refreshLanJoinGames() {
        lanJoinGames = discovery?.games().orEmpty().take(LAN_JOIN_ROWS)
        lanJoinFocus = lanJoinFocus.coerceIn(0, lanJoinCount() - 1)
    }

    private fun handleLanJoin(frame: InputEdgesFrame) {
        refreshLanJoinGames()
        when (frame.direction) {
            Dir.UP -> lanJoinFocus = (lanJoinFocus - 1).coerceAtLeast(0)
            Dir.DOWN -> lanJoinFocus = (lanJoinFocus + 1).coerceAtMost(lanJoinCount() - 1)
            else -> Unit
        }
        if (frame.fire) {
            edges.lockFire()
            activateLanJoin(lanJoinFocus)
        }
    }

    private fun activateLanJoin(index: Int) {
        when {
            index < lanJoinGames.size -> startJoining(
                lanJoinGames[index].host,
                lanJoinGames[index].port,
            )
            index == lanJoinGames.size -> {
                ipError = false
                screenState = FlowScreen.LAN_IP
                screenTicks = 0
                edges.lockFire()
            }

            else -> leaveLanJoin()
        }
    }

    private fun startJoining(address: String, targetPort: Int = lan.hostPort) {
        lan.stop()
        discovery?.stopDiscovery()
        lanAddress = address
        lanError = null
        lan.startJoin(address, targetPort)
        ipError = false
        screenState = FlowScreen.LAN_CONNECTING
        screenTicks = 0
        edges.lockFire()
    }

    private fun handleLanIp(frame: InputEdgesFrame) {
        when (frame.direction) {
            Dir.UP -> ipEntry.move(0, -1)
            Dir.DOWN -> ipEntry.move(0, 1)
            Dir.LEFT -> ipEntry.move(-1, 0)
            Dir.RIGHT -> ipEntry.move(1, 0)
            else -> Unit
        }
        if (frame.fire) confirmIp()
    }

    private fun confirmIp() {
        when (ipEntry.select()) {
            IpEntry.Action.CONFIRM -> validateAndJoin()
            else -> ipError = false
        }
        edges.lockFire()
    }

    private fun validateAndJoin() {
        val address = ipEntry.result()
        if (address == null) {
            ipError = true
            return
        }
        ipError = false
        startJoining(address)
    }

    private fun handleLanConnecting(frame: InputEdgesFrame) {
        if (lan.guestState() != LanClient.State.CONNECTED) {
            lan.sendInput(PlayerInput.NONE)
        }
        val welcome = lan.guestWelcome()
        if (welcome != null) {
            startGuestGame(welcome)
            return
        }
        if (lan.guestTimedOut()) {
            lanError = "TIMED OUT"
        } else if (lan.guestState() == LanClient.State.FAILED) {
            lanError = lan.guestFailure() ?: "CANNOT CONNECT"
        } else if (lan.guestState() == LanClient.State.CLOSED) {
            lanError = lan.guestBye() ?: "CONNECTION CLOSED"
        }
        if (frame.fire && lanError != null) cancelConnecting()
    }

    private fun cancelConnecting() {
        lan.end("LEFT")
        discovery?.startDiscovery()
        lanError = null
        lanJoinGames = discovery?.games().orEmpty()
        screenState = FlowScreen.LAN_JOIN
        screenTicks = 0
        edges.lockFire()
    }

    private fun startGuestGame(welcome: NetMessage.Welcome) {
        lanGuestLevel = welcome.level
        lanSeed = welcome.seed
        lanDifficulty = Difficulty.entries.getOrElse(welcome.difficulty) {
            Difficulty.NORMAL
        }
        peerPauseLast = false
        prevSnapshot = null
        currSnapshot = null
        createGuestLevel(welcome.level)
        titleMusicOn = false
        audio?.stopMusic()
        audio?.playLevelStart()
        screenState = FlowScreen.PLAY
        screenTicks = 0
        resultTicks = 0
        lanError = null
        edges.lockFire()
        tapGuard.arm()
    }

    private fun createGuestLevel(levelNumber: Int) {
        val index = Math.floorMod(levelNumber - 1, levels.size)
        val guest = GameSession(
            levels = levels,
            seed = lanSeed,
            difficulty = lanDifficulty,
            startLevelIndex = index,
            playerCount = 2,
        )
        session = guest
        playScene = PlayScene(guest, atlas, font, audio, remote = true)
        levelStartScores.fill(0)
        tally = null
        tally2p = null
        resultTicks = 0
    }

    private fun pumpLanGuest(input: PlayerInput) {
        if (lan.guestWelcome() == null) return
        try {
            val now = System.currentTimeMillis()
            val batch = lan.drainGuestSnapshots()
            var arrivedAt = now - (batch.size - 1) * SNAPSHOT_INTERVAL_MS
            for (snapshot in batch) {
                if (snapshot.state.levelNumber != lanGuestLevel) {
                    lanGuestLevel = snapshot.state.levelNumber
                    createGuestLevel(lanGuestLevel)
                    prevSnapshot = null
                    currSnapshot = null
                    audio?.playLevelStart()
                }
                val scene = playScene ?: return
                lan.applyTerrain(scene.session.world.terrain, snapshot.terrain)
                prevSnapshot = currSnapshot
                prevArrivedAt = currArrivedAt
                currSnapshot = snapshot.state
                currArrivedAt = arrivedAt
                arrivedAt += SNAPSHOT_INTERVAL_MS
                lan.ackGuest(snapshot.seq)
            }

            val scene = playScene ?: return
            val state = interpolatedState(now) ?: currSnapshot
            if (state != null) scene.session.world.applySnapshot(state)
            if (screenState == FlowScreen.PLAY) {
                val events = lan.drainGuestEvents()
                if (events.isNotEmpty()) scene.session.world.events.addAll(events)
                scene.update(PlayerInput.NONE, PlayerInput.NONE)
            } else {
                lan.drainGuestEvents()
            }
            // Heartbeat even while paused, but never move the tank from menu keys.
            lan.sendInput(if (screenState == FlowScreen.PLAY) input else PlayerInput.NONE)
        } catch (_: RuntimeException) {
            endLanSession("BAD SNAPSHOT", FlowScreen.LAN_LOBBY)
            return
        }

        val bye = lan.guestBye()
        if (bye != null) {
            endLanSession(bye, FlowScreen.LAN_LOBBY)
            return
        }
        if (lan.guestTimedOut()) {
            endLanSession("TIMEOUT", FlowScreen.LAN_LOBBY)
            return
        }
        if (lan.guestState() == LanClient.State.FAILED) {
            endLanSession(lan.guestFailure() ?: "CONNECTION FAILED", FlowScreen.LAN_LOBBY)
            return
        }
        if (lan.guestState() == LanClient.State.CLOSED) {
            endLanSession("CONNECTION LOST", FlowScreen.LAN_LOBBY)
            return
        }

        syncRemotePause(lan.guestRemotePaused())
    }

    private fun interpolatedState(now: Long): WorldSnapshot? {
        val previous = prevSnapshot ?: return currSnapshot
        val current = currSnapshot ?: return null
        if (previous.levelNumber != current.levelNumber) return current
        val span = (currArrivedAt - prevArrivedAt).coerceAtLeast(1L)
        val alpha = ((now - currArrivedAt).toFloat() / span).coerceIn(0f, 1f)
        return interpolateSnapshots(previous, current, alpha)
    }

    /**
     * Applies a peer pause and echoes it so both sides can release it. A
     * transition that arrives while another screen (options, tally) is open is
     * not latched, so it is applied when play or pause is visible again.
     */
    private fun syncRemotePause(peerPaused: Boolean) {
        if (peerPaused == peerPauseLast) return
        if (screenState != FlowScreen.PLAY && screenState != FlowScreen.PAUSE) return
        peerPauseLast = peerPaused
        if (peerPaused && screenState == FlowScreen.PLAY) {
            enterRemotePause()
        } else if (!peerPaused && screenState == FlowScreen.PAUSE) {
            leaveRemotePause()
        }
        lan.sendPause(peerPaused)
    }

    private fun pumpLanHost() {
        if (lan.role != LanRole.HOST) return
        val world = session?.world
        if (world != null) lan.publish(world)

        if (screenState == FlowScreen.LAN_HOST_WAIT && lan.isHostConnected()) {
            lanHostStage = true
            lanHadGuest = true
            difficultyMenu.select(selectedDifficulty.ordinal)
            screenState = FlowScreen.DIFFICULTY
            screenTicks = 0
            edges.lockFire()
            return
        }

        if (session != null && lan.hostConnectionCount() != lanSessionConnection) {
            endLanSession("PLAYER CHANGED", FlowScreen.LAN_LOBBY)
            return
        }

        syncRemotePause(lan.hostRemotePaused())

        if (lan.hostTimedOut()) {
            endLanSession("TIMEOUT", FlowScreen.LAN_LOBBY)
            return
        }
        if (!lan.isHostConnected() && lanHadGuest) {
            when (screenState) {
                FlowScreen.PLAY,
                FlowScreen.PAUSE,
                FlowScreen.CURTAIN,
                FlowScreen.TALLY,
                -> endLanSession(
                    lan.hostByeReason() ?: "PLAYER LEFT",
                    FlowScreen.LAN_LOBBY,
                )

                FlowScreen.GAME_OVER -> stopLanSilently()

                else -> if (lanHostStage) {
                    lanHostStage = false
                    peerPauseLast = false
                    showNotice(
                        "PLAYER LEFT",
                        "WAITING FOR PLAYER 2",
                        FlowScreen.LAN_HOST_WAIT,
                        hint = "HOST LOBBY",
                    )
                }
            }
        }
        lanHadGuest = lan.isHostConnected()
    }

    private fun startLanHostGame(levelNumber: Int) {
        lanHostStage = false
        lan.requestFullTerrain()
        lan.sendWelcome(levelNumber, selectedDifficulty.ordinal)
        lanHadGuest = true
        peerPauseLast = false
        lanSessionConnection = lan.hostConnectionCount()
        pauseNote = null
        beginSession(
            levels,
            (levelNumber - 1).coerceIn(0, levels.size - 1),
            single = false,
        )
    }

    private fun enterRemotePause() {
        if (screenState != FlowScreen.PLAY) return
        pauseNote = null
        screenState = FlowScreen.PAUSE
        screenTicks = 0
        pauseMenu.select(0)
        testPauseMenu.select(0)
        audio?.setEngine(false, false)
        audio?.stopMusic()
        audio?.playPause()
        edges.lockFire()
        tapGuard.arm()
    }

    private fun leaveRemotePause() {
        if (screenState != FlowScreen.PAUSE) return
        pauseNote = null
        screenState = FlowScreen.PLAY
        screenTicks = 0
        edges.lockFire()
        tapGuard.arm()
    }

    private fun endLanSession(reason: String, back: FlowScreen) {
        lan.end(reason)
        discovery?.unregister()
        discovery?.stopDiscovery()
        session = null
        playScene = null
        resultTicks = 0
        lanHostStage = false
        lanHadGuest = false
        lanGuestLevel = -1
        peerPauseLast = false
        prevSnapshot = null
        currSnapshot = null
        tally = null
        tally2p = null
        showNotice(
            "CONNECTION LOST",
            reason.take(28),
            back,
            hint = "BACK TO LOBBY",
        )
    }

    private fun stopLanSilently() {
        if (lan.role != LanRole.NONE) {
            lan.end("GAME ENDED")
            discovery?.unregister()
            discovery?.stopDiscovery()
        }
        lanHostStage = false
        lanHadGuest = false
        lanGuestLevel = -1
        peerPauseLast = false
        prevSnapshot = null
        currSnapshot = null
    }

    private fun lanIpLabel(): String {
        val address = discovery?.localIpv4() ?: return "NO WI-FI ADDRESS"
        return "$address:${lan.hostPort}"
    }

    private fun tapLanJoin(x: Int, y: Int): Boolean {
        refreshLanJoinGames()
        for (index in lanJoinGames.indices) {
            if (!lanJoinRowRect(index).contains(x, y)) continue
            lanJoinFocus = index
            activateLanJoin(index)
            return true
        }
        if (LAN_ENTER_IP_RECT.contains(x, y)) {
            lanJoinFocus = lanJoinGames.size
            activateLanJoin(lanJoinGames.size)
            return true
        }
        if (LAN_BACK_RECT.contains(x, y)) {
            lanJoinFocus = lanJoinGames.size + 1
            activateLanJoin(lanJoinFocus)
            return true
        }
        return false
    }

    private fun tapLanIp(x: Int, y: Int): Boolean {
        if (LAN_BACK_RECT.contains(x, y)) {
            screenState = FlowScreen.LAN_JOIN
            screenTicks = 0
            edges.lockFire()
            return true
        }
        for (index in ipEntry.cells.indices) {
            if (!ipCellRect(index).contains(x, y)) continue
            if (ipEntry.selectCell(index) == IpEntry.Action.CONFIRM) {
                validateAndJoin()
            } else {
                ipError = false
            }
            edges.lockFire()
            return true
        }
        return false
    }

    private fun drawLanLobby(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "LOCAL NETWORK", 12, YELLOW)
        drawMenu(canvas, lanLobbyMenu, MENU_TOP_MODE, MENU_ROW_MODE)
        val error = lanError
        if (error != null) {
            drawCentered(canvas, error.take(28), 188, YELLOW)
        } else {
            drawCentered(canvas, "SAME WI-FI OR HOTSPOT", 188, GREY)
        }
        drawCentered(canvas, "D-PAD FIRE OR TAP", 208, GREY)
    }

    private fun drawLanHostWait(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "HOSTING", 20, YELLOW)
        drawCentered(canvas, "WAITING FOR PLAYER 2", 44, WHITE)
        val blink = (screenTicks / 20) % 2 == 0
        drawCentered(canvas, if (blink) "..." else " ", 62, YELLOW)
        drawCentered(canvas, lanHostIp, 92, WHITE)
        drawCentered(canvas, "JOIN: ENTER THIS IP", 112, GREY)
        drawCentered(canvas, "OR PICK THE GAME LIST", 126, GREY)
        val error = discovery?.registrationError
        if (error != null) drawCentered(canvas, "LIST UNSUPPORTED - USE IP", 146, DARK)
        drawButton(canvas, BACK_RECT, "CANCEL")
    }

    private fun drawLanJoin(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "JOIN GAME", 12, YELLOW)
        refreshLanJoinGames()
        if (lanJoinGames.isEmpty()) {
            drawCentered(canvas, "SEARCHING...", 60, GREY)
        } else {
            for (index in lanJoinGames.indices.take(LAN_JOIN_ROWS)) {
                val service = lanJoinGames[index]
                val rect = lanJoinRowRect(index)
                if (index == lanJoinFocus) {
                    fillPaint.color = SLOT_SELECTED
                    canvas.drawRect(rect, fillPaint)
                    strokePaint.color = YELLOW
                    canvas.drawRect(rect, strokePaint)
                }
                drawText(
                    canvas,
                    service.name.take(14),
                    rect.left + 4,
                    rect.top + 4,
                    if (index == lanJoinFocus) YELLOW else WHITE,
                )
                drawRight(canvas, service.host, rect.right - 4, rect.top + 4, GREY)
            }
        }
        drawButton(
            canvas,
            LAN_ENTER_IP_RECT,
            "ENTER IP",
            selected = lanJoinFocus == lanJoinGames.size,
        )
        drawButton(
            canvas,
            LAN_BACK_RECT,
            "BACK",
            selected = lanJoinFocus == lanJoinGames.size + 1,
        )
        val error = lanError ?: discovery?.discoveryError
        drawCentered(canvas, error?.take(28) ?: "D-PAD FIRE OR TAP", 202, if (error != null) YELLOW else GREY)
    }

    private fun drawLanIp(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "HOST IP", 12, YELLOW)
        drawCentered(canvas, ipEntry.text.ifEmpty { "..." }, 32, WHITE)
        for (index in ipEntry.cells.indices) {
            val rect = ipCellRect(index)
            fillPaint.color = CELL_FILL
            canvas.drawRect(rect, fillPaint)
            strokePaint.color = if (index == ipEntry.cursor) YELLOW else BORDER
            canvas.drawRect(rect, strokePaint)
            val label = ipEntry.cells[index]
            drawText(
                canvas,
                label,
                rect.centerX() - font.width(label) / 2,
                rect.top + 4,
                if (index == ipEntry.cursor) YELLOW else WHITE,
            )
        }
        if (ipError) {
            drawCentered(canvas, "INVALID IP", 150, YELLOW)
        } else {
            drawCentered(canvas, "TYPE THE HOST ADDRESS", 150, GREY)
        }
        drawCentered(canvas, "OK CONNECTS", 164, GREY)
        drawButton(canvas, LAN_BACK_RECT, "BACK")
    }

    private fun drawLanConnecting(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "JOIN GAME", 20, YELLOW)
        drawCentered(canvas, lanAddress, 48, WHITE)
        val state = lan.guestState()
        val status = when {
            lanError != null -> lanError.orEmpty()
            state == LanClient.State.CONNECTING -> "CONNECTING..."
            state == LanClient.State.HANDSHAKE -> "WAITING FOR HOST..."
            state == LanClient.State.CONNECTED -> "STARTING..."
            state == LanClient.State.CLOSED -> "CONNECTION CLOSED"
            else -> "CONNECTING..."
        }
        drawCentered(canvas, status.take(28), 76, if (lanError != null) YELLOW else GREY)
        drawCentered(canvas, "HOST MUST PICK A STAGE", 100, DARK)
        val dots = ".".repeat((screenTicks / 15) % 4)
        drawCentered(canvas, dots, 120, YELLOW)
        drawButton(canvas, BACK_RECT, "BACK")
    }

    private fun lanJoinRowRect(index: Int): Rect {
        val top = LAN_JOIN_TOP + index * LAN_JOIN_ROW
        return Rect(16, top - 2, 240, top + 12)
    }

    private fun ipCellRect(index: Int): Rect {
        val column = index % IP_COLUMNS
        val row = index / IP_COLUMNS
        val left = IP_LEFT + column * IP_CELL
        val top = IP_TOP + row * IP_ROW
        return Rect(left, top, left + IP_CELL - 2, top + IP_ROW - 2)
    }

    private fun handleMenu(
        menu: Menu,
        frame: InputEdgesFrame,
        activate: (Int) -> Unit,
    ) {
        when (frame.direction) {
            Dir.UP -> menu.move(-1)
            Dir.DOWN -> menu.move(1)
            else -> Unit
        }
        if (frame.fire) {
            val selected = menu.selected
            edges.lockFire()
            activate(selected)
        }
    }

    private fun handleLevelSelect(frame: InputEdgesFrame) {
        if (levelTabFocused) {
            when (frame.direction) {
                Dir.LEFT, Dir.RIGHT -> selectLevelTab(
                    if (levelTab == LevelTab.CAMPAIGN) LevelTab.USER else LevelTab.CAMPAIGN,
                )

                Dir.DOWN -> levelTabFocused = false
                else -> Unit
            }
            if (frame.fire) {
                levelTabFocused = false
                edges.lockFire()
            }
            return
        }

        if (levelBackFocused) {
            if (frame.direction == Dir.UP ||
                frame.direction == Dir.LEFT ||
                frame.direction == Dir.RIGHT
            ) {
                levelBackFocused = false
            }
            if (frame.fire) {
                edges.lockFire()
                returnToDifficulty()
            }
            return
        }

        if (levelTab == LevelTab.USER) {
            handleUserLevels(frame)
            return
        }

        val row = (grid.selected - grid.pageStart) / GRID_COLUMNS
        when (frame.direction) {
            Dir.LEFT -> grid.move(-1, 0)
            Dir.RIGHT -> grid.move(1, 0)
            Dir.UP -> if (row == 0) levelTabFocused = true else grid.move(0, -1)
            Dir.DOWN -> {
                val noLowerStage = grid.availableCount <= grid.selected + GRID_COLUMNS
                if (row == GRID_ROWS - 1 || noLowerStage) {
                    levelBackFocused = true
                } else {
                    grid.move(0, 1)
                }
            }

            else -> Unit
        }
        if (frame.fire) {
            edges.lockFire()
            activateSelectedLevel()
        }
    }

    private fun handleUserLevels(frame: InputEdgesFrame) {
        val pager = UserLevelPager(userList.size, USER_PAGE_SIZE)
        when (frame.direction) {
            Dir.UP -> if (userSelected == 0) levelTabFocused = true else userSelected--
            Dir.DOWN ->
                if (userSelected >= pager.lastIndex) levelBackFocused = true else userSelected++
            Dir.LEFT -> userSelected = pager.previousPage(userSelected)
            Dir.RIGHT -> userSelected = pager.nextPage(userSelected)
            else -> Unit
        }
        if (frame.fire) {
            edges.lockFire()
            activateSelectedLevel()
        }
    }

    private fun activateSelectedLevel() {
        if (lanHostStage) {
            if (lan.role == LanRole.HOST) startLanHostGame(grid.levelNumber)
            return
        }
        if (levelTab == LevelTab.USER) {
            userList.getOrNull(userSelected)?.let(::startUserLevel)
        } else {
            startCampaign(grid.levelNumber)
        }
    }

    private fun selectLevelTab(tab: LevelTab) {
        if (lanHostStage && tab == LevelTab.USER) return
        levelTab = tab
        if (tab == LevelTab.USER) refreshUserLevels()
    }

    private fun updatePlay(
        input: PlayerInput,
        second: PlayerInput,
        frame: InputEdgesFrame,
    ) {
        val scene = playScene ?: return
        val locked = playInputLock > 0
        if (locked) playInputLock--
        val first = if (locked) PlayerInput.NONE else input
        val remote = if (lan.role == LanRole.HOST) lan.guestInput() else second
        val other = if (locked) PlayerInput.NONE else remote
        scene.update(first, other)

        when (scene.session.world.state) {
            WorldState.PLAYING -> resultTicks = 0

            WorldState.LEVEL_CLEAR -> {
                if (resultTicks == 0) resultTicks = CLEAR_DELAY_TICKS
                resultTicks--
                val canSkip = !locked && frame.fire &&
                    resultTicks < CLEAR_DELAY_TICKS - SKIP_GUARD_TICKS
                if (resultTicks <= 0 || canSkip) {
                    if (returnToEditor) leaveEditorTest() else enterTally()
                }
            }

            WorldState.GAME_OVER -> {
                if (resultTicks == 0) resultTicks = GAME_OVER_DELAY_TICKS
                resultTicks--
                val canSkip = !locked && frame.fire &&
                    resultTicks < GAME_OVER_DELAY_TICKS - SKIP_GUARD_TICKS
                if (resultTicks <= 0 || canSkip) {
                    when {
                        returnToEditor -> leaveEditorTest()
                        singleLevel -> leaveSingleLevel()
                        else -> enterGameOver()
                    }
                }
            }
        }
    }

    private fun openMode() {
        screenState = FlowScreen.MODE
        screenTicks = 0
        modeMenu.select(0)
        edges.lockFire()
    }

    private fun goToTitle() {
        stopLanSilently()
        session = null
        playScene = null
        players = 1
        returnToEditor = false
        singleLevel = false
        playInputLock = 0
        levelBackFocused = false
        tally = null
        tally2p = null
        gameOverScores = null
        pauseNote = null
        screenState = FlowScreen.TITLE
        screenTicks = 0
        if (!titleMusicOn) {
            titleMusicOn = true
            audio?.stopMusic()
            audio?.playTitle()
        }
        edges.lockFire()
    }

    private fun backToMode() {
        // Leaving the two-player branch restores the one-player touch layout.
        players = 1
        screenState = FlowScreen.MODE
        screenTicks = 0
        edges.lockFire()
    }

    /** Difficulty was reached from the one- or two-player menu. */
    private fun backFromDifficulty() {
        if (lanHostStage) {
            cancelHosting()
            return
        }
        screenState = if (players == 2) FlowScreen.TWO_PLAYER_MODE else FlowScreen.MODE
        screenTicks = 0
        edges.lockFire()
    }

    private fun backFromNotice() {
        screenState = noticeBack
        screenTicks = 0
        edges.lockFire()
    }

    private fun activateMode(index: Int) {
        when (index) {
            0 -> {
                players = 1
                difficultyMenu.select(selectedDifficulty.ordinal)
                screenState = FlowScreen.DIFFICULTY
                screenTicks = 0
            }

            1 -> {
                twoPlayerMenu.select(0)
                screenState = FlowScreen.TWO_PLAYER_MODE
                screenTicks = 0
            }

            2 -> openEditor()
            3 -> openHighScores(FlowScreen.MODE, selectedDifficulty)
            4 -> openOptions(FlowScreen.MODE)
            5 -> goToTitle()
        }
        edges.lockFire()
    }

    private fun activateTwoPlayerMode(index: Int) {
        when (index) {
            0 -> {
                players = 2
                difficultyMenu.select(selectedDifficulty.ordinal)
                screenState = FlowScreen.DIFFICULTY
                screenTicks = 0
            }

            1 -> openLanLobby()
            2 -> backToMode()
        }
        edges.lockFire()
    }

    private fun activateDifficulty(index: Int) {
        if (index >= Difficulty.entries.size) {
            backFromDifficulty()
            return
        }
        selectedDifficulty = Difficulty.entries[index]
        highScoresDifficulty = selectedDifficulty
        levelTab = LevelTab.CAMPAIGN
        refreshGrid()
        levelBackFocused = false
        screenState = FlowScreen.LEVEL_SELECT
        screenTicks = 0
        edges.lockFire()
    }

    private fun returnToDifficulty() {
        levelBackFocused = false
        screenState = FlowScreen.DIFFICULTY
        screenTicks = 0
        edges.lockFire()
    }

    private fun activePauseMenu(): Menu =
        if (returnToEditor) testPauseMenu else pauseMenu

    private fun activatePause(index: Int) {
        if (returnToEditor) {
            when (index) {
                0 -> togglePause()
                else -> leaveEditorTest()
            }
            return
        }
        when (index) {
            0 -> togglePause()
            1 -> openOptions(FlowScreen.PAUSE)
            2 -> enterTitle()
        }
    }

    private fun showNotice(
        title: String,
        body: String,
        back: FlowScreen = FlowScreen.MODE,
        hint: String = "NOT AVAILABLE YET",
    ) {
        noticeTitle = title
        noticeBody = body
        noticeBack = back
        noticeHint = hint
        screenState = FlowScreen.NOTICE
        screenTicks = 0
        edges.lockFire()
    }

    private fun refreshGrid() {
        grid = LevelGrid(levels.size, prefs.maxUnlocked(selectedDifficulty))
        refreshUserLevels()
        levelTabFocused = false
    }

    private fun refreshUserLevels() {
        userList = userStore?.list().orEmpty()
            .filter { LevelValidator.validate(it.level).isValid }
        userSelected = userSelected.coerceIn(0, (userList.size - 1).coerceAtLeast(0))
    }

    private fun startCampaign(levelNumber: Int) {
        pauseNote = null
        val startIndex = (levelNumber - 1).coerceIn(0, levels.size - 1)
        beginSession(levels, startIndex, single = false)
        prefs.unlockLevel(selectedDifficulty, session?.levelNumber ?: levelNumber)
    }

    private fun startUserLevel(userLevel: UserLevel) {
        val validation = LevelValidator.validate(userLevel.level)
        if (!validation.isValid) {
            showNotice(
                "LEVEL BROKEN",
                EditorMessages.friendly(validation.errors).first().take(28),
                FlowScreen.LEVEL_SELECT,
                hint = "FIX IT IN CONSTRUCTION",
            )
            return
        }
        pauseNote = null
        playedUserIndex = userSelected
        beginSession(listOf(userLevel.level), 0, single = true)
    }

    private fun beginSession(
        levelList: List<Level>,
        startIndex: Int,
        single: Boolean,
    ) {
        val sessionPlayers = if (lan.role == LanRole.NONE) players else 2
        session = GameSession(
            levels = levelList,
            seed = LAN_SEED,
            difficulty = selectedDifficulty,
            startLevelIndex = startIndex,
            playerCount = sessionPlayers,
        )
        playScene = null
        resultTicks = 0
        levelStartScores.fill(0)
        tally = null
        tally2p = null
        gameOverScores = null
        singleLevel = single
        returnToEditor = false
        playInputLock = 0
        titleMusicOn = false
        audio?.stopMusic()
        audio?.playLevelStart()
        screenState = FlowScreen.CURTAIN
        screenTicks = 0
        edges.lockFire()
    }

    private fun beginPlay() {
        val current = session ?: return
        val scene = PlayScene(current, atlas, font, audio)
        if (lan.role == LanRole.HOST) scene.eventSink = { lan.sendEvent(it) }
        playScene = scene
        screenState = FlowScreen.PLAY
        screenTicks = 0
        resultTicks = 0
        edges.lockFire()
    }

    private fun cancelCurtain() {
        if (lan.role != LanRole.NONE) return
        session = null
        playScene = null
        resultTicks = 0
        singleLevel = false
        returnToEditor = false
        playInputLock = 0
        titleMusicOn = true
        audio?.stopMusic()
        audio?.playTitle()
        refreshGrid()
        levelBackFocused = false
        screenState = FlowScreen.LEVEL_SELECT
        screenTicks = 0
        edges.lockFire()
    }

    private fun enterTally() {
        val current = session ?: return
        current.syncFromWorld()
        val world = current.world
        if (current.playerCount == 2) {
            val summary = TallySummary2P.from(
                killsP1 = world.killsOf(0),
                scoreP1 = world.scoreByTypeOf(0),
                killsP2 = world.killsOf(1),
                scoreP2 = world.scoreByTypeOf(1),
                levelScoreP1 = world.scoreOf(0) - levelStartScores[0],
                levelScoreP2 = world.scoreOf(1) - levelStartScores[1],
            )
            tally2p = summary
            tally = null
            val winner = summary.winner
            if (winner != null) {
                val livesBefore = world.livesOf(winner)
                // Ruling: the tally bonus is paid even to a player with no lives
                // left, so a kill leader can be revived for the next stage.
                world.addScore(winner, TallySummary2P.BONUS)
                current.syncFromWorld()
                if (world.livesOf(winner) > livesBefore) {
                    audio?.onEvent(GameEvent.ExtraLife)
                }
            }
        } else {
            tally = TallySummary.from(
                kills = world.kills,
                scoreByType = world.scoreByType,
                levelScore = world.score - levelStartScores[0],
            )
            tally2p = null
        }
        if (!singleLevel) unlockNextStage(current)
        screenState = FlowScreen.TALLY
        screenTicks = 0
        edges.lockFire()
    }

    private fun unlockNextStage(current: GameSession) {
        val next = StageProgress.nextStage(current.levelNumber, levels.size)
        prefs.unlockLevel(current.difficulty, next)
    }

    private fun finishTally() {
        val current = session ?: return
        if (singleLevel) {
            leaveSingleLevel()
            return
        }
        current.nextLevel()
        if (lan.role == LanRole.HOST) lan.requestFullTerrain()
        for (index in 0 until current.playerCount) {
            levelStartScores[index] = current.scoreOf(index)
        }
        playScene = null
        tally = null
        tally2p = null
        resultTicks = 0
        audio?.playLevelStart()
        screenState = FlowScreen.CURTAIN
        screenTicks = 0
        edges.lockFire()
    }

    private fun leaveSingleLevel() {
        session = null
        playScene = null
        tally = null
        tally2p = null
        resultTicks = 0
        singleLevel = false
        playInputLock = 0
        levelBackFocused = false
        refreshUserLevels()
        userSelected = playedUserIndex.coerceIn(0, (userList.size - 1).coerceAtLeast(0))
        levelTab = LevelTab.USER
        levelTabFocused = false
        screenState = FlowScreen.LEVEL_SELECT
        screenTicks = 0
        edges.lockFire()
    }

    private fun openEditor() {
        players = 1
        val scene = editor ?: EditorScene(atlas, font, this).also { editor = it }
        scene.showField()
        screenState = FlowScreen.EDITOR
        screenTicks = 0
        levelTabFocused = false
        edges.lockFire()
    }

    private fun startEditorTest(level: Level) {
        pauseNote = null
        beginSession(listOf(level), 0, single = true)
        returnToEditor = true
        playInputLock = TEST_INPUT_LOCK_TICKS
        screenState = FlowScreen.PLAY
        beginPlay()
    }

    private fun leaveEditorTest() {
        returnToEditor = false
        session = null
        playScene = null
        resultTicks = 0
        singleLevel = false
        screenState = FlowScreen.EDITOR
        screenTicks = 0
        editor?.returnFromTest()
        edges.lockFire()
    }

    // ------------------------------------------------------------ editor host

    override fun userLevels(): List<UserLevel> = userStore?.list().orEmpty()

    override fun saveLevel(fileName: String?, level: Level): EditorScene.SaveResult {
        val store = userStore ?: return EditorScene.SaveResult.Failed("NO STORAGE")
        return runCatching { store.save(fileName, level) }.fold(
            onSuccess = { EditorScene.SaveResult.Saved(it, level.name) },
            onFailure = { EditorScene.SaveResult.Failed(it.message ?: "WRITE FAILED") },
        )
    }

    override fun deleteLevel(fileName: String): Boolean =
        userStore?.delete(fileName) == true

    override fun testLevel(level: Level) {
        startEditorTest(level)
    }

    override fun exitEditor() {
        editor = null
        backToMode()
    }

    private fun enterGameOver() {
        val current = session ?: return
        current.syncFromWorld()
        if (current.playerCount == 2) {
            val scores = IntArray(2) { current.scoreOf(it) }
            gameOverScores = scores
            // One high-score entry per two-player run: the higher score.
            gameOverScore = maxOf(scores[0], scores[1])
        } else {
            gameOverScores = null
            gameOverScore = current.score
        }
        gameOverDifficulty = current.difficulty
        screenState = FlowScreen.GAME_OVER
        screenTicks = 0
        resultTicks = 0
        edges.lockFire()
    }

    private fun proceedAfterGameOver() {
        stopLanSilently()
        if (highScores.qualifies(gameOverDifficulty, gameOverScore)) {
            initials = InitialsEntry()
            screenState = FlowScreen.INITIALS
            screenTicks = 0
        } else {
            highScoresDifficulty = gameOverDifficulty
            highScoresReturn = FlowScreen.TITLE
            screenState = FlowScreen.HIGH_SCORES
            screenTicks = 0
        }
        edges.lockFire()
    }

    private fun handleInitials(frame: InputEdgesFrame) {
        when (frame.direction) {
            Dir.LEFT -> initials.moveSlot(-1)
            Dir.RIGHT -> initials.moveSlot(1)
            Dir.UP -> initials.changeLetter(1)
            Dir.DOWN -> initials.changeLetter(-1)
            else -> Unit
        }
        if (frame.fire) {
            confirmInitials()
            edges.lockFire()
        }
    }

    private fun confirmInitials() {
        if (!initials.confirm()) return
        saveInitials()
    }

    private fun saveInitials() {
        highScores.add(gameOverDifficulty, initials.text, gameOverScore)
        prefs.saveHighScores(highScores)
        highScoresDifficulty = gameOverDifficulty
        highScoresReturn = FlowScreen.TITLE
        screenState = FlowScreen.HIGH_SCORES
        screenTicks = 0
        edges.lockFire()
    }

    private fun openHighScores(returnScreen: FlowScreen, difficulty: Difficulty) {
        highScoresReturn = returnScreen
        highScoresDifficulty = difficulty
        screenState = FlowScreen.HIGH_SCORES
        screenTicks = 0
        edges.lockFire()
    }

    private fun leaveHighScores() {
        if (highScoresReturn == FlowScreen.TITLE) {
            titleMusicOn = false
            goToTitle()
        } else {
            screenState = highScoresReturn
            screenTicks = 0
            edges.lockFire()
        }
    }

    private fun handleHighScores(frame: InputEdgesFrame) {
        when (frame.direction) {
            Dir.LEFT -> cycleHighScoreDifficulty(-1)
            Dir.RIGHT -> cycleHighScoreDifficulty(1)
            else -> Unit
        }
        if (frame.fire) {
            edges.lockFire()
            leaveHighScores()
        }
    }

    private fun cycleHighScoreDifficulty(delta: Int) {
        val values = Difficulty.entries
        val index = Math.floorMod(values.indexOf(highScoresDifficulty) + delta, values.size)
        highScoresDifficulty = values[index]
    }

    private fun openOptions(returnScreen: FlowScreen) {
        pauseNote = null
        optionsReturn = returnScreen
        options.select(0)
        screenState = FlowScreen.OPTIONS
        screenTicks = 0
        edges.lockFire()
    }

    private fun leaveOptions() {
        screenState = optionsReturn
        screenTicks = 0
        if (optionsReturn == FlowScreen.PAUSE) pauseMenu.select(0)
        edges.lockFire()
    }

    private fun handleOptions(frame: InputEdgesFrame) {
        when (frame.direction) {
            Dir.UP -> options.move(-1)
            Dir.DOWN -> options.move(1)
            Dir.LEFT -> changeOption(-1)
            Dir.RIGHT -> changeOption(1)
            else -> Unit
        }
        if (frame.fire) {
            val row = options.row
            if (options.activate()) {
                applyAndSaveSettings(row)
            } else {
                leaveOptions()
            }
            edges.lockFire()
        }
    }

    private fun changeOption(direction: Int) {
        val row = options.row
        if (options.change(direction)) applyAndSaveSettings(row)
    }

    private fun applySettings() {
        val engine = audio ?: return
        engine.musicVolume = settings.musicVolume.coerceIn(0, 10) / 10f
        engine.sfxVolume = settings.sfxVolume.coerceIn(0, 10) / 10f
        engine.vibrationEnabled = settings.vibration
    }

    private fun applyAndSaveSettings(changed: OptionRow) {
        prefs.saveSettings(settings)
        applySettings()
        settingsRevision++
        if (changed == OptionRow.SFX) audio?.playPause()
    }

    private fun enterTitle() {
        titleMusicOn = true
        audio?.stopMusic()
        audio?.playTitle()
        goToTitle()
        edges.reset()
    }

    // ---------------------------------------------------------------- drawing

    private fun drawTitle(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawText(canvas, "HI-SCORE", 8, 8, GREY)
        drawRight(canvas, highScores.bestScore().toString().padStart(6, '0'), 248, 8)

        val progress = (screenTicks / 45f).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress)
        val logoY = (224 + (28 - 224) * eased).toInt()
        drawLogo(canvas, brickText.bitmap("STEEL", 4), logoY)
        drawLogo(canvas, brickText.bitmap("EAGLE", 4), logoY + 38)

        if (screenTicks >= 45) {
            val visible = (screenTicks / 20) % 2 == 0
            if (visible) {
                drawCentered(canvas, "PUSH START", 116, WHITE)
                val x = (LOGICAL_WIDTH - font.width("PUSH START")) / 2
                canvas.drawBitmap(tankCursor, (x - 24).toFloat(), 112f, null)
            }
        }

        drawCentered(canvas, "ARCADE ACTION", 168, GREY)
        drawCentered(canvas, "ORIGINAL 2026", 208, DARK)
    }

    private fun drawLogo(canvas: Canvas, logo: Bitmap, y: Int) {
        val x = (LOGICAL_WIDTH - logo.width) / 2
        canvas.drawBitmap(logo, (x + 3).toFloat(), (y + 3).toFloat(), shadowPaint)
        canvas.drawBitmap(logo, x.toFloat(), y.toFloat(), null)
    }

    private fun drawMode(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "SELECT MODE", 12, YELLOW)
        drawMenu(canvas, modeMenu, MENU_TOP_MODE, MENU_ROW_MODE)
        drawCentered(canvas, "D-PAD FIRE OR TAP", 208, GREY)
    }

    private fun drawTwoPlayerMode(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "2 PLAYERS", 12, YELLOW)
        drawMenu(canvas, twoPlayerMenu, MENU_TOP_MODE, MENU_ROW_MODE)
        drawCentered(canvas, "SAME DEVICE OR NETWORK", 208, GREY)
    }

    private fun drawDifficulty(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "DIFFICULTY", 12, YELLOW)
        drawMenu(canvas, difficultyMenu, MENU_TOP_DIFFICULTY, MENU_ROW_DIFFICULTY)
        val focused = Difficulty.entries.getOrElse(difficultyMenu.selected) {
            selectedDifficulty
        }
        drawCentered(canvas, "LIVES ${focused.initialLives}", 178, GREY)
        drawCentered(canvas, "D-PAD FIRE OR TAP", 208, GREY)
    }

    private fun drawLevelSelect(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "SELECT STAGE", 10, YELLOW)
        drawTab(canvas, CAMPAIGN_TAB_RECT, "CAMPAIGN", levelTab == LevelTab.CAMPAIGN)
        drawTab(
            canvas,
            USER_TAB_RECT,
            "MY LEVELS",
            levelTab == LevelTab.USER && !lanHostStage,
        )
        drawCentered(canvas, selectedDifficulty.name, 42, GREY)

        if (levelTab == LevelTab.CAMPAIGN) {
            drawCampaignGrid(canvas)
        } else {
            drawUserLevels(canvas)
        }

        drawButton(canvas, BACK_RECT, "BACK", selected = levelBackFocused)
        if (debug && levelTab == LevelTab.CAMPAIGN) drawButton(canvas, UNLOCK_RECT, "UNLOCK")
        drawCentered(
            canvas,
            if (levelTabFocused) "LEFT RIGHT CHANGES TAB" else "D-PAD SELECTS",
            198,
            GREY,
        )
    }

    private fun drawTab(canvas: Canvas, rect: Rect, label: String, active: Boolean) {
        fillPaint.color = if (active) SLOT_SELECTED else CELL_FILL
        canvas.drawRect(rect, fillPaint)
        strokePaint.color = if (active) YELLOW else BORDER
        strokePaint.strokeWidth = if (active) 2f else 1f
        canvas.drawRect(rect, strokePaint)
        drawText(
            canvas,
            label,
            rect.centerX() - font.width(label) / 2,
            rect.top + 4,
            if (active) YELLOW else GREY,
        )
        strokePaint.strokeWidth = 1f
    }

    private fun drawCampaignGrid(canvas: Canvas) {
        val selectedCell = grid.selected - grid.pageStart
        for (cell in 0 until GRID_PAGE_SIZE) {
            val rect = gridCellRect(cell)
            val stage = grid.pageStart + cell + 1
            val unlocked = stage <= grid.availableCount

            fillPaint.color = if (unlocked) CELL_FILL else LOCKED_FILL
            canvas.drawRect(rect, fillPaint)
            strokePaint.color = when {
                cell == selectedCell -> YELLOW
                unlocked -> BORDER
                else -> DARK
            }
            strokePaint.strokeWidth = if (cell == selectedCell) 2f else 1f
            canvas.drawRect(rect, strokePaint)

            val label = stage.toString().padStart(2, '0')
            val color = when {
                !unlocked -> DARK
                cell == selectedCell -> YELLOW
                else -> WHITE
            }
            drawText(
                canvas,
                label,
                rect.centerX() - font.width(label) / 2,
                rect.centerY() - 4,
                color,
            )
        }

        if (grid.availableCount > GRID_PAGE_SIZE) {
            val pages = (grid.availableCount + GRID_PAGE_SIZE - 1) / GRID_PAGE_SIZE
            drawCentered(canvas, "PAGE ${grid.page + 1}/$pages", 134, GREY)
        }

        if (grid.page > 0) drawButton(canvas, PREV_RECT, "PREV")
        if (grid.page < grid.pageCount - 1) drawButton(canvas, NEXT_PAGE_RECT, "NEXT")
    }

    private fun drawUserLevels(canvas: Canvas) {
        if (userList.isEmpty()) {
            drawCentered(canvas, "NO USER LEVELS", 84, GREY)
            drawCentered(canvas, "CONSTRUCTION MAKES THEM", 100, DARK)
            return
        }

        val pager = UserLevelPager(userList.size, USER_PAGE_SIZE)
        val start = pager.pageStart(userSelected)
        for (row in 0 until USER_PAGE_SIZE) {
            val index = start + row
            val entry = userList.getOrNull(index) ?: break
            val rect = userRowRect(row)
            val selected = index == userSelected
            if (selected) {
                fillPaint.color = SLOT_SELECTED
                canvas.drawRect(rect, fillPaint)
                strokePaint.color = YELLOW
                strokePaint.strokeWidth = 2f
                canvas.drawRect(rect, strokePaint)
                strokePaint.strokeWidth = 1f
            }
            val label = "${index + 1} ${entry.name}".take(USER_NAME_CHARS)
            drawText(canvas, label, rect.left + 6, rect.top + 4, if (selected) YELLOW else WHITE)
        }

        if (pager.pageCount > 1) {
            drawCentered(
                canvas,
                "PAGE ${pager.pageOf(userSelected) + 1}/${pager.pageCount}",
                152,
                GREY,
            )
        }
        if (pager.hasPreviousPage(userSelected)) drawButton(canvas, PREV_RECT, "PREV")
        if (pager.hasNextPage(userSelected)) drawButton(canvas, NEXT_PAGE_RECT, "NEXT")
    }

    private fun drawNotice(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, noticeTitle, 64, YELLOW)
        drawCentered(canvas, noticeBody, 92, WHITE)
        drawCentered(canvas, noticeHint, 120, GREY)
        drawButton(canvas, BACK_RECT, "BACK")
        drawCentered(canvas, "FIRE OR TAP TO RETURN", 198, GREY)
    }

    private fun stageTitle(): String = if (singleLevel) {
        (session?.level?.name ?: "LEVEL").take(20)
    } else {
        "STAGE ${session?.levelNumber ?: 1}"
    }

    private fun drawCurtain(canvas: Canvas) {
        canvas.drawColor(CURTAIN_GREY)
        val bar = (screenTicks * 5).coerceAtMost(64)
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, 0f, LOGICAL_WIDTH.toFloat(), bar.toFloat(), fillPaint)
        canvas.drawRect(
            0f,
            (LOGICAL_HEIGHT - bar).toFloat(),
            LOGICAL_WIDTH.toFloat(),
            LOGICAL_HEIGHT.toFloat(),
            fillPaint,
        )

        if (screenTicks > 12) {
            drawCentered(canvas, stageTitle(), 94, BLACK)
            drawCentered(canvas, "STEEL EAGLE", 118, DARK)
        }
        if (screenTicks > 40) {
            drawCentered(canvas, "READY", 142, BLACK)
        }
    }

    private fun drawPlay(canvas: Canvas) {
        val scene = playScene
        if (scene == null) {
            canvas.drawColor(Color.BLACK)
            return
        }
        scene.draw(canvas)
        when (scene.session.world.state) {
            WorldState.LEVEL_CLEAR -> drawBanner(canvas, "STAGE CLEAR", 92)
            WorldState.GAME_OVER -> {
                val elapsed = (GAME_OVER_DELAY_TICKS - resultTicks)
                    .coerceIn(0, GAME_OVER_DELAY_TICKS)
                val rise = (elapsed * 64 / 60).coerceAtMost(64)
                drawBanner(canvas, "GAME OVER", 160 - rise)
            }

            WorldState.PLAYING -> Unit
        }
    }

    private fun drawPause(canvas: Canvas) {
        playScene?.draw(canvas) ?: canvas.drawColor(Color.BLACK)

        fillPaint.color = 0x99000000.toInt()
        canvas.drawRect(0f, 0f, LOGICAL_WIDTH.toFloat(), LOGICAL_HEIGHT.toFloat(), fillPaint)
        fillPaint.color = Color.BLACK
        canvas.drawRect(48f, 44f, 208f, 172f, fillPaint)
        strokePaint.color = BORDER
        strokePaint.strokeWidth = 2f
        canvas.drawRect(48f, 44f, 208f, 172f, strokePaint)

        val blink = (screenTicks / 16) % 2 == 0
        drawCentered(canvas, "PAUSE", 56, if (blink) YELLOW else WHITE)
        drawMenu(canvas, activePauseMenu(), PAUSE_TOP, PAUSE_ROW)
        val note = pauseNote
        if (note != null) {
            drawCentered(canvas, note, 148, YELLOW)
        } else {
            drawCentered(canvas, "START RESUMES", 152, GREY)
        }
    }

    private fun drawTally(canvas: Canvas) {
        if (tally2p != null) {
            drawTally2P(canvas)
            return
        }

        canvas.drawColor(Color.BLACK)
        val summary = tally
        drawCentered(canvas, tallyTitle(), 10, YELLOW)

        if (summary != null) {
            drawText(canvas, "TYPE", 24, 26, DARK)
            drawRight(canvas, "N", 112, 26, DARK)
            drawRight(canvas, "PTS", 176, 26, DARK)
            drawRight(canvas, "TOTAL", 232, 26, DARK)
            for ((index, row) in summary.rows.withIndex()) {
                val y = 38 + index * 14
                drawText(canvas, typeLabel(row.type), 24, y, WHITE)
                drawRight(canvas, row.kills.toString().padStart(2, '0'), 112, y, GREY)
                drawText(canvas, "X", 124, y, DARK)
                drawRight(canvas, row.points.toString(), 176, y, GREY)
                drawRight(canvas, row.subtotal.toString(), 232, y, WHITE)
            }

            if (summary.bonus > 0) {
                val y = 38 + summary.rows.size * 14
                drawText(canvas, "BONUS", 24, y, WHITE)
                drawRight(canvas, summary.bonus.toString(), 232, y, WHITE)
            }

            fillPaint.color = BORDER
            canvas.drawRect(24f, 110f, 232f, 111f, fillPaint)
            drawText(canvas, "TOTAL", 24, 118, YELLOW)
            drawRight(canvas, summary.total.toString(), 232, 118, YELLOW)
            drawText(canvas, "SCORE", 24, 136, GREY)
            drawRight(canvas, (session?.score ?: 0).toString().padStart(6, '0'), 232, 136, GREY)
        }

        if (screenTicks > TALLY_GUARD_TICKS) {
            drawButton(canvas, NEXT_RECT, "NEXT")
            drawCentered(canvas, "FIRE OR TAP", 192, GREY)
        }
    }

    private fun drawTally2P(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val summary = tally2p ?: return
        drawCentered(canvas, tallyTitle(), 8, YELLOW)
        drawText(canvas, "TYPE", 12, 26, DARK)
        drawRight(canvas, "P1 K/PTS", 132, 26, WHITE)
        drawRight(canvas, "P2 K/PTS", 220, 26, P2_GREEN)

        val first = summary.players[0]
        val second = summary.players[1]
        for ((index, row) in first.rows.withIndex()) {
            val y = 38 + index * 12
            val other = second.rows[index]
            drawText(canvas, typeLabel(row.type), 12, y, WHITE)
            drawRight(canvas, row.kills.toString().padStart(2, '0'), 92, y, GREY)
            drawRight(canvas, row.subtotal.toString(), 132, y, WHITE)
            drawRight(canvas, other.kills.toString().padStart(2, '0'), 176, y, GREY)
            drawRight(canvas, other.subtotal.toString(), 220, y, P2_GREEN)
        }

        drawText(canvas, "OTHER", 12, 88, GREY)
        drawRight(canvas, first.other.toString(), 132, 88, GREY)
        drawRight(canvas, second.other.toString(), 220, 88, P2_GREEN)

        drawText(canvas, "BONUS", 12, 100, WHITE)
        drawRight(
            canvas,
            if (summary.winner == 0) "+${TallySummary2P.BONUS}" else "0",
            132,
            100,
            YELLOW,
        )
        drawRight(
            canvas,
            if (summary.winner == 1) "+${TallySummary2P.BONUS}" else "0",
            220,
            100,
            P2_GREEN,
        )

        fillPaint.color = BORDER
        canvas.drawRect(12f, 112f, 244f, 113f, fillPaint)
        drawText(canvas, "TOTAL", 12, 120, YELLOW)
        drawRight(canvas, first.total.toString(), 132, 120, YELLOW)
        drawRight(canvas, second.total.toString(), 220, 120, P2_GREEN)
        drawText(canvas, "KILLS", 12, 132, GREY)
        drawRight(canvas, first.totalKills.toString(), 132, 132, GREY)
        drawRight(canvas, second.totalKills.toString(), 220, 132, P2_GREEN)

        if (screenTicks > TALLY_GUARD_TICKS) {
            drawButton(canvas, NEXT_RECT, "NEXT")
            drawCentered(canvas, "FIRE OR TAP", 192, GREY)
        }
    }

    private fun tallyTitle(): String = if (singleLevel) {
        "${(session?.level?.name ?: "LEVEL").take(20)} CLEAR"
    } else {
        "STAGE ${session?.levelNumber ?: 1} CLEAR"
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawLogo(canvas, brickText.bitmap("GAME OVER", 3), 52)
        val scores = gameOverScores
        if (scores != null) {
            drawCentered(
                canvas,
                "P1 ${scores[0].toString().padStart(6, '0')}",
                96,
            )
            drawCentered(
                canvas,
                "P2 ${scores[1].toString().padStart(6, '0')}",
                110,
                P2_GREEN,
            )
            drawCentered(canvas, "STAGE ${session?.levelNumber ?: 1}", 126, GREY)
        } else {
            drawCentered(canvas, "SCORE ${gameOverScore.toString().padStart(6, '0')}", 100)
            drawCentered(canvas, "STAGE ${session?.levelNumber ?: 1}", 116, GREY)
        }

        if (screenTicks > GAME_OVER_GUARD_TICKS) {
            drawButton(canvas, CONTINUE_RECT, "SCORES")
            drawCentered(canvas, "FIRE OR TAP", 192, GREY)
        }
    }

    private fun drawInitials(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val visible = (screenTicks / 14) % 2 == 0
        drawCentered(canvas, "NEW HIGH SCORE", 18, if (visible) YELLOW else WHITE)
        drawCentered(canvas, "SCORE ${gameOverScore.toString().padStart(6, '0')}", 40)
        drawCentered(canvas, "ENTER INITIALS", 60, GREY)

        for (slot in 0 until 3) {
            val rect = initialSlotRect(slot)
            fillPaint.color = CELL_FILL
            canvas.drawRect(rect, fillPaint)
            strokePaint.color = if (slot == initials.slot) YELLOW else BORDER
            strokePaint.strokeWidth = if (slot == initials.slot) 2f else 1f
            canvas.drawRect(rect, strokePaint)
            drawText(
                canvas,
                initials.text[slot].toString(),
                rect.centerX() - 4,
                rect.centerY() - 4,
                if (slot == initials.slot) YELLOW else WHITE,
            )
        }

        drawButton(canvas, INITIALS_UP_RECT, "UP")
        drawButton(canvas, INITIALS_DOWN_RECT, "DOWN")
        drawButton(canvas, INITIALS_OK_RECT, "OK")
        drawButton(canvas, INITIALS_SAVE_RECT, "SAVE")
        drawCentered(canvas, "ARROWS CHANGE  FIRE OK", 198, GREY)
    }

    private fun drawHighScores(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "HIGH SCORES", 6, YELLOW)

        val tabs = listOf(
            Difficulty.EASY to 40,
            Difficulty.NORMAL to 100,
            Difficulty.HARD to 164,
        )
        for ((difficulty, left) in tabs) {
            val width = if (difficulty == Difficulty.NORMAL) 60 else 56
            val label = difficulty.name
            val color = if (difficulty == highScoresDifficulty) YELLOW else GREY
            drawText(canvas, label, left + (width - font.width(label)) / 2, 26, color)
            if (difficulty == highScoresDifficulty) {
                fillPaint.color = YELLOW
                canvas.drawRect(
                    left.toFloat(),
                    38f,
                    (left + width).toFloat(),
                    39f,
                    fillPaint,
                )
            }
        }

        val entries = highScores.entries(highScoresDifficulty)
        if (entries.isEmpty()) {
            drawCentered(canvas, "NO SCORES YET", 96, GREY)
        } else {
            for ((index, entry) in entries.withIndex()) {
                val y = 52 + index * 12
                val color = if (index == 0) YELLOW else WHITE
                drawText(canvas, (index + 1).toString().padStart(2, '0'), 32, y, GREY)
                drawText(canvas, entry.display, 80, y, color)
                drawRight(canvas, entry.score.toString().padStart(6, '0'), 224, y, color)
            }
        }

        drawButton(canvas, SCORES_BACK_RECT, "BACK")
        drawCentered(canvas, "LEFT RIGHT DIFFICULTY", 204, GREY)
    }

    private fun drawOptions(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        drawCentered(canvas, "OPTIONS", 10, YELLOW)

        for ((index, row) in OptionRow.entries.withIndex()) {
            val y = OPTIONS_TOP + index * OPTIONS_ROW
            val selected = index == options.selected
            if (selected) {
                canvas.drawBitmap(tankCursor, 4f, (y - 4).toFloat(), null)
            }
            drawText(canvas, row.label, 28, y, if (selected) YELLOW else WHITE)
            if (row != OptionRow.BACK) {
                drawRight(canvas, options.valueLabel(row), 196, y, if (selected) WHITE else GREY)
                drawText(canvas, "-", OPTIONS_MINUS_X, y, GREY)
                drawText(canvas, "+", OPTIONS_PLUS_X, y, GREY)
            }
        }

        drawCentered(canvas, "START BACK  TAP TO CHANGE", 200, GREY)
    }

    private fun drawMenu(canvas: Canvas, menu: Menu, top: Int, rowHeight: Int) {
        for ((index, label) in menu.labels.withIndex()) {
            val y = top + index * rowHeight
            val x = (LOGICAL_WIDTH - font.width(label)) / 2
            val selected = index == menu.selected
            if (selected) {
                canvas.drawBitmap(tankCursor, (x - 24).toFloat(), (y - 4).toFloat(), null)
            }
            drawText(canvas, label, x, y, if (selected) YELLOW else WHITE)
        }
    }

    private fun drawBanner(canvas: Canvas, text: String, y: Int) {
        fillPaint.color = 0xCC000000.toInt()
        canvas.drawRect(36f, y.toFloat(), 172f, (y + 24).toFloat(), fillPaint)
        strokePaint.color = BORDER
        strokePaint.strokeWidth = 1f
        canvas.drawRect(36f, y.toFloat(), 172f, (y + 24).toFloat(), strokePaint)
        drawCentered(canvas, text, y + 4, WHITE)
    }

    private fun drawButton(
        canvas: Canvas,
        rect: Rect,
        label: String,
        selected: Boolean = false,
    ) {
        fillPaint.color = CELL_FILL
        canvas.drawRect(rect, fillPaint)
        strokePaint.color = if (selected) YELLOW else BORDER
        strokePaint.strokeWidth = if (selected) 2f else 1f
        canvas.drawRect(rect, strokePaint)
        drawText(
            canvas,
            label,
            rect.centerX() - font.width(label) / 2,
            rect.centerY() - 4,
            if (selected) YELLOW else WHITE,
        )
    }

    private fun drawText(canvas: Canvas, text: String, x: Int, y: Int, color: Int = WHITE) {
        font.draw(canvas, text, x, y, textPaints.getValue(color))
    }

    private fun drawCentered(canvas: Canvas, text: String, y: Int, color: Int = WHITE) {
        drawText(canvas, text, (LOGICAL_WIDTH - font.width(text)) / 2, y, color)
    }

    private fun drawRight(canvas: Canvas, text: String, right: Int, y: Int, color: Int = WHITE) {
        drawText(canvas, text, right - font.width(text), y, color)
    }

    // ------------------------------------------------------------------ touch

    private fun tapMenu(
        top: Int,
        rowHeight: Int,
        menu: Menu,
        x: Int,
        y: Int,
    ): Boolean {
        if (x < 32 || x > 248) return false
        for (index in menu.labels.indices) {
            val rowTop = top + index * rowHeight - 4
            if (y >= rowTop && y < rowTop + 18) {
                menu.select(index)
                edges.lockFire()
                return true
            }
        }
        return false
    }

    private fun tapLevelSelect(x: Int, y: Int): Boolean {
        if (CAMPAIGN_TAB_RECT.contains(x, y)) {
            levelTabFocused = false
            levelBackFocused = false
            selectLevelTab(LevelTab.CAMPAIGN)
            return true
        }
        if (USER_TAB_RECT.contains(x, y)) {
            if (lanHostStage) return true
            levelTabFocused = false
            levelBackFocused = false
            selectLevelTab(LevelTab.USER)
            return true
        }

        if (levelTab == LevelTab.USER) {
            if (userList.isNotEmpty()) {
                val pager = UserLevelPager(userList.size, USER_PAGE_SIZE)
                if (pager.hasPreviousPage(userSelected) && PREV_RECT.contains(x, y)) {
                    userSelected = pager.previousPage(userSelected)
                    levelBackFocused = false
                    return true
                }
                if (pager.hasNextPage(userSelected) && NEXT_PAGE_RECT.contains(x, y)) {
                    userSelected = pager.nextPage(userSelected)
                    levelBackFocused = false
                    return true
                }
                for (row in 0 until USER_PAGE_SIZE) {
                    if (!userRowRect(row).contains(x, y)) continue
                    val index = pager.pageStart(userSelected) + row
                    if (index > pager.lastIndex) return true
                    userSelected = index
                    edges.lockFire()
                    startUserLevel(userList[index])
                    return true
                }
            }
        } else {
            if (debug && UNLOCK_RECT.contains(x, y)) {
                prefs.unlockLevel(selectedDifficulty, levels.size)
                refreshGrid()
                return true
            }
            if (grid.page > 0 && PREV_RECT.contains(x, y)) {
                levelBackFocused = false
                grid.move(-GRID_PAGE_SIZE, 0)
                return true
            }
            if (grid.page < grid.pageCount - 1 && NEXT_PAGE_RECT.contains(x, y)) {
                levelBackFocused = false
                grid.move(GRID_PAGE_SIZE, 0)
                return true
            }
        }

        if (BACK_RECT.contains(x, y)) {
            returnToDifficulty()
            return true
        }

        if (levelTab == LevelTab.USER) return false

        for (cell in 0 until GRID_PAGE_SIZE) {
            if (!gridCellRect(cell).contains(x, y)) continue
            val stage = grid.stageAt(cell) ?: return true
            grid.selectIndex(stage - 1)
            edges.lockFire()
            if (lanHostStage) startLanHostGame(stage) else startCampaign(stage)
            return true
        }
        return false
    }

    private fun userRowRect(row: Int): Rect {
        val top = USER_ROWS_TOP + row * USER_ROW
        return Rect(12, top - 3, 244, top + 13)
    }

    private fun tapInitials(x: Int, y: Int): Boolean {
        for (slot in 0 until 3) {
            if (initialSlotRect(slot).contains(x, y)) {
                initials.selectSlot(slot)
                return true
            }
        }

        when {
            INITIALS_UP_RECT.contains(x, y) -> initials.changeLetter(1)
            INITIALS_DOWN_RECT.contains(x, y) -> initials.changeLetter(-1)
            INITIALS_OK_RECT.contains(x, y) -> confirmInitials()
            INITIALS_SAVE_RECT.contains(x, y) -> saveInitials()
            else -> return false
        }
        return true
    }

    private fun tapHighScores(x: Int, y: Int): Boolean {
        val tabs = listOf(
            Difficulty.EASY to Rect(32, 22, 96, 40),
            Difficulty.NORMAL to Rect(100, 22, 164, 40),
            Difficulty.HARD to Rect(168, 22, 232, 40),
        )
        for ((difficulty, rect) in tabs) {
            if (rect.contains(x, y)) {
                highScoresDifficulty = difficulty
                return true
            }
        }
        if (SCORES_BACK_RECT.contains(x, y)) {
            leaveHighScores()
            return true
        }
        return false
    }

    private fun tapOptions(x: Int, y: Int): Boolean {
        if (y < OPTIONS_TOP - 4 || y >= OPTIONS_TOP + OptionRow.entries.size * OPTIONS_ROW) {
            return false
        }
        val index = (y - (OPTIONS_TOP - 4)) / OPTIONS_ROW
        if (index !in OptionRow.entries.indices) return false
        options.select(index)

        if (options.row == OptionRow.BACK) {
            leaveOptions()
            return true
        }

        val row = options.row
        val changed = when {
            x in OPTIONS_MINUS_AREA -> options.change(-1)
            x in OPTIONS_PLUS_AREA -> options.change(1)
            else -> options.change(1)
        }
        if (changed) applyAndSaveSettings(row)
        return true
    }

    private fun gridCellRect(cell: Int): Rect {
        val row = cell / GRID_COLUMNS
        val column = cell % GRID_COLUMNS
        val left = GRID_LEFT + column * (GRID_CELL + GRID_GAP)
        val top = GRID_TOP + row * (GRID_CELL + GRID_ROW_GAP)
        return Rect(left, top, left + GRID_CELL, top + GRID_CELL)
    }

    private fun initialSlotRect(slot: Int): Rect {
        val left = INITIALS_LEFT + slot * (INITIALS_SLOT + INITIALS_GAP)
        return Rect(left, INITIALS_TOP, left + INITIALS_SLOT, INITIALS_TOP + 28)
    }

    private fun typeLabel(type: EnemyType): String = when (type) {
        EnemyType.BASIC -> "BASIC"
        EnemyType.FAST -> "FAST"
        EnemyType.POWER -> "POWER"
        EnemyType.ARMORED -> "ARMOR"
    }

    private companion object {
        const val LOGICAL_WIDTH = 256
        const val LOGICAL_HEIGHT = 224

        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFFA0A0A0.toInt()
        const val DARK = 0xFF606060.toInt()
        const val YELLOW = 0xFFFCD800.toInt()
        const val P2_GREEN = 0xFF68D868.toInt()
        const val SLOT_SELECTED = 0xFF5A4A10.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val SHADOW = 0xFF401008.toInt()
        const val BORDER = 0xFF8C8C8C.toInt()
        const val CELL_FILL = 0xFF303030.toInt()
        const val LOCKED_FILL = 0xFF181818.toInt()
        const val CURTAIN_GREY = 0xFF9C9C9C.toInt()

        const val TITLE_GUARD_TICKS = 15
        const val CURTAIN_TICKS = 100
        const val CLEAR_DELAY_TICKS = 180
        const val GAME_OVER_DELAY_TICKS = 150
        const val SKIP_GUARD_TICKS = 30
        const val TALLY_GUARD_TICKS = 30
        const val LAN_SEED = 1
        const val SNAPSHOT_INTERVAL_MS = 33L
        const val TEST_INPUT_LOCK_TICKS = 10
        const val GAME_OVER_GUARD_TICKS = 30
        const val GAME_OVER_TICKS = 150

        const val MENU_TOP_MODE = 64
        const val MENU_ROW_MODE = 20
        const val MENU_TOP_DIFFICULTY = 80
        const val MENU_ROW_DIFFICULTY = 24
        const val PAUSE_TOP = 92
        const val PAUSE_ROW = 20
        const val OPTIONS_TOP = 40
        const val OPTIONS_ROW = 16
        const val OPTIONS_MINUS_X = 204
        const val OPTIONS_PLUS_X = 228
        val OPTIONS_MINUS_AREA = 196..216
        val OPTIONS_PLUS_AREA = 220..244

        const val GRID_COLUMNS = 5
        const val GRID_ROWS = 2
        const val GRID_PAGE_SIZE = 10
        const val GRID_CELL = 24
        const val GRID_GAP = 8
        const val GRID_ROW_GAP = 12
        const val GRID_LEFT = 52
        const val GRID_TOP = 60

        const val LAN_JOIN_ROWS = 5
        const val LAN_JOIN_TOP = 44
        const val LAN_JOIN_ROW = 18
        const val IP_COLUMNS = 4
        const val IP_LEFT = 80
        const val IP_TOP = 62
        const val IP_CELL = 24
        const val IP_ROW = 20

        const val USER_PAGE_SIZE = 5
        const val USER_ROWS_TOP = 56
        const val USER_ROW = 18
        const val USER_NAME_CHARS = 28

        const val INITIALS_LEFT = 84
        const val INITIALS_TOP = 78
        const val INITIALS_SLOT = 24
        const val INITIALS_GAP = 8

        val CAMPAIGN_TAB_RECT = Rect(24, 22, 120, 40)
        val LAN_ENTER_IP_RECT = Rect(32, 144, 224, 162)
        val LAN_BACK_RECT = Rect(96, 172, 160, 190)
        val USER_TAB_RECT = Rect(136, 22, 232, 40)
        val PREV_RECT = Rect(8, 160, 76, 178)
        val NEXT_PAGE_RECT = Rect(180, 160, 248, 178)
        val UNLOCK_RECT = Rect(8, 2, 72, 19)
        val BACK_RECT = Rect(96, 160, 160, 178)
        val SCORES_BACK_RECT = Rect(96, 180, 160, 198)
        val NEXT_RECT = Rect(96, 160, 160, 178)
        val CONTINUE_RECT = Rect(88, 160, 168, 178)
        val INITIALS_UP_RECT = Rect(68, 118, 116, 136)
        val INITIALS_DOWN_RECT = Rect(140, 118, 188, 136)
        val INITIALS_OK_RECT = Rect(68, 146, 188, 164)
        val INITIALS_SAVE_RECT = Rect(96, 168, 160, 184)
    }
}
