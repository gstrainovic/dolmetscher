package jp.kyoto.sync.parley

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Jede Oberflächensprache muss jeden Text haben, damit nichts unübersetzt durchrutscht. */
class StringsTest {

    private val res = File("src/main/res")

    private fun keys(dir: String): Set<String> =
        Regex("""<string name="([^"]+)"""")
            .findAll(File(res, "$dir/strings.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun germanIsTheDefaultAndSerbianExists() {
        val default = File(res, "values/strings.xml").readText()
        assertTrue(default.contains("<string name=\"app_name\">Dolmetscher</string>"))
        assertTrue(File(res, "values-sr/strings.xml").exists())
    }

    @Test
    fun everyLanguageHasEveryText() {
        val expected = keys("values")
        for (dir in listOf("values-sr", "values-en", "values-ja")) {
            assertEquals("Fehlende oder überzählige Texte in $dir", expected, keys(dir))
        }
    }
}
