package pt.tiago.steeleagle.scene

import android.graphics.Canvas
import pt.tiago.steeleagle.core.model.PlayerInput

/** A screen or gameplay state drawn into the 256x224 logical bitmap. */
interface Scene {
    fun update(input: PlayerInput, second: PlayerInput)
    fun draw(canvas: Canvas)
}

class SceneManager(initialScene: Scene) : Scene {
    var current: Scene = initialScene
        private set

    fun switchTo(scene: Scene) {
        current = scene
    }

    override fun update(input: PlayerInput, second: PlayerInput) {
        current.update(input, second)
    }

    override fun draw(canvas: Canvas) {
        current.draw(canvas)
    }
}
