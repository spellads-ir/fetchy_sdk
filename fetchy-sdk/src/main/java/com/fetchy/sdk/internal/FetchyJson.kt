package com.fetchy.sdk.internal

import com.fetchy.sdk.internal.model.ActionButton
import org.json.JSONArray
import org.json.JSONObject

internal object FetchyJson {
    fun encodeStringList(values: List<String>): String {
        val jsonArray = JSONArray()
        values.forEach(jsonArray::put)
        return jsonArray.toString()
    }

    fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    add(array.optString(index))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeActionButtonList(values: List<ActionButton>): String {
        val jsonArray = JSONArray()
        values.forEach { btn ->
            jsonArray.put(JSONObject().put("title", btn.title).put("url", btn.url))
        }
        return jsonArray.toString()
    }

    fun decodeActionButtonList(raw: String?): List<ActionButton> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val title = obj.optString("title")
                    val url = obj.optString("url")
                    if (url.isNotBlank()) add(ActionButton(title = title, url = url))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
