package com.personal.sleepalarm.data.db.entity

/**
 * Тип элемента библиотеки.
 * Хранится в БД как TEXT через конвертер.
 */
enum class LibraryItemType {
    BOOK,
    MOVIE,
    MUSIC
}