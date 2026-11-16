package jp.kyoto.sync.parley.net

import jp.kyoto.sync.parley.core.Lang
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class InterpreterProtocolTest {

    private fun session(target: Lang = Lang.SR) =
        JSONObject(InterpreterProtocol.sessionUpdate(target, Lang.DE_CH, "gpt-realtime-whisper")).getJSONObject("session")

    @Test
    fun instructionsTellModelToOnlyTranslateIntoSerbian() {
        val instructions = session(Lang.SR).getString("instructions")
        assertTrue(instructions.contains("Serbian"))
        assertTrue(instructions.contains("Swiss German"))
        assertTrue(instructions.contains("Only output the translation"))
    }

    /**
     * Übersetzt wird nur die Sprache, aus der die Richtung kommt: beim Zuhören die des Gegenübers, bei der eigenen
     * Taste die der Benutzerin. In 0.5.3 hiess es fest «nur Deutsch», und Gemini verschwieg Vesnas Serbisch bei der Taste.
     */
    @Test
    fun instructionsTranslateOnlyTheSourceLanguageOfTheDirection() {
        assertTrue(InterpreterProtocol.instructions(target = Lang.SR, source = Lang.DE_CH)
            .contains("Translate German and Swiss German dialect (Schweizerdeutsch) speech into Serbian"))
        assertTrue(InterpreterProtocol.instructions(target = Lang.DE, source = Lang.SR)
            .contains("Translate Serbian speech into German"))
        assertTrue(InterpreterProtocol.instructions(target = Lang.SR, source = Lang.EN)
            .contains("Translate English speech into Serbian"))
    }

    @Test
    fun instructionsFollowTheChosenTargetLanguage() {
        assertTrue(session(Lang.DE).getString("instructions").contains("into German"))
    }

    @Test
    fun answersInAudioAt24kHzPcm() {
        val s = session()
        assertEquals("realtime", s.getString("type"))
        assertEquals("audio", s.getJSONArray("output_modalities").getString(0))
        val audio = s.getJSONObject("audio")
        for (dir in listOf("input", "output")) {
            val fmt = audio.getJSONObject(dir).getJSONObject("format")
            assertEquals("audio/pcm", fmt.getString("type"))
            assertEquals(24_000, fmt.getInt("rate"))
        }
    }

    @Test
    fun speakerContinuingDoesNotCutOffTheRunningTranslation() {
        val td = session().getJSONObject("audio").getJSONObject("input").getJSONObject("turn_detection")
        assertTrue(td.getBoolean("create_response"))
        assertFalse(td.getBoolean("interrupt_response"))
    }

    @Test
    fun originalSpeechIsTranscribedForSubtitles() {
        val tr = session().getJSONObject("audio").getJSONObject("input").getJSONObject("transcription")
        assertEquals("gpt-realtime-whisper", tr.getString("model"))
    }

    @Test
    fun appendSendsBase64Audio() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val msg = JSONObject(InterpreterProtocol.appendAudio(pcm))
        assertEquals("input_audio_buffer.append", msg.getString("type"))
        assertArrayEquals(pcm, Base64.getDecoder().decode(msg.getString("audio")))
    }

    @Test
    fun parsesTranslatedAudioAndSubtitles() {
        val pcm = byteArrayOf(9, 8, 7, 6)
        val b64 = Base64.getEncoder().encodeToString(pcm)
        val audio = InterpreterProtocol.parse("""{"type":"response.output_audio.delta","delta":"$b64"}""")
        assertArrayEquals(pcm, (audio as InterpreterEvent.Audio).pcm16)

        assertEquals(
            InterpreterEvent.OutputTranscript("Dobar dan"),
            InterpreterProtocol.parse("""{"type":"response.output_audio_transcript.delta","delta":"Dobar dan"}"""),
        )
        assertEquals(
            InterpreterEvent.InputTranscript("Grüezi"),
            InterpreterProtocol.parse(
                """{"type":"conversation.item.input_audio_transcription.delta","delta":"Grüezi"}""",
            ),
        )
    }

    @Test
    fun newSubtitleLineAfterEachFinishedTranslation() {
        assertEquals(
            InterpreterEvent.OutputTranscript("\n"),
            InterpreterProtocol.parse("""{"type":"response.output_audio_transcript.done","transcript":"x"}"""),
        )
        assertEquals(
            InterpreterEvent.InputTranscript("\n"),
            InterpreterProtocol.parse(
                """{"type":"conversation.item.input_audio_transcription.completed","transcript":"x"}""",
            ),
        )
    }

    @Test
    fun errorsCarryTheServerMessage() {
        val e = InterpreterProtocol.parse("""{"type":"error","error":{"message":"invalid_api_key"}}""")
        assertTrue((e as InterpreterEvent.Error).message.contains("invalid_api_key"))
    }

    @Test
    fun ignoresUnknownAndBrokenMessages() {
        assertNull(InterpreterProtocol.parse("""{"type":"rate_limits.updated"}"""))
        assertNull(InterpreterProtocol.parse("not json"))
    }
}
