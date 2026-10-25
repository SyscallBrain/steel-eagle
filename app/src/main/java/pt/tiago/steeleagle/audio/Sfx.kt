package pt.tiago.steeleagle.audio

enum class SfxVoice {
    SQUARE1,
    SQUARE2,
    TRIANGLE,
    NOISE,
}

enum class Waveform {
    SQUARE_12,
    SQUARE_25,
    SQUARE_50,
    TRIANGLE,
    NOISE,
}

data class SfxEffect(
    val voice: SfxVoice,
    val waveform: Waveform,
    val startFrequency: Float,
    val endFrequency: Float,
    val durationMs: Int,
    val volume: Float,
)

/** Original effects, defined as envelopes and frequency sweeps. */
object Sfx {
    val SHOT = SfxEffect(
        SfxVoice.SQUARE1, Waveform.SQUARE_50, 880f, 440f, 60, 0.35f,
    )
    val STEEL_HIT = SfxEffect(
        SfxVoice.SQUARE2, Waveform.SQUARE_12, 1_400f, 700f, 45, 0.30f,
    )
    val BRICK_BREAK = SfxEffect(
        SfxVoice.NOISE, Waveform.NOISE, 1_200f, 300f, 90, 0.35f,
    )
    val ENEMY_EXPLODE = SfxEffect(
        SfxVoice.NOISE, Waveform.NOISE, 800f, 120f, 220, 0.40f,
    )
    val PLAYER_EXPLODE = SfxEffect(
        SfxVoice.NOISE, Waveform.NOISE, 600f, 60f, 500, 0.50f,
    )
    val POWER_UP_APPEAR = SfxEffect(
        SfxVoice.SQUARE2, Waveform.SQUARE_25, 400f, 900f, 300, 0.30f,
    )
    val POWER_UP_PICK = SfxEffect(
        SfxVoice.SQUARE1, Waveform.SQUARE_50, 523f, 1_568f, 160, 0.40f,
    )
    val EXTRA_LIFE = SfxEffect(
        SfxVoice.SQUARE2, Waveform.SQUARE_25, 523f, 2_093f, 320, 0.40f,
    )
    val PAUSE = SfxEffect(
        SfxVoice.SQUARE1, Waveform.SQUARE_50, 660f, 330f, 120, 0.30f,
    )
}
