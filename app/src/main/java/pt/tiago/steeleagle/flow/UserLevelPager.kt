package pt.tiago.steeleagle.flow

/**
 * Paging for the My Levels list. Draw and tap share these predicates so the
 * arrows can never be visible when they do nothing (or hidden when they work).
 */
class UserLevelPager(
    val size: Int,
    val pageSize: Int = 5,
) {
    init {
        require(pageSize > 0) { "page size must be positive" }
    }

    val lastIndex: Int
        get() = (size - 1).coerceAtLeast(0)

    val pageCount: Int
        get() = if (size == 0) 1 else (size + pageSize - 1) / pageSize

    fun pageOf(index: Int): Int = index.coerceIn(0, lastIndex) / pageSize

    fun pageStart(index: Int): Int = pageOf(index) * pageSize

    fun hasPreviousPage(index: Int): Boolean = pageOf(index) > 0

    fun hasNextPage(index: Int): Boolean = pageOf(index) < pageCount - 1

    /** Stays on the same row when there is no previous page. */
    fun previousPage(index: Int): Int =
        if (hasPreviousPage(index)) (pageOf(index) - 1) * pageSize else clamp(index)

    /** Stays on the same row when there is no next page. */
    fun nextPage(index: Int): Int =
        if (hasNextPage(index)) (pageOf(index) + 1) * pageSize else clamp(index)

    fun clamp(index: Int): Int = index.coerceIn(0, lastIndex)
}
