package pt.tiago.steeleagle.core.editor

/** Brush footprint in level cells; one cell is the 8x8 px terrain block. */
enum class BrushShape(val width: Int, val height: Int) {
    FULL(2, 2),
    WIDE(2, 1),
    TALL(1, 2),
    QUARTER(1, 1),
}

/**
 * Construction palette. Brick and steel have whole, half
 * and quarter patterns; water, trees and ice are whole tiles; the eraser
 * clears a whole tile.
 */
enum class Brush(val terrain: Char, val shape: BrushShape) {
    BRICK_FULL('B', BrushShape.FULL),
    BRICK_WIDE('B', BrushShape.WIDE),
    BRICK_TALL('B', BrushShape.TALL),
    BRICK_QUARTER('B', BrushShape.QUARTER),
    STEEL_FULL('S', BrushShape.FULL),
    STEEL_WIDE('S', BrushShape.WIDE),
    STEEL_TALL('S', BrushShape.TALL),
    STEEL_QUARTER('S', BrushShape.QUARTER),
    WATER('W', BrushShape.FULL),
    TREES('T', BrushShape.FULL),
    ICE('I', BrushShape.FULL),
    ERASER('.', BrushShape.FULL),
    ;

    val width: Int
        get() = shape.width

    val height: Int
        get() = shape.height

    val label: String
        get() = when (this) {
            BRICK_FULL -> "BRICK"
            BRICK_WIDE -> "BRICK WIDE"
            BRICK_TALL -> "BRICK TALL"
            BRICK_QUARTER -> "BRICK 1/4"
            STEEL_FULL -> "STEEL"
            STEEL_WIDE -> "STEEL WIDE"
            STEEL_TALL -> "STEEL TALL"
            STEEL_QUARTER -> "STEEL 1/4"
            WATER -> "WATER"
            TREES -> "TREES"
            ICE -> "ICE"
            ERASER -> "ERASE"
        }
}
