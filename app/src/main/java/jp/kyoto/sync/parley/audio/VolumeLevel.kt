package jp.kyoto.sync.parley.audio

/**
 * Ein Lautstärkeregler von 0 bis [MAX] (3.0 = 300 %): Bis 1.0 stellt er die Medienlautstärke
 * des Handys, darüber steht das Handy auf voll und die App verstärkt zusätzlich. So gibt es
 * nur einen Regler, egal ob Brille, Handy oder App zu leise ist.
 */
object VolumeLevel {
    const val MAX = 3.0f

    data class Split(val mediaSteps: Int, val gain: Float)

    fun split(level: Float, maxSteps: Int): Split {
        val l = level.coerceIn(0f, MAX)
        return if (l <= 1f) {
            Split(mediaSteps = Math.round(l * maxSteps), gain = 1.0f)
        } else {
            Split(mediaSteps = maxSteps, gain = l)
        }
    }

    fun from(mediaSteps: Int, maxSteps: Int, gain: Float): Float =
        if (gain > 1f && mediaSteps >= maxSteps) gain.coerceAtMost(MAX)
        else if (maxSteps <= 0) 0f
        else mediaSteps.toFloat() / maxSteps
}
