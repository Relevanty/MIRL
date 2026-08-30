package com.personal.sleepalarm.data.english

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishDictionaryIntegrityTest {
    private fun assetFile(): File {
        return sequenceOf(
            File("src/main/assets/english/english_words_10000.tsv"),
            File("app/src/main/assets/english/english_words_10000.tsv")
        ).first { it.isFile }
    }

    private fun words() = assetFile().inputStream().use(EnglishDictionaryAssetSource::parse)

    @Test
    fun `asset has exactly ten thousand unique translated words`() {
        val words = words()

        assertEquals(10_000, words.size)
        assertEquals(10_000, words.map { it.word }.toSet().size)
        assertTrue(words.all { it.translation.isNotBlank() })
        assertTrue(words.all { it.level in setOf("BASE", "COMMON", "CONFIDENT", "ADVANCED", "RARE") })
        assertFalse(words.any { it.word.contains(' ') })
    }

    @Test
    fun `reviewed core metadata is modern and internally consistent`() {
        val byWord = words().associateBy { it.word }
        val expected = mapOf(
            "the" to listOf("определённый артикль", "/ðə; ðiː/", "article"),
            "be" to listOf("быть; находиться", "/biː/", "verb"),
            "is" to listOf("есть; является", "/ɪz/", "verb"),
            "are" to listOf("есть; являются", "/ɑːr/", "verb"),
            "was" to listOf("был; была; было", "/wɒz/", "verb"),
            "were" to listOf("были; был", "/wɜːr/", "verb"),
            "on" to listOf("на; включённый", "/ɒn/", "preposition"),
            "or" to listOf("или; либо", "/ɔːr/", "conjunction"),
            "one" to listOf("один; единица", "/wʌn/", "numeral"),
            "may" to listOf("мочь; возможно", "/meɪ/", "modal verb"),
            "can" to listOf("мочь; уметь", "/kæn/", "modal verb"),
            "will" to listOf("будет; намереваться", "/wɪl/", "modal verb"),
            "high" to listOf("высокий; высоко", "/haɪ/", "adjective")
        )

        expected.forEach { (word, fields) ->
            val entry = requireNotNull(byWord[word]) { "Missing reviewed core word: $word" }
            assertEquals("translation for $word", fields[0], entry.translation)
            assertEquals("pronunciation for $word", fields[1], entry.pronunciation)
            assertEquals("part of speech for $word", fields[2], entry.partOfSpeech)
            assertTrue("modern hint for $word", entry.hint.isNotBlank())
        }
    }

    @Test
    fun `asset does not expose blocked offensive vocabulary`() {
        val blocked = setOf("fuck", "fucking", "shit", "cunt", "nigger", "porn", "rape")
        val words = words()

        assertTrue(words.none { it.word in blocked })
        assertTrue(words.none { it.translation.contains(Regex("(?i)(хуй|пизд|бляд|ёб|жоп|дроч|порн)")) })
    }

    @Test
    fun `asset builds one structured article sense for every word`() {
        val bundle = assetFile().inputStream().use(EnglishDictionaryAssetSource::parseBundle)

        assertEquals(10_000, bundle.words.size)
        assertEquals(10_000, bundle.senses.size)
        assertEquals(bundle.words.map { it.id }, bundle.senses.map { it.wordId })
        assertTrue(bundle.senses.all { it.translations.isNotBlank() })
    }

    @Test
    fun `top five hundred never expose flagged secondary senses`() {
        val discouraged = Regex(
            "(?i)\\b(obsolete|archaic|rare|dated|slang|vulgar|offensive|tincture|heraldry|deprecated)\\b"
        )

        assertTrue(words().take(500).none { it.hint.contains(discouraged) })
        assertTrue(
            words().take(500).none {
                it.partOfSpeech in setOf("suffix", "prefix", "pn", "symbol", "letter")
            }
        )
    }
}
