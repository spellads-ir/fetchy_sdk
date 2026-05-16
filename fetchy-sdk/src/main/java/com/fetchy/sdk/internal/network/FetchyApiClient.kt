package com.fetchy.sdk.internal.network

import com.fetchy.sdk.internal.model.AckLinkRequest
import com.fetchy.sdk.internal.model.ActionButton
import com.fetchy.sdk.internal.model.FeedResponse
import com.fetchy.sdk.internal.model.RegisterTokenRequest
import com.fetchy.sdk.internal.model.FetchyNotificationPayload
import com.fetchy.sdk.internal.model.FetchyScope
import com.fetchy.sdk.internal.model.FetchySource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal class FetchyApiClient(
    private val baseUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    fun registerToken(request: RegisterTokenRequest): String {
        val bodyJson = JSONObject()
            .put("app_api_key", request.appApiKey)
            .put("existing_token", request.existingToken.orEmpty())
            .put("client_type", request.clientType)
            .put("device_brand", request.deviceBrand)
            .put("device_model", request.deviceModel)
            .put("android_version", request.androidVersion)
            .put("android_api_level", request.androidApiLevel)
            .put("app_version", request.appVersion)
            .put("sdk_version", request.sdkVersion)

        val responseJson = executeJsonPost("/tokens/register", bodyJson)
        return responseJson.getString("token")
    }

    fun getFeed(token: String, lastRetrieve: Long): FeedResponse {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addEncodedPathSegments("feed")
            .addQueryParameter("token", token)
            .addQueryParameter("last_retrieve", lastRetrieve.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(parseApiError(body, response.code))
            }
            return parseFeedResponse(body)
        }
    }

    fun ackLink(request: AckLinkRequest) {
        executeJsonPost(
            path = "/clicks/ack/link",
            body = JSONObject()
                .put("notification_id", request.notificationId)
                .put("org_id", request.orgId)
                .put("app_id", request.appId)
                .put("link_url", request.linkUrl)
                .put("signature", request.signature)
        )
    }

    private fun executeJsonPost(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(baseUrl.toHttpUrl().newBuilder().addEncodedPathSegments(path.removePrefix("/")).build())
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(parseApiError(responseBody, response.code))
            }
            return if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
        }
    }

    private fun parseFeedResponse(body: String): FeedResponse {
        val json = JSONObject(body)
        val notifications = parseNotificationArray(
            json.optJSONArray("notifications"),
            source = FetchySource.PULL,
            scope = FetchyScope.BROADCAST
        )
        val exclusiveNotifications = parseNotificationArray(
            json.optJSONArray("exclusive_notifications"),
            source = FetchySource.PULL,
            scope = FetchyScope.EXCLUSIVE
        )
        return FeedResponse(notifications = notifications, exclusiveNotifications = exclusiveNotifications)
    }

    private fun parseNotificationArray(
        array: JSONArray?,
        source: FetchySource,
        scope: FetchyScope
    ): List<FetchyNotificationPayload> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val linkUrl = item.optString("link_url").takeIf { it.isNotBlank() }
                    ?: item.optString("deep_link").takeIf { it.isNotBlank() }
                add(
                    FetchyNotificationPayload(
                        source = source,
                        scope = scope,
                        title = item.optString("title").ifBlank { "Fetchy" },
                        body = item.optString("body"),
                        badgeUrl = item.optString("badge_url").takeIf { it.isNotBlank() },
                        imageUrl = item.optString("image_url").takeIf { it.isNotBlank() },
                        linkUrl = linkUrl,
                        actionButtons = parseActionButtonArray(item.optJSONArray("action_buttons")),
                        remoteNotificationId = item.optLong("id").takeIf { it != 0L },
                        orgId = item.optLong("org_id").takeIf { it != 0L },
                        appId = item.optLong("app_id").takeIf { it != 0L },
                        clickAckSignature = item.optString("click_ack_signature").takeIf { it.isNotBlank() },
                        createdAtEpochMs = item.optString("created_at").takeIf { it.isNotBlank() }?.let(::parseBackendTimestamp),
                        fetchyId = item.optString("fetchy_id").takeIf { it.isNotBlank() },
                        schemaVersion = item.optInt("schema_version").takeIf { it != 0 }
                    )
                )
            }
        }
    }

    private fun parseApiError(body: String, code: Int): String {
        return try {
            val message = JSONObject(body).optString("message")
            if (message.isBlank()) {
                "Fetchy API request failed with HTTP $code"
            } else {
                "Fetchy API request failed with HTTP $code: $message"
            }
        } catch (_: Exception) {
            "Fetchy API request failed with HTTP $code"
        }
    }

    private fun parseBackendTimestamp(value: String): Long {
        val trimmed = value.trim()
        val match = BACKEND_TIMESTAMP_REGEX.matchEntire(trimmed)
            ?: throw ParseException("Unsupported backend timestamp: $value", 0)
        val base = match.groupValues[1]
        val fraction = match.groupValues[2]
        val offset = match.groupValues[3]
        val millis = fraction
            .removePrefix(".")
            .take(3)
            .padEnd(3, '0')
        val normalizedOffset = if (offset == "Z") "+0000" else offset.replace(":", "")
        val normalized = "$base.$millis$normalizedOffset"
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.parse(normalized)?.time ?: 0L
    }

    private fun parseActionButtonArray(array: JSONArray?): List<ActionButton> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
                val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                add(ActionButton(title = title, url = url))
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val BACKEND_TIMESTAMP_REGEX = Regex(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$"
        )
    }
}
