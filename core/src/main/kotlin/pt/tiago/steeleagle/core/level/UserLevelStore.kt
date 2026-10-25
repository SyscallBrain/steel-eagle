package pt.tiago.steeleagle.core.level

import java.io.File

/** One level saved by the editor. */
data class UserLevel(
    val fileName: String,
    val name: String,
    val level: Level,
    val text: String,
)

/**
 * User levels under `filesDir/levels`, in the same text format as the built-in
 * stages. Files are named `user_NNN.txt`; unreadable files are skipped so one
 * bad save cannot break the level select.
 */
class UserLevelStore(private val directory: File) {

    fun list(): List<UserLevel> = files().mapNotNull { read(it.name) }

    fun directory(): File = directory

    fun read(fileName: String): UserLevel? {
        if (!isUserFile(fileName)) return null
        val file = File(directory, fileName)
        if (!file.isFile) return null
        return runCatching {
            val text = file.readText()
            val level = LevelParser.parse(text)
            UserLevel(fileName, level.name, level, text)
        }.getOrNull()
    }

    /**
     * Writes [level] to [fileName], or to the next free name when it is null.
     * Returns the file name written.
     */
    fun save(fileName: String?, level: Level): String {
        val target = fileName?.takeIf { isUserFile(it) } ?: nextFileName()
        if (!directory.isDirectory) directory.mkdirs()
        require(directory.isDirectory) { "cannot create ${directory.path}" }
        val file = File(directory, target)
        val temp = File(directory, "$target.tmp")
        try {
            temp.writeText(LevelWriter.serialize(level))
            if (!temp.renameTo(file)) file.writeText(temp.readText())
        } finally {
            if (temp.exists()) temp.delete()
        }
        return target
    }

    fun delete(fileName: String): Boolean {
        if (!isUserFile(fileName)) return false
        return File(directory, fileName).delete()
    }

    fun nextFileName(): String {
        val used = files().mapNotNull { indexOf(it.name) }.toSet()
        var index = 1
        while (index in used) index++
        return "$PREFIX${index.toString().padStart(3, '0')}$SUFFIX"
    }

    private fun files(): List<File> {
        val all = directory.listFiles() ?: return emptyList()
        return all
            .filter { it.isFile && isUserFile(it.name) }
            .sortedBy { indexOf(it.name) ?: Int.MAX_VALUE }
    }

    private fun indexOf(fileName: String): Int? =
        USER_FILE.matchEntire(fileName)?.groupValues?.get(1)?.toIntOrNull()

    private fun isUserFile(fileName: String): Boolean =
        USER_FILE.matches(fileName)

    private companion object {
        const val PREFIX = "user_"
        const val SUFFIX = ".txt"
        val USER_FILE = Regex("""user_(\d{3,6})\.txt""")
    }
}
