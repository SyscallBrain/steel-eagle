package pt.tiago.steeleagle.core.net

import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.world.WorldSnapshot
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Authoritative host for a local two-player game. One guest connects, receives
 * [NetMessage.Welcome] with the stage and then snapshots at 30 Hz. Incoming
 * messages are applied on the host's game thread by polling the exposed state.
 */
class LanHost(
    private val port: Int = NetProtocol.DEFAULT_PORT,
    private val timeoutMs: Long = NetProtocol.TIMEOUT_MS,
) : Closeable {

    enum class State { IDLE, LISTENING, CONNECTED, CLOSED, FAILED }

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var failure: String? = null
        private set

    @Volatile
    var guestName: String = ""
        private set

    @Volatile
    var lastMessageAt: Long = 0L
        private set

    @Volatile
    var lastAck: Int = -1
        private set

    @Volatile
    var remotePaused: Boolean = false
        private set

    @Volatile
    var byeReason: String? = null
        private set

    @Volatile
    var lastSnapshotSeq: Int = 0
        private set

    /** Increments on every completed handshake, so a new guest resyncs. */
    @Volatile
    var connectionCount: Int = 0
        private set

    private val guestInput = AtomicReference(PlayerInput.NONE)
    private val guestFireLatch = AtomicBoolean(false)
    private val guestInputSeq = AtomicInteger(-1)
    private val outbox = LinkedBlockingQueue<NetMessage>(OUTBOX_SIZE)

    private var server: ServerSocket? = null
    @Volatile
    private var socket: Socket? = null
    @Volatile
    private var output: DataOutputStream? = null
    private var acceptThread: Thread? = null
    private var writerThread: Thread? = null

    @Volatile
    private var closed = false
    private val writeLock = Any()

    val localPort: Int
        get() = server?.localPort ?: -1

    /** Binds the port and starts accepting. False when the port is busy. */
    fun start(): Boolean {
        if (state != State.IDLE) return false
        return try {
            val bound = ServerSocket()
            bound.reuseAddress = true
            bound.bind(InetSocketAddress(port))
            server = bound
            state = State.LISTENING
            acceptThread = Thread({ acceptLoop() }, "steeleagle-host-accept").apply {
                isDaemon = true
                start()
            }
            true
        } catch (exception: Exception) {
            failure = exception.message ?: "CANNOT LISTEN"
            state = State.FAILED
            false
        }
    }

    private fun acceptLoop() {
        while (!closed) {
            val serverSocket = server ?: return
            val client = try {
                serverSocket.accept()
            } catch (_: Exception) {
                if (closed) return
                Thread.sleep(ACCEPT_RETRY_MS)
                continue
            }
            handleConnection(client)
        }
    }

    private fun handleConnection(client: Socket) {
        socket = client
        outbox.clear()
        guestInput.set(PlayerInput.NONE)
        guestFireLatch.set(false)
        guestInputSeq.set(-1)
        lastAck = -1
        remotePaused = false
        byeReason = null
        lastSnapshotSeq = 0
        try {
            client.tcpNoDelay = true
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(client.getInputStream()))
            val hello = NetCodec.read(input)
            if (hello !is NetMessage.Hello || hello.version != NetProtocol.VERSION) {
                writeDirect(client, NetMessage.Bye("VERSION MISMATCH"))
                closeQuietly(client)
                return
            }
            guestName = hello.name
            failure = null
            connectionCount++
            lastMessageAt = System.currentTimeMillis()
            client.soTimeout = 0
            state = State.CONNECTED
            val out = DataOutputStream(BufferedOutputStream(client.getOutputStream()))
            output = out
            startWriter(client, out)
            readLoop(client, input)
        } catch (exception: Exception) {
            if (!closed) failure = exception.message
        } finally {
            writerThread?.interrupt()
            closeQuietly(client)
            output = null
            socket = null
            guestName = ""
            remotePaused = false
            guestInput.set(PlayerInput.NONE)
            outbox.clear()
            if (!closed) state = State.LISTENING
        }
    }

    private fun readLoop(client: Socket, input: DataInputStream) {
        while (!closed && !client.isClosed) {
            val message = try {
                NetCodec.read(input)
            } catch (_: Exception) {
                return
            }
            lastMessageAt = System.currentTimeMillis()
            when (message) {
                is NetMessage.Input -> {
                    guestInput.set(
                        PlayerInput(message.direction, message.fire),
                    )
                    // A tap can press and release fire between two host ticks;
                    // latch it so the next tick still fires once.
                    if (message.fire) guestFireLatch.set(true)
                    guestInputSeq.set(message.seq)
                }

                is NetMessage.Pause -> remotePaused = message.paused
                is NetMessage.Ack -> lastAck = message.snapshotSeq
                is NetMessage.Bye -> {
                    byeReason = message.reason
                    return
                }

                else -> Unit
            }
        }
    }

    private fun startWriter(client: Socket, output: DataOutputStream) {
        writerThread = Thread({
            try {
                while (!closed && !client.isClosed) {
                    // Idle polls double as a heartbeat: the render thread stops
                    // while the host app is in the background, and the guest
                    // must not time out during that synchronized pause.
                    val message = outbox.poll(WRITER_POLL_MS, TimeUnit.MILLISECONDS)
                        ?: NetMessage.Ack(HEARTBEAT_SEQ)
                    synchronized(writeLock) { NetCodec.write(output, message) }
                    if (message is NetMessage.Bye) {
                        closeQuietly(client)
                        return@Thread
                    }
                }
            } catch (_: Exception) {
                closeQuietly(client)
            }
        }, "steeleagle-host-writer").apply {
            isDaemon = true
            start()
        }
    }

    private fun writeDirect(client: Socket, message: NetMessage) {
        try {
            DataOutputStream(client.getOutputStream()).also {
                NetCodec.write(it, message)
                it.flush()
            }
        } catch (_: Exception) {
            Unit
        }
    }

    /** Latest guest input; a fire tap since the last call counts as held once. */
    fun guestInput(): PlayerInput {
        val current = guestInput.get()
        val latched = guestFireLatch.getAndSet(false)
        return if (latched && !current.fire) current.copy(fire = true) else current
    }

    fun guestInputSeq(): Int = guestInputSeq.get()

    fun sendWelcome(seed: Int, level: Int, difficulty: Int) {
        enqueue(
            NetMessage.Welcome(
                version = NetProtocol.VERSION,
                seed = seed,
                level = level,
                difficulty = difficulty,
                players = 2,
            ),
        )
    }

    /** Sequence the next [sendSnapshot] will use, for the terrain tracker. */
    val nextSnapshotSeq: Int
        get() = lastSnapshotSeq + 1

    fun sendSnapshot(
        state: WorldSnapshot,
        terrain: NetProtocol.TerrainState,
    ) {
        val seq = lastSnapshotSeq + 1
        lastSnapshotSeq = seq
        enqueue(NetMessage.Snapshot(seq, guestInputSeq.get(), state, terrain))
    }

    fun sendEvent(event: GameEvent) {
        enqueue(NetMessage.Event(event))
    }

    fun sendPause(paused: Boolean) {
        enqueue(NetMessage.Pause(paused))
    }

    fun sendBye(reason: String) {
        enqueue(NetMessage.Bye(reason.take(40)))
    }

    /** Best-effort goodbye written before an immediate close. */
    fun sendByeNow(reason: String) {
        val out = output ?: return
        try {
            synchronized(writeLock) {
                NetCodec.write(out, NetMessage.Bye(reason.take(40)))
                out.flush()
            }
        } catch (_: Exception) {
            Unit
        }
    }

    /** Restarts the silence window after the app comes back from the background. */
    fun resetTimeout() {
        lastMessageAt = System.currentTimeMillis()
    }

    fun isGuestTimedOut(now: Long = System.currentTimeMillis()): Boolean =
        state == State.CONNECTED && now - lastMessageAt > timeoutMs

    private fun enqueue(message: NetMessage) {
        if (state != State.CONNECTED) return
        outbox.offer(message)
    }

    override fun close() {
        if (closed) return
        closed = true
        state = State.CLOSED
        closeQuietly(server)
        closeQuietly(socket)
        acceptThread?.interrupt()
        writerThread?.interrupt()
        acceptThread?.join(THREAD_JOIN_MS)
        writerThread?.join(THREAD_JOIN_MS)
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
            Unit
        }
    }

    private companion object {
        const val OUTBOX_SIZE = 512
        const val WRITER_POLL_MS = 100L
        const val THREAD_JOIN_MS = 150L
        const val HANDSHAKE_TIMEOUT_MS = 5_000
        const val ACCEPT_RETRY_MS = 50L
        const val HEARTBEAT_SEQ = -1
    }
}

/**
 * Guest side: connects to a host, sends its input (on change plus a 100 ms
 * heartbeat) and exposes the latest snapshot and events for the render thread.
 */
class LanClient(
    private val address: String,
    private val port: Int = NetProtocol.DEFAULT_PORT,
    private val name: String = "PLAYER 2",
    private val timeoutMs: Long = NetProtocol.TIMEOUT_MS,
) : Closeable {

    enum class State { IDLE, CONNECTING, HANDSHAKE, CONNECTED, CLOSED, FAILED }

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var failure: String? = null
        private set

    @Volatile
    var welcome: NetMessage.Welcome? = null
        private set

    @Volatile
    var lastMessageAt: Long = 0L
        private set

    @Volatile
    var remotePaused: Boolean = false
        private set

    @Volatile
    var byeReason: String? = null
        private set

    private val events = mutableListOf<GameEvent>()
    private val snapshots = ArrayDeque<NetMessage.Snapshot>()
    private val outbox = LinkedBlockingQueue<NetMessage>(OUTBOX_SIZE)

    @Volatile
    private var socket: Socket? = null
    @Volatile
    private var output: DataOutputStream? = null
    private var connectThread: Thread? = null
    private var writerThread: Thread? = null
    private var heartbeatThread: Thread? = null
    private val inputLock = Any()

    @Volatile
    private var closed = false
    private val writeLock = Any()

    private var inputSeq = 0
    private var lastDirection: Dir? = null
    private var lastFire = false
    private var lastInputAt = 0L

    @Volatile
    private var startedAt = 0L

    fun start() {
        if (state != State.IDLE) return
        state = State.CONNECTING
        startedAt = System.currentTimeMillis()
        connectThread = Thread({
            try {
                val client = Socket()
                socket = client
                client.tcpNoDelay = true
                client.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(client.getOutputStream()))
                output = out
                startWriter(client, out)
                sendRaw(NetMessage.Hello(NetProtocol.VERSION, name.take(24)))
                state = State.HANDSHAKE
                startHeartbeat()
                readLoop(client, input)
            } catch (exception: Exception) {
                if (!closed) {
                    failure = exception.message ?: "CANNOT CONNECT"
                    state = State.FAILED
                }
            } finally {
                writerThread?.interrupt()
                heartbeatThread?.interrupt()
                closeQuietly(socket)
                output = null
                if (state == State.CONNECTED || state == State.HANDSHAKE) {
                    state = State.CLOSED
                }
            }
        }, "steeleagle-client-connect").apply {
            isDaemon = true
            start()
        }
    }

    private fun readLoop(client: Socket, input: DataInputStream) {
        while (!closed && !client.isClosed) {
            val message = try {
                NetCodec.read(input)
            } catch (_: Exception) {
                return
            }
            lastMessageAt = System.currentTimeMillis()
            when (message) {
                is NetMessage.Welcome -> {
                    if (message.version != NetProtocol.VERSION) {
                        failure = "VERSION MISMATCH"
                        state = State.FAILED
                        return
                    }
                    welcome = message
                    state = State.CONNECTED
                }

                is NetMessage.Snapshot -> {
                    synchronized(snapshots) {
                        snapshots.addLast(message)
                        while (snapshots.size > SNAPSHOT_BACKLOG) snapshots.removeFirst()
                    }
                }

                is NetMessage.Event -> synchronized(events) {
                    events += message.event
                    while (events.size > MAX_EVENTS) events.removeAt(0)
                }
                is NetMessage.Pause -> remotePaused = message.paused
                is NetMessage.Bye -> {
                    byeReason = message.reason
                    return
                }

                else -> Unit
            }
        }
    }

    private fun startWriter(client: Socket, output: DataOutputStream) {
        writerThread = Thread({
            try {
                while (!closed && !client.isClosed) {
                    val message = outbox.poll(WRITER_POLL_MS, TimeUnit.MILLISECONDS)
                        ?: continue
                    synchronized(writeLock) { NetCodec.write(output, message) }
                    if (message is NetMessage.Bye) {
                        closeQuietly(client)
                        return@Thread
                    }
                }
            } catch (_: Exception) {
                closeQuietly(client)
            }
        }, "steeleagle-client-writer").apply {
            isDaemon = true
            start()
        }
    }

    /** Sends on change or every [NetProtocol.HEARTBEAT_MS]; call once per tick. */
    fun sendInput(direction: Dir?, fire: Boolean, now: Long = System.currentTimeMillis()) {
        if (state != State.CONNECTED && state != State.HANDSHAKE) return
        synchronized(inputLock) {
            val changed = direction != lastDirection || fire != lastFire
            if (!changed && now - lastInputAt < NetProtocol.HEARTBEAT_MS) return
            lastDirection = direction
            lastFire = fire
            lastInputAt = now
            inputSeq++
            sendRaw(NetMessage.Input(inputSeq, direction, fire))
        }
    }

    /** Keeps the host's silence window open while the app is in the background. */
    private fun startHeartbeat() {
        heartbeatThread = Thread({
            while (!closed) {
                try {
                    Thread.sleep(HEARTBEAT_TICK_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val current = state
                if (current != State.CONNECTED && current != State.HANDSHAKE) continue
                synchronized(inputLock) {
                    val now = System.currentTimeMillis()
                    if (now - lastInputAt >= NetProtocol.HEARTBEAT_MS) {
                        lastInputAt = now
                        inputSeq++
                        sendRaw(NetMessage.Input(inputSeq, lastDirection, lastFire))
                    }
                }
            }
        }, "steeleagle-client-heartbeat").apply {
            isDaemon = true
            start()
        }
    }

    fun sendPause(paused: Boolean) {
        if (state == State.CONNECTED) sendRaw(NetMessage.Pause(paused))
    }

    fun sendAck(snapshotSeq: Int) {
        if (state == State.CONNECTED) sendRaw(NetMessage.Ack(snapshotSeq))
    }

    fun sendBye(reason: String) {
        if (state == State.CONNECTED) sendRaw(NetMessage.Bye(reason.take(40)))
    }

    /** Best-effort goodbye written before an immediate close. */
    fun sendByeNow(reason: String) {
        val out = output ?: return
        try {
            synchronized(writeLock) {
                NetCodec.write(out, NetMessage.Bye(reason.take(40)))
                out.flush()
            }
        } catch (_: Exception) {
            Unit
        }
    }

    /** Restarts the silence window after the app comes back from the background. */
    fun resetTimeout() {
        lastMessageAt = System.currentTimeMillis()
    }

    fun drainEvents(): List<GameEvent> = synchronized(events) {
        if (events.isEmpty()) return emptyList()
        val copy = events.toList()
        events.clear()
        copy
    }

    /** All snapshots since the last drain, in arrival order. */
    fun drainSnapshots(): List<NetMessage.Snapshot> = synchronized(snapshots) {
        if (snapshots.isEmpty()) return emptyList()
        val copy = snapshots.toList()
        snapshots.clear()
        copy
    }

    fun isTimedOut(now: Long = System.currentTimeMillis()): Boolean = when (state) {
        State.HANDSHAKE -> now - startedAt > NetProtocol.STAGE_TIMEOUT_MS
        State.CONNECTED -> now - lastMessageAt > timeoutMs
        else -> false
    }

    private fun sendRaw(message: NetMessage) {
        outbox.offer(message)
    }

    override fun close() {
        if (closed) return
        closed = true
        closeQuietly(socket)
        connectThread?.interrupt()
        writerThread?.interrupt()
        heartbeatThread?.interrupt()
        connectThread?.join(THREAD_JOIN_MS)
        writerThread?.join(THREAD_JOIN_MS)
        heartbeatThread?.join(THREAD_JOIN_MS)
        if (state != State.FAILED) state = State.CLOSED
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
            Unit
        }
    }

    private companion object {
        const val OUTBOX_SIZE = 256
        const val SNAPSHOT_BACKLOG = 64
        const val MAX_EVENTS = 256
        const val CONNECT_TIMEOUT_MS = 3_000
        const val WRITER_POLL_MS = 100L
        const val THREAD_JOIN_MS = 150L
        const val HEARTBEAT_TICK_MS = 50L
    }
}
