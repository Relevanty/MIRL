package com.personal.sleepalarm.domain.model

import com.personal.sleepalarm.data.db.entity.TaskEntity

/**
 * Task targets use negative ids inside the mixed focus picker so they cannot
 * collide with positive Subject/OtherActivity ids. Database links always use
 * the original positive task id.
 */
fun taskFocusItemId(taskId: Int): Int {
    require(taskId > 0) { "A focus target requires a persisted task id" }
    return -taskId
}

fun focusItemTaskId(itemId: Int?): Int? =
    itemId?.takeIf { it < 0 && it != Int.MIN_VALUE }?.let { -it }

fun TaskEntity.focusActivityType(): FocusActivityType =
    runCatching { FocusActivityType.valueOf(category.trim().uppercase()) }
        .getOrDefault(FocusActivityType.WORK)
