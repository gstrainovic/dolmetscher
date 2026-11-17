package jp.kyoto.sync.parley.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTest {

    @Test
    fun defaultTargetsAreCorrect() {
        val c = Conversation.JA_EN
        assertEquals(Lang.JA, c.myLang)
        assertEquals(Lang.EN, c.partnerLang)
        // Nur eine Richtung: das Gegenüber wird in die Sprache der Benutzerin übersetzt.
        assertEquals(Lang.JA, c.incomingTarget)
    }

    @Test
    fun vesnaHearsSerbianWhenSomeoneSpeaksGerman() {
        val c = Conversation.DEFAULT
        assertEquals(Lang.SR, c.incomingTarget)
        assertEquals(Lang.DE_CH, c.partnerLang)
    }

    @Test
    fun serbianIsOfferedInThePicker() {
        assertEquals(Lang.SR, Lang.fromCode("sr"))
        assertEquals("Srpski", Lang.SR.displayName)
    }

    @Test
    fun langCodes() {
        assertEquals("ja", Lang.JA.code)
        assertEquals("en", Lang.EN.code)
    }
}
