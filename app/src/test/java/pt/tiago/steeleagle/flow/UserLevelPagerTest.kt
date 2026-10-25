package pt.tiago.steeleagle.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLevelPagerTest {

    @Test
    fun `an empty list still has one page`() {
        val pager = UserLevelPager(0)

        assertEquals(1, pager.pageCount)
        assertEquals(0, pager.lastIndex)
        assertFalse(pager.hasPreviousPage(0))
        assertFalse(pager.hasNextPage(0))
        assertEquals(0, pager.clamp(7))
        assertEquals(0, pager.nextPage(0))
        assertEquals(0, pager.previousPage(0))
    }

    @Test
    fun `exact page sizes hide the next arrow on the last page`() {
        val pager = UserLevelPager(5)

        assertEquals(1, pager.pageCount)
        assertFalse(pager.hasNextPage(0))
        assertFalse(pager.hasNextPage(4))

        val ten = UserLevelPager(10)
        assertEquals(2, ten.pageCount)
        assertTrue(ten.hasNextPage(0))
        assertTrue(ten.hasNextPage(4))
        assertFalse(ten.hasNextPage(5))
        assertFalse(ten.hasNextPage(9))
        assertEquals(5, ten.nextPage(2))
        assertEquals(0, ten.previousPage(7))
    }

    @Test
    fun `edge pages do not move the selection`() {
        for (size in 1..13) {
            val pager = UserLevelPager(size)
            for (index in 0..pager.lastIndex) {
                if (!pager.hasNextPage(index)) {
                    assertEquals("size $size index $index", index, pager.nextPage(index))
                }
                if (!pager.hasPreviousPage(index)) {
                    assertEquals("size $size index $index", index, pager.previousPage(index))
                }
            }
        }
    }

    @Test
    fun `partial last pages only page when a page exists`() {
        for (size in listOf(6, 7, 8, 9, 11, 12)) {
            val pager = UserLevelPager(size)
            for (index in 0..pager.lastIndex) {
                val page = pager.pageOf(index)
                val next = pager.nextPage(index)
                if (pager.hasNextPage(index)) {
                    assertEquals("size $size index $index", page + 1, pager.pageOf(next))
                    assertTrue("size $size index $index", next <= pager.lastIndex)
                } else {
                    assertEquals("size $size index $index", index, next)
                }
                val previous = pager.previousPage(index)
                if (pager.hasPreviousPage(index)) {
                    assertEquals("size $size index $index", page - 1, pager.pageOf(previous))
                } else {
                    assertEquals("size $size index $index", index, previous)
                }
            }

            assertEquals(5, pager.nextPage(0))
            assertEquals(5, pager.nextPage(4))
            assertEquals(0, pager.previousPage(5))
        }
    }

    @Test
    fun `next page lands on a valid row`() {
        val pager = UserLevelPager(12)

        assertEquals(5, pager.nextPage(3))
        assertEquals(10, pager.nextPage(8))
        assertEquals(11, pager.nextPage(11))
        assertEquals(5, pager.previousPage(10))
        assertEquals(3, pager.previousPage(3))
    }
}
