package pt.tiago.steeleagle.flow

/** Vertical menu cursor with wrap-around. */
class Menu(
    val labels: List<String>,
    initial: Int = 0,
) {
    var selected: Int = 0
        private set

    init {
        require(labels.isNotEmpty()) { "a menu needs at least one item" }
        selected = initial.coerceIn(0, labels.lastIndex)
    }

    fun move(delta: Int) {
        selected = Math.floorMod(selected + delta, labels.size)
    }

    fun select(index: Int) {
        require(index in labels.indices) { "menu index out of range" }
        selected = index
    }

    val current: String
        get() = labels[selected]
}
