package pt.tiago.steeleagle.render

import android.content.Context
import android.graphics.Bitmap
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.sprite.AsciiSpriteParser
import pt.tiago.steeleagle.core.sprite.EnemySprites
import pt.tiago.steeleagle.core.sprite.SpriteFrame
import pt.tiago.steeleagle.core.sprite.TankSprites

/**
 * In-memory atlas built at startup from the ASCII sprite files. No image files
 * are shipped in the APK.
 */
class SpriteAtlas private constructor(
    private val sprites: Map<String, Bitmap>,
) {
    fun bitmap(name: String): Bitmap =
        sprites[name] ?: throw IllegalArgumentException("sprite '$name' not found")

    fun bitmapOrNull(name: String): Bitmap? = sprites[name]

    val size: Int
        get() = sprites.size

    companion object {
        private const val DIRECTORY = "sprites"

        fun load(context: Context): SpriteAtlas {
            val assets = context.assets
            val files = assets.list(DIRECTORY)
                ?.filter { it.endsWith(".txt") }
                ?.sorted()
                .orEmpty()
            require(files.isNotEmpty()) { "no sprite files in assets/$DIRECTORY" }

            val frames = files.flatMap { file ->
                assets.open("$DIRECTORY/$file").bufferedReader().use { reader ->
                    AsciiSpriteParser.parse(reader.readText())
                }
            }

            val bitmaps = LinkedHashMap<String, Bitmap>()
            for (frame in frames) {
                bitmaps[frame.name] = frame.toBitmap()
            }
            addTankRotations(bitmaps, frames)
            return SpriteAtlas(bitmaps)
        }

        private fun addTankRotations(
            bitmaps: MutableMap<String, Bitmap>,
            frames: List<SpriteFrame>,
        ) {
            addRotations(bitmaps, frames, "tank_up_", TankSprites::name)
            addRotations(bitmaps, frames, "enemy_up_", EnemySprites::name)
        }

        private fun addRotations(
            bitmaps: MutableMap<String, Bitmap>,
            frames: List<SpriteFrame>,
            prefix: String,
            nameFor: (Dir, Int) -> String,
        ) {
            for (frame in frames.filter { it.name.startsWith(prefix) }) {
                val frameIndex = frame.name.substringAfterLast('_').toInt()
                var rotated = frame
                for (direction in listOf(Dir.RIGHT, Dir.DOWN, Dir.LEFT)) {
                    rotated = AsciiSpriteParser.rotate90(rotated)
                    bitmaps[nameFor(direction, frameIndex)] = rotated.toBitmap()
                }
            }
        }

        private fun SpriteFrame.toBitmap(): Bitmap {
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val row = rows[y]
                for (x in 0 until width) {
                    pixels[y * width + x] = Palette.colorFor(row[x])
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }
}
