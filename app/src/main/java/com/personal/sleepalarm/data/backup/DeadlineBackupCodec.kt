package com.personal.sleepalarm.data.backup

import com.personal.sleepalarm.data.db.entity.DDayEntity
import com.personal.sleepalarm.util.DeadlineLinks
import org.json.JSONArray
import org.json.JSONObject

internal fun deadlineToBackupJson(deadline: DDayEntity): JSONObject = JSONObject().apply {
    put("id", deadline.id)
    put("title", deadline.title)
    put("targetDate", deadline.targetDate)
    put("projectId", deadline.projectId ?: JSONObject.NULL)
    put("taskId", deadline.taskId ?: JSONObject.NULL)
    put("notes", deadline.notes)
    put("links", JSONArray(DeadlineLinks.decode(deadline.linksJson)))
    put("createdAt", deadline.createdAt)
}

/** The new optional array keeps backups made before deadline links fully compatible. */
internal fun deadlineFromBackupJson(value: JSONObject): DDayEntity = DDayEntity(
    id = value.optInt("id", 0),
    title = value.optString("title"),
    targetDate = value.optString("targetDate"),
    projectId = if (value.isNull("projectId")) null else value.optInt("projectId"),
    taskId = if (value.isNull("taskId")) null else value.optInt("taskId"),
    notes = value.optString("notes", ""),
    linksJson = DeadlineLinks.encode(
        DeadlineLinks.decode(value.optJSONArray("links")?.toString() ?: "[]")
    ),
    createdAt = value.optLong("createdAt", System.currentTimeMillis())
)
