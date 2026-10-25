package pt.tiago.steeleagle.render

import android.graphics.Bitmap
import android.graphics.Canvas
import pt.tiago.steeleagle.core.world.Terrain
import pt.tiago.steeleagle.core.world.TerrainGrid

/**
 * Caches the 208x208 terrain into a base layer and a tree layer. The tree layer
 * is drawn over tanks and bullets, as in the original. Rebuilds only when the
 * terrain changes or the water animation advances.
 */
class TerrainRenderer(private val atlas: SpriteAtlas) {

    private val base = Bitmap.createBitmap(FIELD_SIZE, FIELD_SIZE, Bitmap.Config.ARGB_8888)
    private val trees = Bitmap.createBitmap(FIELD_SIZE, FIELD_SIZE, Bitmap.Config.ARGB_8888)
    private val baseCanvas = Canvas(base)
    private val treesCanvas = Canvas(trees)

    private var cachedVersion = -1
    private var cachedWaterFrame = -1

    fun update(terrain: TerrainGrid, tick: Long) {
        val waterFrame = ((tick / WATER_FRAME_TICKS) % 2).toInt()
        if (terrain.version == cachedVersion && waterFrame == cachedWaterFrame) return

        rebuild(terrain, waterFrame)
        cachedVersion = terrain.version
        cachedWaterFrame = waterFrame
    }

    fun drawBase(canvas: Canvas) {
        canvas.drawBitmap(base, 0f, 0f, null)
    }

    fun drawTrees(canvas: Canvas) {
        canvas.drawBitmap(trees, 0f, 0f, null)
    }

    private fun rebuild(terrain: TerrainGrid, waterFrame: Int) {
        base.eraseColor(0)
        trees.eraseColor(0)

        val brick = atlas.bitmap("terrain_brick")
        val steel = atlas.bitmap("terrain_steel")
        val water = atlas.bitmap(if (waterFrame == 0) "terrain_water_0" else "terrain_water_1")
        val tree = atlas.bitmap("terrain_trees")
        val ice = atlas.bitmap("terrain_ice")

        for (cellY in 0 until TerrainGrid.SIZE) {
            for (cellX in 0 until TerrainGrid.SIZE) {
                val x = (cellX * CELL_PIXELS).toFloat()
                val y = (cellY * CELL_PIXELS).toFloat()
                when (terrain.get(cellX, cellY)) {
                    Terrain.BRICK -> baseCanvas.drawBitmap(brick, x, y, null)
                    Terrain.STEEL -> baseCanvas.drawBitmap(steel, x, y, null)
                    Terrain.WATER -> baseCanvas.drawBitmap(water, x, y, null)
                    Terrain.ICE -> baseCanvas.drawBitmap(ice, x, y, null)
                    Terrain.TREES -> treesCanvas.drawBitmap(tree, x, y, null)
                    Terrain.EMPTY -> Unit
                }
            }
        }
    }

    private companion object {
        const val FIELD_SIZE = 208
        const val CELL_PIXELS = 4
        const val WATER_FRAME_TICKS = 30L
    }
}
