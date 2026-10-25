package pt.tiago.steeleagle.flow

/** Campaign progress rules shared by the flow and its tests. */
object StageProgress {

    /** Stage after [current] when the campaign has [levelCount] stages. */
    fun nextStage(current: Int, levelCount: Int): Int {
        require(levelCount > 0) { "there must be at least one stage" }
        require(current in 1..levelCount) { "stage out of range" }
        return if (current >= levelCount) 1 else current + 1
    }
}
