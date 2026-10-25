package pt.tiago.steeleagle.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import pt.tiago.steeleagle.input.VirtualTouchInput
import pt.tiago.steeleagle.scene.Scene

/**
 * Draws the scene into a 256x224 bitmap, scales it with an integer factor and
 * letterboxes it. Touch controls are drawn afterwards in real screen pixels so
 * they stay crisp and never get stretched with the game image.
 */
class GameRenderer(
    private val font: PixelFont,
    private val showFps: Boolean,
) {
    private val logical = Bitmap.createBitmap(
        Viewport.LOGICAL_WIDTH,
        Viewport.LOGICAL_HEIGHT,
        Bitmap.Config.ARGB_8888,
    )
    private val logicalCanvas = Canvas(logical)
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val destination = Rect()
    private val scanlinePaint = Paint().apply {
        isAntiAlias = false
        color = 0x40000000
        strokeWidth = 1f
    }
    private var scanlinePoints = FloatArray(0)
    private var scanlineHeight = -1

    /** Persisted CRT overlay; rebuilt only when the destination height changes. */
    @Volatile
    var scanlines: Boolean = false

    private var frames = 0
    private var fps = 0
    private var fpsText = "00"
    private var lastFpsNanos = System.nanoTime()

    fun draw(
        target: Canvas,
        scene: Scene,
        viewport: Viewport,
        touchInput: VirtualTouchInput?,
        secondTouch: VirtualTouchInput? = null,
    ) {
        scene.draw(logicalCanvas)
        countFrame()

        target.drawColor(Color.BLACK)
        destination.set(
            viewport.left,
            viewport.top,
            viewport.left + viewport.width,
            viewport.top + viewport.height,
        )
        target.drawBitmap(logical, null, destination, paint)
        if (scanlines) drawScanlines(target)
        // Debug FPS lives in the screen corner so it never covers menu or HUD text.
        if (showFps) {
            target.save()
            target.translate(viewport.scale * 2f, viewport.scale * 2f)
            target.scale(viewport.scale.toFloat(), viewport.scale.toFloat())
            font.draw(target, "FPS $fpsText", 0, 0)
            target.restore()
        }
        touchInput?.draw(target)
        secondTouch?.draw(target)
    }

    private fun drawScanlines(target: Canvas) {
        if (scanlineHeight != destination.height()) {
            scanlineHeight = destination.height()
            scanlinePoints = Scanlines.points(
                left = 0,
                top = 0,
                width = destination.width(),
                height = destination.height(),
            )
        }
        if (scanlinePoints.isEmpty()) return
        target.save()
        target.translate(destination.left.toFloat(), destination.top.toFloat())
        target.drawLines(scanlinePoints, scanlinePaint)
        target.restore()
    }

    private fun countFrame() {
        frames++
        val now = System.nanoTime()
        val elapsed = now - lastFpsNanos
        if (elapsed >= NANOS_PER_SECOND) {
            val value = (frames * NANOS_PER_SECOND / elapsed).toInt()
            if (value != fps) {
                fps = value
                fpsText = value.toString().padStart(2, '0')
            }
            frames = 0
            lastFpsNanos = now
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
