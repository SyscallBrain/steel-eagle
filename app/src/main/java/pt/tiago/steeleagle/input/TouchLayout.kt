package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.render.Viewport

data class ControlCircle(
    val centerX: Int,
    val centerY: Int,
    val radius: Int,
) {
    fun contains(x: Int, y: Int): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }
}

/** Screen-pixel rectangle used for the floating joystick's start area. */
data class ControlArea(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Int, y: Int): Boolean =
        x >= left && x < right && y >= top && y < bottom

    companion object {
        val EMPTY = ControlArea(0, 0, 0, 0)
    }
}

/**
 * Placement of the touch controls. They live in the letterbox margins when
 * those are wide enough, and fall back to a semi-transparent overlay on top of
 * the game area otherwise.
 *
 * D-pad mode uses fixed circles. Joystick mode uses a floating base anywhere
 * in [joystickZone]; [dpad] is then the default/inactive hint position. The
 * fire circle is always fixed. Size and opacity are carried in the layout so
 * the renderer does not need the settings object.
 */
data class TouchLayout(
    val dpad: ControlCircle,
    val fire: ControlCircle,
    val overlay: Boolean,
    val opacity: Int = 70,
    val sizeStep: Int = 1,
    val joystickZone: ControlArea = ControlArea.EMPTY,
    /** NES-style A and B buttons drawn inside the fire area; both fire. */
    val abButtons: Boolean = false,
) {
    /** B sits lower-left and A upper-right, as on the NES pad. */
    val buttonB: ControlCircle
        get() = ControlCircle(
            centerX = fire.centerX - fire.radius * AB_OFFSET_X / 100,
            centerY = fire.centerY + fire.radius * AB_OFFSET_Y / 100,
            radius = fire.radius * AB_RADIUS / 100,
        )

    val buttonA: ControlCircle
        get() = ControlCircle(
            centerX = fire.centerX + fire.radius * AB_OFFSET_X / 100,
            centerY = fire.centerY - fire.radius * AB_OFFSET_Y / 100,
            radius = fire.radius * AB_RADIUS / 100,
        )

    /** Whether a touch lands on fire; A/B mode also accepts the button rims. */
    fun fireContains(x: Int, y: Int): Boolean =
        fire.contains(x, y) ||
            (abButtons && (buttonA.contains(x, y) || buttonB.contains(x, y)))

    /**
     * Opacity mapped to paint alpha. Overlay controls keep the old 47% ceiling
     * so the game stays legible; the opacity option still scales up to it.
     */
    val alpha: Int
        get() {
            val requested = (opacity * 255 / 100).coerceIn(0, 255)
            return if (overlay) minOf(requested, OVERLAY_ALPHA) else requested
        }

    companion object {
        private const val AB_OFFSET_X = 52
        private const val AB_OFFSET_Y = 30
        private const val AB_RADIUS = 48
        private const val DPAD_RADIUS_DP = 56
        private const val FIRE_RADIUS_DP = 34
        private const val EDGE_DP = 8
        private const val OVERLAY_EDGE_DP = 12
        private const val OVERLAY_ALPHA = 120
        private const val JOYSTICK_ZONE_FRACTION = 0.56f
        private const val SMALL_SIZE_STEP = 0

        fun compute(
            screenWidth: Int,
            screenHeight: Int,
            viewport: Viewport,
            density: Float,
            joystick: Boolean = false,
            sizeStep: Int = 1,
            opacity: Int = 70,
            fireLeft: Boolean = false,
        ): TouchLayout {
            require(screenWidth > 0 && screenHeight > 0) { "screen must be non-empty" }
            require(density > 0f) { "density must be positive" }

            val scale = sizeScale(sizeStep)
            val minScale = sizeScale(SMALL_SIZE_STEP)
            val cleanOpacity = opacity.coerceIn(30, 100)
            val dpadBase = (DPAD_RADIUS_DP * density * scale).toInt().coerceAtLeast(1)
            val fireBase = (FIRE_RADIUS_DP * density * scale).toInt().coerceAtLeast(1)
            val minDpadBase = (DPAD_RADIUS_DP * density * minScale)
                .toInt()
                .coerceAtLeast(1)
            val minFireBase = (FIRE_RADIUS_DP * density * minScale)
                .toInt()
                .coerceAtLeast(1)
            val minSide = maxOf(minDpadBase, minFireBase)
            val edge = (EDGE_DP * density).toInt().coerceAtLeast(1)
            val overlayEdge = (OVERLAY_EDGE_DP * density).toInt().coerceAtLeast(1)

            if (joystick) {
                return joystickLayout(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    viewport = viewport,
                    dpadBase = dpadBase,
                    fireBase = fireBase,
                    minDpadBase = minDpadBase,
                    minFireBase = minFireBase,
                    edge = edge,
                    overlayEdge = overlayEdge,
                    opacity = cleanOpacity,
                    sizeStep = sizeStep.coerceIn(0, 2),
                    fireLeft = fireLeft,
                )
            }

            val leftMargin = viewport.left
            val rightMargin = screenWidth - (viewport.left + viewport.width)
            val margin = minOf(leftMargin, rightMargin)
            val verticalLimit = (screenHeight - edge * 2) / 2
            val sideFits = margin >= minSide * 2 + edge * 2 && verticalLimit >= minSide

            if (sideFits) {
                val radius = minOf(dpadBase, (margin - edge * 2) / 2, verticalLimit)
                val fireRadius = minOf(fireBase, (margin - edge * 2) / 2, verticalLimit)
                val leftDpad = ControlCircle(
                    centerX = leftMargin / 2,
                    centerY = screenHeight / 2,
                    radius = radius,
                )
                val rightFire = ControlCircle(
                    centerX = viewport.left + viewport.width + rightMargin / 2,
                    centerY = screenHeight / 2,
                    radius = fireRadius,
                )
                return TouchLayout(
                    dpad = if (fireLeft) leftDpad.mirrored(screenWidth) else leftDpad,
                    fire = if (fireLeft) rightFire.mirrored(screenWidth) else rightFire,
                    overlay = false,
                    opacity = cleanOpacity,
                    sizeStep = sizeStep.coerceIn(0, 2),
                )
            }

            val maxRadius = minOf(
                dpadBase,
                (viewport.width - overlayEdge * 3) / 4,
                (viewport.height - overlayEdge * 3) / 4,
            ).coerceAtLeast(1)
            val dpadRadius = maxRadius
            val fireRadius = minOf(fireBase, maxRadius).coerceAtLeast(1)
            val centerY = viewport.top + viewport.height - overlayEdge - dpadRadius
            val leftDpad = ControlCircle(
                centerX = viewport.left + overlayEdge + dpadRadius,
                centerY = centerY,
                radius = dpadRadius,
            )
            val rightFire = ControlCircle(
                centerX = viewport.left + viewport.width - overlayEdge - fireRadius,
                centerY = viewport.top + viewport.height - overlayEdge - fireRadius,
                radius = fireRadius,
            )
            return TouchLayout(
                dpad = if (fireLeft) leftDpad.mirrored(screenWidth) else leftDpad,
                fire = if (fireLeft) rightFire.mirrored(screenWidth) else rightFire,
                overlay = true,
                opacity = cleanOpacity,
                sizeStep = sizeStep.coerceIn(0, 2),
            )
        }

        /**
         * Two-player layout: P1 owns the left side and P2 the right. Each
         * player's D-pad/fire pair goes side by side or stacked in the margin
         * when possible, otherwise splits the bottom of its half.
         */
        fun computeTwoPlayer(
            screenWidth: Int,
            screenHeight: Int,
            viewport: Viewport,
            density: Float,
            player: Int,
            sizeStep: Int = 1,
            opacity: Int = 70,
        ): TouchLayout {
            require(screenWidth > 0 && screenHeight > 0) { "screen must be non-empty" }
            require(density > 0f) { "density must be positive" }
            require(player in 0..1) { "player must be 0 or 1" }

            val scale = sizeScale(sizeStep)
            val minScale = sizeScale(SMALL_SIZE_STEP)
            val size = sizeStep.coerceIn(0, 2)
            val cleanOpacity = opacity.coerceIn(30, 100)
            val dpadBase = (DPAD_RADIUS_DP * density * scale).toInt().coerceAtLeast(1)
            val fireBase = (FIRE_RADIUS_DP * density * scale).toInt().coerceAtLeast(1)
            val minDpadBase = (DPAD_RADIUS_DP * density * minScale)
                .toInt()
                .coerceAtLeast(1)
            val minFireBase = (FIRE_RADIUS_DP * density * minScale)
                .toInt()
                .coerceAtLeast(1)
            val edge = (EDGE_DP * density).toInt().coerceAtLeast(1)
            val leftMargin = viewport.left
            val rightMargin = screenWidth - (viewport.left + viewport.width)
            val margin = if (player == 0) leftMargin else rightMargin
            val dpadRadius = minOf(dpadBase, (margin - edge * 2).coerceAtLeast(1) / 2)
                .coerceAtLeast(1)
            val fireRadius = minOf(fireBase, (margin - edge * 2).coerceAtLeast(1) / 2)
                .coerceAtLeast(1)
            val marginUsable =
                dpadRadius >= minDpadBase && fireRadius >= minFireBase
            val bottom = screenHeight - edge

            val bottomFits =
                screenHeight >= maxOf(dpadRadius, fireRadius) * 2 + edge * 2
            val horizontalFits = marginUsable && bottomFits &&
                margin >= (dpadRadius + fireRadius) * 2 + edge * 3
            val verticalFits = marginUsable &&
                margin >= maxOf(dpadRadius, fireRadius) * 2 + edge * 2 &&
                screenHeight >= (dpadRadius + fireRadius) * 2 + edge * 3

            if (horizontalFits) {
                val dpad = ControlCircle(
                    centerX = if (player == 0) {
                        edge + dpadRadius
                    } else {
                        screenWidth - edge - dpadRadius
                    },
                    centerY = bottom - dpadRadius,
                    radius = dpadRadius,
                )
                val fire = ControlCircle(
                    centerX = if (player == 0) {
                        margin - edge - fireRadius
                    } else {
                        screenWidth - margin + edge + fireRadius
                    },
                    centerY = bottom - fireRadius,
                    radius = fireRadius,
                )
                return TouchLayout(
                    dpad = dpad,
                    fire = fire,
                    overlay = false,
                    opacity = cleanOpacity,
                    sizeStep = size,
                )
            }

            if (verticalFits) {
                val x = if (player == 0) margin / 2 else screenWidth - margin / 2
                val fire = ControlCircle(
                    centerX = x,
                    centerY = bottom - fireRadius,
                    radius = fireRadius,
                )
                val dpad = ControlCircle(
                    centerX = x,
                    centerY = fire.centerY - fireRadius - edge - dpadRadius,
                    radius = dpadRadius,
                )
                return TouchLayout(
                    dpad = dpad,
                    fire = fire,
                    overlay = false,
                    opacity = cleanOpacity,
                    sizeStep = size,
                )
            }

            val half = screenWidth / 2
            val gap = edge * 2
            val spaceForTwo = ((half - gap) / 2).coerceAtLeast(2)
            val overlayEdge = (OVERLAY_EDGE_DP * density).toInt().coerceAtLeast(1)
            // Same half-viewport reserve as the single-player overlay, so both
            // controls stay on screen even when the two islands share a row.
            val overlayHeightLimit =
                ((viewport.height - overlayEdge * 3) / 4).coerceAtLeast(1)
            val overlayFireRadius = minOf(
                fireBase,
                spaceForTwo / 2,
                overlayHeightLimit,
            ).coerceAtLeast(1)
            val overlayDpadRadius = minOf(
                dpadBase,
                spaceForTwo - overlayFireRadius,
                overlayHeightLimit,
            ).coerceAtLeast(1)
            val overlayBottom = viewport.top + viewport.height - overlayEdge
            val dpad = ControlCircle(
                centerX = if (player == 0) {
                    edge + overlayDpadRadius
                } else {
                    screenWidth - edge - overlayDpadRadius
                },
                centerY = overlayBottom - overlayDpadRadius,
                radius = overlayDpadRadius,
            )
            val fire = ControlCircle(
                centerX = if (player == 0) {
                    half - edge - overlayFireRadius
                } else {
                    half + edge + overlayFireRadius
                },
                centerY = overlayBottom - overlayFireRadius,
                radius = overlayFireRadius,
            )
            return TouchLayout(
                dpad = dpad,
                fire = fire,
                overlay = true,
                opacity = cleanOpacity,
                sizeStep = size,
            )
        }

        private fun joystickLayout(
            screenWidth: Int,
            screenHeight: Int,
            viewport: Viewport,
            dpadBase: Int,
            fireBase: Int,
            minDpadBase: Int,
            minFireBase: Int,
            edge: Int,
            overlayEdge: Int,
            opacity: Int,
            sizeStep: Int,
            fireLeft: Boolean,
        ): TouchLayout {
            val leftMargin = viewport.left
            val rightMargin = screenWidth - (viewport.left + viewport.width)
            val verticalLimit = (screenHeight - edge * 2) / 2
            val fireMargin = if (fireLeft) leftMargin else rightMargin
            val joystickMargin = if (fireLeft) rightMargin else leftMargin
            val fireSideFits =
                fireMargin >= minFireBase * 2 + edge * 2 && verticalLimit >= minFireBase
            val joystickSideFits =
                joystickMargin >= minDpadBase * 2 + edge * 2 && verticalLimit >= minDpadBase

            val fire = if (fireSideFits) {
                ControlCircle(
                    centerX = if (fireLeft) {
                        leftMargin / 2
                    } else {
                        viewport.left + viewport.width + rightMargin / 2
                    },
                    centerY = screenHeight / 2,
                    radius = minOf(fireBase, (fireMargin - edge * 2) / 2, verticalLimit),
                )
            } else {
                val radius = minOf(
                    fireBase,
                    (viewport.width - overlayEdge * 3) / 4,
                    (viewport.height - overlayEdge * 3) / 4,
                ).coerceAtLeast(1)
                ControlCircle(
                    centerX = if (fireLeft) {
                        viewport.left + overlayEdge + radius
                    } else {
                        viewport.left + viewport.width - overlayEdge - radius
                    },
                    centerY = viewport.top + viewport.height - overlayEdge - radius,
                    radius = radius,
                )
            }

            val hint = if (joystickSideFits) {
                ControlCircle(
                    centerX = if (fireLeft) {
                        viewport.left + viewport.width + rightMargin / 2
                    } else {
                        leftMargin / 2
                    },
                    centerY = screenHeight / 2,
                    radius = minOf(dpadBase, (joystickMargin - edge * 2) / 2, verticalLimit),
                )
            } else {
                val radius = minOf(
                    dpadBase,
                    (viewport.width - overlayEdge * 3) / 4,
                    (viewport.height - overlayEdge * 3) / 4,
                ).coerceAtLeast(1)
                ControlCircle(
                    centerX = if (fireLeft) {
                        viewport.left + viewport.width - overlayEdge - radius
                    } else {
                        viewport.left + overlayEdge + radius
                    },
                    centerY = viewport.top + viewport.height - overlayEdge - radius,
                    radius = radius,
                )
            }

            val split = (screenWidth * JOYSTICK_ZONE_FRACTION).toInt()
            val zone = if (fireLeft) {
                ControlArea(
                    left = screenWidth - split,
                    top = 0,
                    right = screenWidth,
                    bottom = screenHeight,
                )
            } else {
                ControlArea(
                    left = 0,
                    top = 0,
                    right = split,
                    bottom = screenHeight,
                )
            }

            return TouchLayout(
                dpad = hint,
                fire = fire,
                overlay = !fireSideFits || !joystickSideFits,
                opacity = opacity,
                sizeStep = sizeStep,
                joystickZone = zone,
            )
        }

        private fun sizeScale(sizeStep: Int): Float = when (sizeStep.coerceIn(0, 2)) {
            0 -> 0.8f
            2 -> 1.25f
            else -> 1f
        }

        private fun ControlCircle.mirrored(screenWidth: Int): ControlCircle =
            copy(centerX = screenWidth - centerX)
    }
}
