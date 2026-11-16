package jp.kyoto.sync.parley.net

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SuisseProtocolTest {

    @Test
    fun recognisesSwissGermanAndCommitsAtPauses() {
        val c = JSONObject(SuisseProtocol.config())
        assertEquals("config", c.getString("type"))
        assertEquals("de-CH", c.getString("lang"))
        assertTrue(c.getBoolean("finalize_on_pause"))
    }

    @Test
    fun swissEverydayWordsAreInTheVocabulary() {
        // Ohne Wortschatz wird «Chüngel» zu «Küngel» oder «Hüngel» (Test mit Suisse-TTS, Zürich).
        val vocab = JSONObject(SuisseProtocol.config()).getJSONArray("vocabulary")
        val words = (0 until vocab.length()).map { vocab.getString(it) }
        assertTrue(words.contains("Chüngel"))
        assertTrue(words.size <= 200)
        assertTrue(words.all { it.length <= 100 })
    }

    @Test
    fun audioGoesOutAt16kHz() {
        val pcm24 = ByteArray(4800) // 100 ms bei 24 kHz
        val msg = JSONObject(SuisseProtocol.appendAudio(pcm24))
        assertEquals("audio.append", msg.getString("type"))
        assertEquals(3200, Base64.getDecoder().decode(msg.getString("data")).size) // 100 ms bei 16 kHz
    }

    @Test
    fun endOfSpeechCommitsTheSentenceAtOnce() {
        assertEquals("finalize", JSONObject(SuisseProtocol.finalize()).getString("type"))
    }

    @Test
    fun finishedSentencesAreTranslatedPartialsAreNot() {
        assertEquals(
            SuisseEvent.Final("Haben Sie Ihre Tabletten genommen?"),
            SuisseProtocol.parse("""{"type":"transcript.final","text":"Haben Sie Ihre Tabletten genommen?","lang":"de-CH"}"""),
        )
        assertNull(SuisseProtocol.parse("""{"type":"transcript.partial","text":"Haben Sie"}"""))
        assertNull(SuisseProtocol.parse("""{"type":"transcript.final","text":"  "}"""))
    }

    @Test
    fun errorsCarryTheServerMessageInBothShapes() {
        val a = SuisseProtocol.parse("""{"type":"error","error":{"code":"auth.invalid_key","message":"Invalid key."}}""")
        val b = SuisseProtocol.parse("""{"type":"error","code":"stt.invalid_request","message":"Unknown lang."}""")
        assertTrue((a as SuisseEvent.Error).message.contains("Invalid key."))
        assertTrue((b as SuisseEvent.Error).message.contains("Unknown lang."))
    }
}
