package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ein einziger Regler: bis 100 % Medienlautstärke des Handys, darüber Verstärkung in der App. */
class VolumeLevelTest {

    @Test
    fun lowerHalfMovesOnlyThePhoneMediaVolume() {
        assertEquals(VolumeLevel.Split(mediaSteps = 8, gain = 1.0f), VolumeLevel.split(0.5f, maxSteps = 15))
        assertEquals(VolumeLevel.Split(mediaSteps = 0, gain = 1.0f), VolumeLevel.split(0f, maxSteps = 15))
    }

    @Test
    fun aboveFullVolumeTheAppAmplifies() {
        assertEquals(VolumeLevel.Split(mediaSteps = 15, gain = 2.5f), VolumeLevel.split(2.5f, maxSteps = 15))
    }

    @Test
    fun sliderShowsTheCurrentVolumeWhenOpened() {
        assertEquals(0.6f, VolumeLevel.from(mediaSteps = 9, maxSteps = 15, gain = 1.0f), 0.001f)
        assertEquals(2.0f, VolumeLevel.from(mediaSteps = 15, maxSteps = 15, gain = 2.0f), 0.001f)
    }

    @Test
    fun valuesOutsideTheRangeAreClamped() {
        assertEquals(VolumeLevel.Split(mediaSteps = 15, gain = 3.0f), VolumeLevel.split(9f, maxSteps = 15))
    }
}
