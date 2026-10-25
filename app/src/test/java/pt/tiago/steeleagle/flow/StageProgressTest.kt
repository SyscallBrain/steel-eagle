package pt.tiago.steeleagle.flow

import org.junit.Assert.assertEquals
import org.junit.Test

class StageProgressTest {

    @Test
    fun `advances through the campaign`() {
        assertEquals(2, StageProgress.nextStage(current = 1, levelCount = 3))
        assertEquals(3, StageProgress.nextStage(current = 2, levelCount = 3))
    }

    @Test
    fun `wraps to stage one after the last stage`() {
        assertEquals(1, StageProgress.nextStage(current = 3, levelCount = 3))
        assertEquals(1, StageProgress.nextStage(current = 1, levelCount = 1))
    }
}
