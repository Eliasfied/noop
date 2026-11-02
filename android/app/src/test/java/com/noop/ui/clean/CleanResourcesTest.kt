package com.noop.ui.clean

import com.noop.ui.normJournalKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The clean design's strings live in their own clean_strings.xml (en + de). This pins the contract the
 * screens rely on: every translatable English key has a German twin with the same placeholders, the
 * habit catalogue arrays line up item for item, and no catalogue habit duplicates another or one of the
 * starter questions (which would put the same habit in the logbook twice).
 */
class CleanResourcesTest {

    private fun resFile(rel: String): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val found = listOf(
            File(userDir, "src/main/res/$rel"),
            File(userDir, "app/src/main/res/$rel"),
            File(userDir, "android/app/src/main/res/$rel"),
        ).firstOrNull { it.isFile }
        assertNotNull("$rel not found from user.dir=$userDir; a resource guard must fail closed", found)
        return found!!
    }

    private data class Res(
        val strings: Map<String, String>,
        val translatable: Set<String>,
        val plurals: Map<String, List<String>>,
        val arrays: Map<String, List<String>>,
    )

    private fun parse(file: File): Res {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = LinkedHashMap<String, String>()
        val translatable = HashSet<String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            strings[e.getAttribute("name")] = e.textContent
            if (e.getAttribute("translatable") != "false") translatable.add(e.getAttribute("name"))
        }
        fun children(tag: String, child: String): Map<String, List<String>> {
            val out = LinkedHashMap<String, List<String>>()
            val list = doc.getElementsByTagName(tag)
            for (i in 0 until list.length) {
                val e = list.item(i) as Element
                val items = e.getElementsByTagName(child)
                out[e.getAttribute("name")] = (0 until items.length).map { items.item(it).textContent }
            }
            return out
        }
        return Res(strings, translatable, children("plurals", "item"), children("string-array", "item"))
    }

    private val placeholder = Regex("%(\\d+\\$)?[sdf]")
    private fun placeholders(s: String): Set<String> = placeholder.findAll(s).map { it.value }.toSet()

    @Test
    fun everyTranslatableKeyHasAGermanTwinWithTheSamePlaceholders() {
        val en = parse(resFile("values/clean_strings.xml"))
        val de = parse(resFile("values-de/clean_strings.xml"))
        val missing = en.translatable.filter { it !in de.strings }
        assertTrue("German is missing: $missing", missing.isEmpty())
        val extra = de.strings.keys.filter { it !in en.strings }
        assertTrue("German declares keys English does not: $extra", extra.isEmpty())
        val untranslatableRedeclared = de.strings.keys.filter { it in en.strings && it !in en.translatable }
        assertTrue("German redeclares untranslatable keys: $untranslatableRedeclared", untranslatableRedeclared.isEmpty())
        for ((key, text) in de.strings) {
            assertEquals("placeholders of $key", placeholders(en.strings.getValue(key)), placeholders(text))
        }
        assertEquals(en.plurals.keys, de.plurals.keys)
        assertEquals(en.arrays.keys, de.arrays.keys)
    }

    @Test
    fun habitCataloguesLineUpAndNeverRepeatAHabit() {
        for (locale in listOf("values", "values-de")) {
            val res = parse(resFile("$locale/clean_strings.xml"))
            val en = parse(resFile("values/clean_strings.xml"))
            val habits = res.arrays.filterKeys { it.startsWith("clean_habits_") }
            assertEquals("8 sections in $locale", 8, habits.size)
            habits.forEach { (name, items) ->
                assertEquals("$name length in $locale", en.arrays.getValue(name).size, items.size)
            }
            val all = habits.values.flatten().map(::normJournalKey)
            val dupes = all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue("duplicate habits in $locale: $dupes", dupes.isEmpty())
            val starters = res.strings.filterKeys { it.startsWith("clean_starter_") }.values.map(::normJournalKey).toSet()
            val clash = all.filter { it in starters }
            assertTrue("catalogue repeats a starter question in $locale: $clash", clash.isEmpty())
        }
    }

    @Test
    fun theStarterLabelsCoverEveryStarterQuestion() {
        assertEquals(com.noop.ui.STARTER_JOURNAL_QUESTIONS.toSet(), starterLabels.keys)
    }
}
