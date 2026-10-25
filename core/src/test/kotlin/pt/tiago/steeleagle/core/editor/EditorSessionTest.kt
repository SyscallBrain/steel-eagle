package pt.tiago.steeleagle.core.editor

import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.level.LevelValidator
import pt.tiago.steeleagle.core.level.UserLevel
import pt.tiago.steeleagle.core.rules.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorSessionTest {

    @Test
    fun `a new editor starts on the template with twenty basics`() {
        val session = EditorSession()

        assertEquals(EditorMode.FIELD, session.mode)
        assertFalse(session.dirty)
        assertEquals(EditorMap.template().rows(), session.map.rows())
        assertEquals(20, session.enemies.total)
        assertEquals("B*20", session.enemies.spec())
        assertEquals(Brush.BRICK_FULL, session.brush)
    }

    @Test
    fun `the cursor moves and fire paints the field`() {
        val session = EditorSession()

        session.handle(EditorCommand.RIGHT)
        session.handle(EditorCommand.DOWN)
        assertEquals(13, session.cursorX)
        assertEquals(13, session.cursorY)

        session.handle(EditorCommand.FIRE)

        assertEquals('B', session.map.get(13, 13))
        assertTrue(session.dirty)
    }

    @Test
    fun `the cursor stops at the field border`() {
        val session = EditorSession()

        repeat(40) {
            session.handle(EditorCommand.LEFT)
            session.handle(EditorCommand.UP)
        }
        assertEquals(0, session.cursorX)
        assertEquals(0, session.cursorY)

        repeat(40) {
            session.handle(EditorCommand.RIGHT)
            session.handle(EditorCommand.DOWN)
        }
        assertEquals(25, session.cursorX)
        assertEquals(25, session.cursorY)
    }

    @Test
    fun `back opens the menu and back again returns to the field`() {
        val session = EditorSession()

        session.handle(EditorCommand.BACK)
        assertEquals(EditorMode.MENU, session.mode)

        session.handle(EditorCommand.BACK)
        assertEquals(EditorMode.FIELD, session.mode)
    }

    @Test
    fun `the brush palette selects every brush`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("BRUSH"))
        assertEquals(EditorMode.BRUSH, session.mode)

        session.handle(EditorCommand.RIGHT)
        assertEquals(Brush.BRICK_WIDE, session.brush)
        session.handle(EditorCommand.DOWN)
        assertEquals(Brush.STEEL_WIDE, session.brush)
        session.handle(EditorCommand.LEFT)
        assertEquals(Brush.STEEL_FULL, session.brush)
        session.handle(EditorCommand.UP)
        assertEquals(Brush.BRICK_FULL, session.brush)
        session.handle(EditorCommand.DOWN)
        session.handle(EditorCommand.DOWN)
        assertEquals(Brush.WATER, session.brush)
        session.handle(EditorCommand.RIGHT)
        assertEquals(Brush.TREES, session.brush)
        session.handle(EditorCommand.RIGHT)
        assertEquals(Brush.ICE, session.brush)
        session.handle(EditorCommand.UP)
        assertEquals(Brush.STEEL_TALL, session.brush)
        session.handle(EditorCommand.LEFT)
        session.handle(EditorCommand.LEFT)
        assertEquals(Brush.STEEL_FULL, session.brush)

        session.handle(EditorCommand.FIRE)
        assertEquals(EditorMode.FIELD, session.mode)
        session.handle(EditorCommand.FIRE)
        assertEquals('S', session.map.get(12, 12))
    }

    @Test
    fun `the brush palette stops at its edges`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("BRUSH"))

        session.selectBrush(Brush.WATER.ordinal)
        session.handle(EditorCommand.DOWN)
        assertEquals(Brush.WATER, session.brush)

        session.selectBrush(Brush.ICE.ordinal)
        session.handle(EditorCommand.DOWN)
        assertEquals(Brush.ICE, session.brush)
        session.handle(EditorCommand.RIGHT)
        assertEquals(Brush.ERASER, session.brush)
        session.handle(EditorCommand.RIGHT)
        assertEquals(Brush.ERASER, session.brush)

        session.selectBrush(Brush.BRICK_FULL.ordinal)
        session.handle(EditorCommand.LEFT)
        assertEquals(Brush.BRICK_FULL, session.brush)
        session.handle(EditorCommand.UP)
        assertEquals(Brush.BRICK_FULL, session.brush)
    }

    @Test
    fun `a dirty new asks before discarding`() {
        val session = EditorSession()
        session.handle(EditorCommand.FIRE)
        session.handle(EditorCommand.BACK)

        session.activateMenu(session.menuIndex("NEW"))

        assertEquals(EditorMode.CONFIRM, session.mode)
        assertEquals(EditorConfirm.NEW, session.confirm)
        assertEquals(1, session.confirmIndex)

        session.handle(EditorCommand.FIRE) // NO
        assertEquals(EditorMode.MENU, session.mode)
        assertTrue(session.dirty)

        session.activateMenu(session.menuIndex("NEW"))
        session.handle(EditorCommand.LEFT) // YES
        session.handle(EditorCommand.FIRE)

        assertEquals(EditorMode.FIELD, session.mode)
        assertFalse(session.dirty)
        assertEquals(EditorMap.template().rows(), session.map.rows())
    }

    @Test
    fun `save asks for a name and returns a save effect`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("SAVE"))

        assertEquals(EditorMode.NAME, session.mode)
        assertEquals("USER LEVEL", session.nameEntry.text)

        session.nameEntry.setText("MY LEVEL")
        val effect = session.selectNameCell(38)

        val save = assertIs<EditorEffect.Save>(effect)
        assertEquals(null, save.fileName)
        assertEquals("MY LEVEL", save.level.name)
        assertEquals("B*20", save.level.enemies)
        assertTrue(LevelValidator.validate(save.level).isValid)
        assertEquals("MY LEVEL", session.name)
    }

    @Test
    fun `saving an open level keeps its file`() {
        val session = EditorSession()
        session.onSaved("user_003.txt", "OLD NAME")
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("SAVE"))
        session.nameEntry.setText("NEW NAME")

        val effect = assertIs<EditorEffect.Save>(session.selectNameCell(38))

        assertEquals("user_003.txt", effect.fileName)
        assertEquals("NEW NAME", effect.level.name)
    }

    @Test
    fun `a blocked spawn cannot be saved or tested`() {
        val session = EditorSession()
        session.paintAt(0, 0)
        session.handle(EditorCommand.BACK)

        session.activateMenu(session.menuIndex("TEST"))
        assertEquals(EditorMode.MESSAGES, session.mode)
        assertEquals("CANNOT TEST", session.infoTitle)
        assertTrue("ENEMY SPAWN 1 BLOCKED" in session.messages)

        session.handle(EditorCommand.FIRE)
        session.activateMenu(session.menuIndex("SAVE"))
        session.selectNameCell(38)

        assertEquals(EditorMode.MESSAGES, session.mode)
        assertEquals("CANNOT SAVE", session.infoTitle)
        assertTrue("ENEMY SPAWN 1 BLOCKED" in session.messages)
    }

    @Test
    fun `test hands a valid level to the app`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)

        val effect = session.activateMenu(session.menuIndex("TEST"))

        val test = assertIs<EditorEffect.Test>(effect)
        assertEquals("USER LEVEL", test.level.name)
        assertEquals(20, test.level.enemies.removePrefix("B*").toInt())
        assertEquals(EditorMode.MENU, session.mode)
    }

    @Test
    fun `the enemy screen adjusts counts without passing twenty`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("ENEMIES"))

        session.adjustEnemy(EnemyType.BASIC.ordinal, -2)
        session.handle(EditorCommand.DOWN)
        session.handle(EditorCommand.RIGHT)
        session.handle(EditorCommand.RIGHT)

        assertEquals(EnemyType.FAST, EnemyType.entries[session.enemyIndex])
        assertEquals(2, session.enemies.count(EnemyType.FAST))
        assertEquals(18, session.enemies.count(EnemyType.BASIC))
        assertEquals(20, session.enemies.total)
        assertTrue(session.dirty)
    }

    @Test
    fun `the open list loads a saved level`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)
        assertEquals(
            EditorEffect.ListLevels,
            session.activateMenu(session.menuIndex("OPEN")),
        )
        assertEquals(EditorMode.OPEN, session.mode)

        val level = EditorMap.template().toLevel("LOADED", "F*20")
        session.setOpenList(listOf(UserLevel("user_001.txt", "LOADED", level, "")))
        session.handle(EditorCommand.FIRE)

        assertEquals(EditorMode.FIELD, session.mode)
        assertEquals("LOADED", session.name)
        assertEquals("user_001.txt", session.fileName)
        assertEquals(level.map, session.map.rows())
        assertEquals(20, session.enemies.count(EnemyType.FAST))
        assertFalse(session.dirty)
    }

    @Test
    fun `delete asks first and clears the file`() {
        val session = EditorSession()
        session.onSaved("user_001.txt", "ONE")
        session.handle(EditorCommand.BACK)

        session.activateMenu(session.menuIndex("DELETE"))
        assertEquals(EditorMode.CONFIRM, session.mode)
        assertEquals(EditorConfirm.DELETE, session.confirm)

        session.handle(EditorCommand.LEFT)
        val effect = session.handle(EditorCommand.FIRE)

        assertEquals(EditorEffect.Delete("user_001.txt"), effect)
        session.onDeleted()

        assertNull(session.fileName)
        assertTrue(session.dirty)
        assertEquals("DELETED", session.infoTitle)
        assertEquals(EditorMode.MESSAGES, session.mode)
    }

    @Test
    fun `delete without a file explains why`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)

        session.activateMenu(session.menuIndex("DELETE"))

        assertEquals(EditorMode.MESSAGES, session.mode)
        assertEquals(listOf("NO SAVED LEVEL"), session.messages)
    }

    @Test
    fun `exit waits for confirmation when dirty`() {
        val session = EditorSession()
        session.handle(EditorCommand.FIRE)
        session.handle(EditorCommand.BACK)

        assertNull(session.activateMenu(session.menuIndex("EXIT")))
        assertEquals(EditorMode.CONFIRM, session.mode)

        session.handle(EditorCommand.LEFT)
        assertEquals(EditorEffect.Exit, session.handle(EditorCommand.FIRE))
    }

    @Test
    fun `a clean editor exits at once`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)

        assertEquals(EditorEffect.Exit, session.activateMenu(session.menuIndex("EXIT")))
    }

    @Test
    fun `saved and failed results close the loop`() {
        val session = EditorSession()

        session.onSaved("user_002.txt", "TWO")
        assertEquals("SAVED", session.infoTitle)
        assertEquals(listOf("TWO"), session.messages)
        assertEquals("user_002.txt", session.fileName)
        assertEquals("TWO", session.name)
        assertFalse(session.dirty)

        session.dismissMessages()
        assertEquals(EditorMode.MENU, session.mode)

        session.onSaveFailed("disk full")
        assertEquals(EditorMode.MESSAGES, session.mode)
        assertEquals(listOf("DISK FULL"), session.messages)
    }

    @Test
    fun `a partial roster can be completed before saving`() {
        val level = EditorMap.template().toLevel("PART", "F*18")
        val session = EditorSession(
            map = EditorMap.fromLevel(level),
            enemies = EnemySetup.fromSpec(level.enemies),
        )
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("SAVE"))
        session.selectNameCell(38)

        assertEquals(EditorMode.MESSAGES, session.mode)
        assertTrue("ENEMY ROSTER MUST TOTAL 20" in session.messages)

        session.dismissMessages()
        session.activateMenu(session.menuIndex("ENEMIES"))
        val basic = EnemyType.BASIC.ordinal
        session.adjustEnemy(basic, 2)
        assertEquals(20, session.enemies.total)
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("SAVE"))
        val effect = session.selectNameCell(38)

        val save = assertIs<EditorEffect.Save>(effect)
        assertEquals("B*2 F*18", save.level.enemies)
    }

    private fun EditorSession.menuIndex(label: String): Int =
        menuLabels.indexOf(label).also { require(it >= 0) { "no menu item $label" } }

    private fun openLevel(level: Level): UserLevel =
        UserLevel("user_001.txt", level.name, level, "")

    @Test
    fun `open while dirty asks before discarding`() {
        val session = EditorSession()
        session.handle(EditorCommand.FIRE)
        session.handle(EditorCommand.BACK)

        assertNull(session.activateMenu(session.menuIndex("OPEN")))
        assertEquals(EditorMode.CONFIRM, session.mode)
        assertEquals(EditorConfirm.OPEN, session.confirm)

        session.handle(EditorCommand.FIRE) // NO
        assertEquals(EditorMode.MENU, session.mode)
        assertTrue(session.dirty)

        session.activateMenu(session.menuIndex("OPEN"))
        session.handle(EditorCommand.LEFT) // YES
        assertEquals(EditorEffect.ListLevels, session.handle(EditorCommand.FIRE))
    }

    @Test
    fun `a clean open goes straight to the list`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)

        assertEquals(
            EditorEffect.ListLevels,
            session.activateMenu(session.menuIndex("OPEN")),
        )
    }

    @Test
    fun `a failed delete keeps the file attached`() {
        val session = EditorSession()
        session.onSaved("user_009.txt", "NINE")

        session.onDeleteFailed("FILE NOT REMOVED")

        assertEquals("user_009.txt", session.fileName)
        assertEquals("CANNOT DELETE", session.infoTitle)
        assertEquals(EditorMode.MESSAGES, session.mode)
    }

    @Test
    fun `leaving a menu returns to the field`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("ENEMIES"))
        assertEquals(EditorMode.ENEMIES, session.mode)

        session.toField()

        assertEquals(EditorMode.FIELD, session.mode)
    }

    @Test
    fun `save errors are font safe`() {
        val session = EditorSession()

        session.onSaveFailed("open failed: /data/user (0)")

        assertEquals("CANNOT SAVE", session.infoTitle)
        assertTrue(session.messages.all { message -> message.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 :./-!?+" } })
        assertTrue(session.messages.first().contains("OPEN FAILED"))
    }

    @Test
    fun `opening a level keeps its enemy counts`() {
        val level = EditorMap.template().toLevel("MIX", "B*5 F*5 P*5 A*5")
        val session = EditorSession()
        session.setOpenList(listOf(openLevel(level)))
        session.openLevelAt(0)

        assertEquals(5, session.enemies.count(EnemyType.ARMORED))
        assertEquals("B*5 F*5 P*5 A*5", session.enemies.spec())
    }

    @Test
    fun `a blank name is refused`() {
        val session = EditorSession()
        session.handle(EditorCommand.BACK)
        session.activateMenu(session.menuIndex("SAVE"))
        session.nameEntry.setText("   ")

        assertNull(session.selectNameCell(38))

        assertEquals(EditorMode.MESSAGES, session.mode)
        assertEquals("NAME NEEDED", session.infoTitle)
    }
}
