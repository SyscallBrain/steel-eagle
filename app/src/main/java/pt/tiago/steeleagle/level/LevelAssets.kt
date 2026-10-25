package pt.tiago.steeleagle.level

import android.content.Context
import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.level.LevelParser

/** Loads level text from the APK assets. */
object LevelAssets {
    fun load(context: Context, fileName: String): Level =
        context.assets.open("levels/$fileName").bufferedReader().use { reader ->
            LevelParser.parse(reader.readText())
        }

    fun loadAll(context: Context): List<Level> {
        val files = context.assets.list("levels")
            ?.filter { it.endsWith(".txt") }
            ?.sorted()
            .orEmpty()
        return files.map { load(context, it) }
    }
}
