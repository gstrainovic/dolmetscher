package jp.kyoto.sync.parley.net

import jp.kyoto.sync.parley.core.Lang
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextServicesProtocolTest {

    // --- Voxtral (Mistral, Umschrift je Satz) ---

    @Test
    fun voxtralGetsAValidWavOfTheSentence() {
        val pcm = ByteArray(3200) // 100 ms bei 16 kHz
        val wav = VoxtralProtocol.wav16k(pcm)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(44 + 3200, wav.size)
        // Abtastrate 16000 an Stelle 24, little-endian
        assertEquals(16000, (wav[24].toInt() and 0xFF) or ((wav[25].toInt() and 0xFF) shl 8) or ((wav[26].toInt() and 0xFF) shl 16))
    }

    @Test
    fun voxtralTextIsTakenFromTheAnswer() {
        assertEquals("Mir ist kalt.", VoxtralProtocol.parse("""{"model":"voxtral-mini-2602","text":" Mir ist kalt. "}"""))
        assertEquals(null, VoxtralProtocol.parse("""{"text":"  "}"""))
    }

    // --- Gemini Flash als Textübersetzer ---

    @Test
    fun geminiFlashSeesTheRecentConversationForContext() {
        val body = JSONObject(GeminiFlashProtocol.request(Lang.SR, listOf("Wollen Sie in den Garten?"), "Könnte ich noch einen Job haben?"))
        val text = body.toString()
        assertTrue(text.contains("Wollen Sie in den Garten?"))
        assertTrue(text.contains("Könnte ich noch einen Job haben?"))
        assertTrue(text.contains("Serbian"))
    }

    @Test
    fun geminiFlashAnswerIsTheTranslation() {
        val answer = """{"candidates":[{"content":{"parts":[{"text":"Hladno mi je.\n"}]}}]}"""
        assertEquals("Hladno mi je.", GeminiFlashProtocol.parse(answer))
    }

    // --- Claude über den eigenen Server ---

    @Test
    fun claudeServerRequestCarriesSessionTargetAndText() {
        val r = JSONObject(ClaudeServerProtocol.request("sitzung-1", Lang.SR, "Haben Sie Hunger?"))
        assertEquals("sitzung-1", r.getString("sitzung"))
        assertEquals("sr", r.getString("ziel"))
        assertEquals("Haben Sie Hunger?", r.getString("text"))
        assertEquals("Da li ste gladni?", ClaudeServerProtocol.parse("""{"uebersetzung":"Da li ste gladni?"}"""))
    }

    // --- Azure-Stimme ---

    @Test
    fun azureSpeaksSerbianInLatinScriptWithEscapedText() {
        val ssml = AzureProtocol.ssml(Lang.SR, "Ja & ti <3")
        assertTrue(ssml.contains("sr-Latn-RS-SophieNeural"))
        assertTrue(ssml.contains("Ja &amp; ti &lt;3"))
    }

    @Test
    fun azureHasAVoiceForEveryTargetTheAppOffersOrFallsBackToGerman() {
        assertTrue(AzureProtocol.voice(Lang.DE).startsWith("de-"))
        assertTrue(AzureProtocol.voice(Lang.HR).startsWith("hr-"))
    }
}
