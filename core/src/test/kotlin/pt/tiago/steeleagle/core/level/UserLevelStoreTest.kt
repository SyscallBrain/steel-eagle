package pt.tiago.steeleagle.core.level

import pt.tiago.steeleagle.core.editor.EditorCommand
import pt.tiago.steeleagle.core.editor.EditorEffect
import pt.tiago.steeleagle.core.editor.EditorSession
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserLevelStoreTest {

    @Test
    fun `levels round trip through the text format`() {
        val level = Level(
            name = "My Level",
            enemies = "B*10 F*10",
            map = EditorLevels.mapWith('B'),
        )

        val parsed = LevelParser.parse(LevelWriter.serialize(level))

        assertEquals(level.name, parsed.name)
        assertEquals(level.enemies, parsed.enemies)
        assertEquals(level.map, parsed.map)
    }

    @Test
    fun `saving creates numbered files and lists them by name`() {
        withStore { store ->
            val first = store.save(null, level("First"))
            val second = store.save(null, level("Second"))

            assertEquals("user_001.txt", first)
            assertEquals("user_002.txt", second)
            val list = store.list()
            assertEquals(listOf("First", "Second"), list.map { it.name })
            assertEquals(first, list[0].fileName)
        }
    }

    @Test
    fun `saving over a file keeps its number`() {
        withStore { store ->
            val fileName = store.save(null, level("First"))
            val again = store.save(fileName, level("Renamed"))

            assertEquals(fileName, again)
            assertEquals(1, store.list().size)
            assertEquals("Renamed", store.list()[0].name)
            assertEquals("user_002.txt", store.nextFileName())
        }
    }

    @Test
    fun `deleting removes the file and reuses numeric gaps`() {
        withStore { store ->
            val first = store.save(null, level("First"))
            store.save(null, level("Second"))

            assertTrue(store.delete(first))
            assertFalse(store.delete(first))

            assertEquals(listOf("Second"), store.list().map { it.name })
            assertEquals("user_001.txt", store.nextFileName())
        }
    }

    @Test
    fun `corrupt and unrelated files are ignored`() {
        withStore { store, directory ->
            val good = store.save(null, level("Good"))
            File(directory, "user_002.txt").writeText("not a level")
            File(directory, "notes.txt").writeText("hello")
            File(directory, "user_bad.txt").writeText("hello")

            val list = store.list()

            assertEquals(listOf("Good"), list.map { it.name })
            assertEquals(good, list[0].fileName)
            assertNull(store.read("../evil.txt"))
            assertNull(store.read("notes.txt"))
        }
    }

    @Test
    fun `an editor level saves and loads without losing cells`() {
        withStore { store ->
            val session = EditorSession()
            session.paintAt(3, 3)
            session.handle(EditorCommand.RIGHT)
            session.paintAt(4, 3)
            session.handle(EditorCommand.BACK)
            session.activateMenu(session.menuLabels.indexOf("SAVE"))
            session.nameEntry.setText("ROUND TRIP")
            val effect = session.selectNameCell(38)

            val save = effect as? EditorEffect.Save
            assertNotNull(save)
            val fileName = store.save(save.fileName, save.level)
            val loaded = store.list().single()

            assertEquals("ROUND TRIP", loaded.name)
            assertEquals(save.level.map, loaded.level.map)
            assertEquals(save.level.enemies, loaded.level.enemies)
            assertTrue(LevelValidator.validate(loaded.level).isValid)
        }
    }

    @Test
    fun `files sort by number, not by name`() {
        withStore { store ->
            store.save("user_100.txt", level("Hundred"))
            store.save("user_099.txt", level("Ninety nine"))
            store.save("user_002.txt", level("Two"))

            assertEquals(
                listOf("Two", "Ninety nine", "Hundred"),
                store.list().map { it.name },
            )
            assertEquals("user_001.txt", store.nextFileName())
        }
    }

    @Test
    fun `a gameplay invalid file stays listed so the editor can repair it`() {
        withStore { store ->
            val level = Level(
                name = "Broken",
                enemies = "B*19",
                map = EditorLevels.mapWith('.'),
            )
            val fileName = store.save(null, level)

            val loaded = store.list().single()
            assertEquals(fileName, loaded.fileName)
            assertFalse(LevelValidator.validate(loaded.level).isValid)
        }
    }

    @Test
    fun `a save leaves no temporary file behind`() {
        withStore { store, directory ->
            val fileName = store.save(null, level("Clean"))

            assertEquals(listOf(fileName), directory.list()?.sorted())
        }
    }

    @Test
    fun `the store creates a missing directory`() {
        val parent = Files.createTempDirectory("steeleagle-levels").toFile()
        val directory = File(parent, "nested/levels")
        try {
            val store = UserLevelStore(directory)

            val fileName = store.save(null, level("Nested"))

            assertTrue(File(directory, fileName).isFile)
            assertNotNull(store.read(fileName))
        } finally {
            parent.deleteRecursively()
        }
    }

    private fun level(name: String) = Level(
        name = name,
        enemies = "B*20",
        map = EditorLevels.mapWith('.'),
    )

    private fun withStore(block: (UserLevelStore) -> Unit) {
        val directory = Files.createTempDirectory("steeleagle-levels").toFile()
        try {
            block(UserLevelStore(directory))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun withStore(block: (UserLevelStore, File) -> Unit) {
        val directory = Files.createTempDirectory("steeleagle-levels").toFile()
        try {
            block(UserLevelStore(directory), directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}

/** 26x26 helper maps for the level-store tests. */
internal object EditorLevels {
    fun mapWith(char: Char, row: Int = 0): List<String> =
        List(26) { index ->
            if (index == row) char.toString().repeat(26) else ".".repeat(26)
        }
}
