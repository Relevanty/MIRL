package com.personal.sleepalarm.data.english

import com.personal.sleepalarm.data.db.entity.EnglishStudyCardEntity
import com.personal.sleepalarm.data.db.entity.EnglishWordEntity
import com.personal.sleepalarm.domain.english.EnglishStudyDirection
import com.personal.sleepalarm.domain.english.EnglishStudyPrompt

data class EnglishStudyCardCandidate(
    val card: EnglishStudyCardEntity,
    val direction: EnglishStudyDirection,
    val prompt: EnglishStudyPrompt
)

data class EnglishDictionaryWordCandidate(
    val word: EnglishWordEntity,
    val direction: EnglishStudyDirection,
    val prompt: EnglishStudyPrompt
)
