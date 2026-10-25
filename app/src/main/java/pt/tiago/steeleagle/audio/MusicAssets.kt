package pt.tiago.steeleagle.audio

import android.content.Context

enum class MusicId(val fileName: String) {
    LEVEL_START("level_start.txt"),
    GAME_OVER("game_over.txt"),
    TALLY("tally.txt"),
    TITLE("title.txt"),
}

/** Loads the original note sequences from assets; a bad file is skipped. */
object MusicAssets {
    fun load(context: Context): Map<MusicId, MusicSequence> {
        val result = mutableMapOf<MusicId, MusicSequence>()
        for (id in MusicId.entries) {
            runCatching {
                context.assets.open("music/${id.fileName}").bufferedReader().use {
                    MusicSequence.parse(it.readText())
                }
            }.onSuccess { result[id] = it }
        }
        return result
    }
}
