package com.personal.sleepalarm.data.english

import android.content.Context
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordSenseEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class EnglishDictionaryAssetSource(
    private val context: Context,
    private val assetName: String = ASSET_NAME
) {
    fun load(): List<EnglishWordEntity> = loadBundle().words

    fun loadBundle(): EnglishDictionaryBundle = context.assets.open(assetName).use(::parseBundle)

    companion object {
        const val ASSET_NAME = "english/english_words_10000.tsv"
        const val EXPECTED_WORD_COUNT = 10_000
        // Bump whenever the generated TSV changes; repository remaps progress by headword.
        const val DATASET_VERSION = "2026-08-30.1"

        fun parse(input: InputStream): List<EnglishWordEntity> {
            val words = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines
                    .filter { it.isNotBlank() && !it.startsWith('#') }
                    .mapIndexed { index, line -> parseLine(index + 1, line) }
                    .toList()
            }
            require(words.size == EXPECTED_WORD_COUNT) {
                "English dictionary must contain exactly $EXPECTED_WORD_COUNT words, found ${words.size}"
            }
            require(words.map { it.id }.toSet().size == EXPECTED_WORD_COUNT) {
                "English dictionary contains duplicate ids"
            }
            require(words.map { it.word }.toSet().size == EXPECTED_WORD_COUNT) {
                "English dictionary contains duplicate headwords"
            }
            require(words.all { it.translation.isNotBlank() }) {
                "Every English word must have a Russian translation"
            }
            return words
        }

        fun parseBundle(input: InputStream): EnglishDictionaryBundle {
            val words = parse(input)
            return EnglishDictionaryBundle(
                words = words,
                senses = words.map { word ->
                    EnglishWordSenseEntity(
                        wordId = word.id,
                        senseOrder = 0,
                        definition = word.hint,
                        translations = word.translation,
                        example = "",
                        exampleTranslation = "",
                        synonyms = "",
                        usageLabels = word.partOfSpeech
                    )
                }
            )
        }

        private fun parseLine(lineNumber: Int, line: String): EnglishWordEntity {
            val columns = line.split('\t', limit = 8)
            require(columns.size == 8) { "Malformed dictionary row $lineNumber" }
            return EnglishWordEntity(
                id = columns[0].toInt(),
                frequencyRank = columns[1].toInt(),
                level = columns[2],
                word = columns[3],
                translation = columns[4],
                hint = columns[5],
                pronunciation = columns[6],
                partOfSpeech = columns[7]
            )
        }
    }
}

data class EnglishDictionaryBundle(
    val words: List<EnglishWordEntity>,
    val senses: List<EnglishWordSenseEntity>
)
