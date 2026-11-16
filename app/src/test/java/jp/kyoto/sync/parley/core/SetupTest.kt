package jp.kyoto.sync.parley.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Erkennung, Übersetzung und Stimme sind einzeln wählbar (alle Varianten einstellbar). */
class SetupTest {

    @Test
    fun allInOneModelsNeedNeitherTranslatorNorVoice() {
        for (r in listOf(Recognition.GEMINI_LIVE, Recognition.OPENAI, Recognition.OPENAI_MINI)) {
            val s = Setup(r, Translation.CLAUDE_SERVER, Voice.AZURE)
            assertTrue(s.allInOne)
            assertFalse(s.usesTranslator)
            assertFalse(s.usesVoice)
        }
    }

    @Test
    fun geminiVoiceTranslatesItselfSoNoExtraTranslator() {
        val s = Setup(Recognition.SUISSE, Translation.CLAUDE_SERVER, Voice.GEMINI)
        assertFalse(s.usesTranslator)
        assertTrue(s.usesVoice)
    }

    /** Claude übersetzt, Gemini liest nur vor. */
    @Test
    fun geminiReadingAloudKeepsTheTextTranslator() {
        val s = Setup(Recognition.VOXTRAL, Translation.CLAUDE_SERVER, Voice.GEMINI_VORLESEN)
        assertTrue(s.usesTranslator)
        assertTrue(s.usesVoice)
        assertEquals(s, Setup.decode(s.encode()))
    }

    /**
     * Die Sprache des Gegenübers schlägt die Kette vor. Deutsch und Schweizerdeutsch sind ein Eintrag mit Claudes
     * Deutung; nur dort hält das Auslassen fremder Sprachen sicher (Gorans Entscheid 1035a, messungen/nur-deutsch.md).
     * Andere Sprachen übersetzt Gemini Live allein, weil Voxtral fest Deutsch erkennt.
     */
    @Test
    fun germanGetsTheChainWithClaude() {
        assertEquals(Setup(Recognition.VOXTRAL, Translation.CLAUDE_SERVER, Voice.GEMINI_VORLESEN), Setup.vorschlag(Lang.DE_CH))
    }

    @Test
    fun germanIsOneEntryForStandardAndSwissGerman() {
        assertEquals(listOf(Lang.DE_CH), Lang.entries.filter { it.englishName == "German" })
        assertEquals("Deutsch / Schweizerdeutsch", Lang.DE_CH.displayName)
    }

    @Test
    fun otherLanguagesGetGeminiLiveAlone() {
        assertEquals(Recognition.GEMINI_LIVE, Setup.vorschlag(Lang.EN).recognition)
    }

    @Test
    fun otherVoicesNeedATextTranslator() {
        val s = Setup(Recognition.VOXTRAL, Translation.CLAUDE_SERVER, Voice.ANDROID)
        assertTrue(s.usesTranslator)
        assertTrue(s.usesVoice)
    }

    @Test
    fun theCurrentFavouriteIsTheDefault() {
        assertEquals(Setup.vorschlag(Lang.DE_CH), Setup.DEFAULT)
    }

    @Test
    fun storedChoicesSurviveAndUnknownOnesFallBack() {
        val s = Setup(Recognition.VOXTRAL, Translation.GEMINI_FLASH, Voice.AZURE)
        assertEquals(s, Setup.decode(s.encode()))
        assertEquals(Setup.DEFAULT, Setup.decode("kaputt"))
        assertEquals(Setup.DEFAULT, Setup.decode(null))
    }
}
