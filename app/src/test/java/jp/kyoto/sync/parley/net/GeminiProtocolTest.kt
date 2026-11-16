package jp.kyoto.sync.parley.net

import jp.kyoto.sync.parley.core.Lang
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GeminiProtocolTest {

    private val setup = JSONObject(GeminiProtocol.setup(Lang.SR)).getJSONObject("setup")

    @Test
    fun usesTheStableLiveModelWithSpokenAnswers() {
        assertEquals("models/gemini-3.8-live", setup.getString("model"))
        assertEquals("AUDIO", setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
    }

    @Test
    fun sameInterpreterInstructionsAsOpenAi() {
        val text = setup.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals(InterpreterProtocol.instructions(Lang.SR), text)
    }

    /** Vorlesen statt übersetzen; mit dieser Anweisung las Gemini 8 von 8 Sätzen wortgetreu (messungen/vorlesen.py). */
    @Test
    fun readingAloudUsesItsOwnInstruction() {
        val s = JSONObject(GeminiProtocol.setup(Lang.SR, InterpreterProtocol.readAloudInstructions(Lang.SR)))
            .getJSONObject("setup")
        val text = s.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(text.contains("word for word"))
        assertTrue(text.contains("Serbian"))
    }

    @Test
    fun speakerContinuingDoesNotCutOffTheTranslation() {
        assertEquals("NO_INTERRUPTION", setup.getJSONObject("realtimeInputConfig").getString("activityHandling"))
    }

    @Test
    fun subtitlesForBothSidesAndLongSessions() {
        assertTrue(setup.has("inputAudioTranscription"))
        assertTrue(setup.has("outputAudioTranscription"))
        assertTrue(setup.getJSONObject("contextWindowCompression").has("slidingWindow"))
    }

    @Test
    fun audioIsSentAs24kHzPcm() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val audio = JSONObject(GeminiProtocol.appendAudio(pcm)).getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=24000", audio.getString("mimeType"))
        assertArrayEquals(pcm, Base64.getDecoder().decode(audio.getString("data")))
    }

    @Test
    fun recognisedTextCanBeSentInsteadOfAudio() {
        val msg = JSONObject(GeminiProtocol.text("Haben Sie Hunger?")).getJSONObject("realtimeInput")
        assertEquals("Haben Sie Hunger?", msg.getString("text"))
    }

    @Test
    fun endOfSpeechTellsGeminiTheAudioPaused() {
        assertTrue(JSONObject(GeminiProtocol.audioStreamEnd()).getJSONObject("realtimeInput").getBoolean("audioStreamEnd"))
    }

    @Test
    fun oneServerMessageCanCarryAudioAndSubtitles() {
        val pcm = byteArrayOf(5, 6, 7, 8)
        val b64 = Base64.getEncoder().encodeToString(pcm)
        val events = GeminiProtocol.parse(
            """{"serverContent":{
                 "modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64"}}]},
                 "inputTranscription":{"text":"Grüezi"},
                 "outputTranscription":{"text":"Zdravo"}}}""",
        )
        assertArrayEquals(pcm, (events[0] as InterpreterEvent.Audio).pcm16)
        assertTrue(events.contains(InterpreterEvent.InputTranscript("Grüezi")))
        assertTrue(events.contains(InterpreterEvent.OutputTranscript("Zdravo")))
    }

    @Test
    fun newSubtitleLineWhenATranslationIsFinished() {
        assertEquals(
            listOf(InterpreterEvent.OutputTranscript("\n"), InterpreterEvent.InputTranscript("\n")),
            GeminiProtocol.parse("""{"serverContent":{"turnComplete":true}}"""),
        )
    }

    @Test
    fun recognisesSetupConfirmationAndAnnouncedDisconnect() {
        assertTrue(GeminiProtocol.isSetupComplete("""{"setupComplete":{}}"""))
        assertTrue(GeminiProtocol.isGoAway("""{"goAway":{"timeLeft":"10s"}}"""))
        assertTrue(!GeminiProtocol.isGoAway("""{"serverContent":{}}"""))
    }

    @Test
    fun internalMarkersDoNotShowUpInTheSubtitles() {
        // Gesehen auf dem Testhandy: «<no speech>{pause}» im serbischen Untertitel.
        assertEquals(
            emptyList<InterpreterEvent>(),
            GeminiProtocol.parse("""{"serverContent":{"outputTranscription":{"text":"<no speech>{pause}"}}}"""),
        )
        assertEquals(
            listOf(InterpreterEvent.OutputTranscript("Želite li čaj?")),
            GeminiProtocol.parse("""{"serverContent":{"outputTranscription":{"text":"Želite li čaj?{pause}"}}}"""),
        )
    }

    @Test
    fun ignoresSetupConfirmationAndBrokenMessages() {
        assertEquals(emptyList<InterpreterEvent>(), GeminiProtocol.parse("""{"setupComplete":{}}"""))
        assertEquals(emptyList<InterpreterEvent>(), GeminiProtocol.parse("kaputt"))
    }
}
