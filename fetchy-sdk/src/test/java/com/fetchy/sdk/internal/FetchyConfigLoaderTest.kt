package com.fetchy.sdk.internal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FetchyConfigLoaderTest {
    @Test
    fun omitsFirebaseWhenTheBlockIsAbsent() {
        val config = FetchyConfigLoader.fromJson(baseJson())
        assertNull(config.firebase)
        assertFalse(JSONObject(FetchyConfigLoader.toJson(config)).has("firebase"))
    }

    @Test
    fun parsesTheFirebaseBlockAndRoundTripsIt() {
        val config = FetchyConfigLoader.fromJson(
            baseJson(
                """
                ,"firebase": {
                  "project_id": " pull-notif ",
                  "application_id": "1:1:android:abc",
                  "api_key": "key",
                  "gcm_sender_id": "1",
                  "storage_bucket": "pull-notif.appspot.com"
                }
                """.trimIndent()
            )
        )

        assertEquals("pull-notif", config.firebase?.projectId)
        assertEquals("1:1:android:abc", config.firebase?.applicationId)
        assertEquals("key", config.firebase?.apiKey)
        assertEquals("1", config.firebase?.gcmSenderId)
        assertEquals("pull-notif.appspot.com", config.firebase?.storageBucket)

        val again = FetchyConfigLoader.fromJson(FetchyConfigLoader.toJson(config))
        assertEquals(config.firebase, again.firebase)
        assertEquals("firebase", again.push.provider)
    }

    @Test
    fun readsTheCustomerPushProvider() {
        val config = FetchyConfigLoader.fromJson(
            baseJson(""", "push": {"enabled": true, "provider": "customer"}""")
        )
        assertEquals("customer", config.push.provider)
        assertEquals("customer", FetchyConfigLoader.fromJson(FetchyConfigLoader.toJson(config)).push.provider)
    }

    @Test
    fun readsMobileSdkAppIdWhenApplicationIdIsAPackageName() {
        val config = FetchyConfigLoader.fromJson(
            baseJson(
                """
                ,"firebase": {
                  "application_id": "com.example.app",
                  "mobile_sdk_app_id": "1:123:android:abc",
                  "api_key": "key"
                }
                """.trimIndent()
            )
        )
        assertEquals("com.example.app", config.firebase?.applicationId)
        assertEquals("1:123:android:abc", config.firebase?.mobileSdkAppId)
        assertEquals(
            "1:123:android:abc",
            effectiveFirebaseApplicationId(
                config.firebase?.applicationId.orEmpty(),
                config.firebase?.mobileSdkAppId.orEmpty()
            )
        )
    }

    @Test
    fun treatsABlankFirebaseBlockAsAbsent() {
        val config = FetchyConfigLoader.fromJson(
            baseJson(
                """
                ,"firebase": {
                  "project_id": " ",
                  "application_id": "",
                  "api_key": "",
                  "gcm_sender_id": "",
                  "storage_bucket": ""
                }
                """.trimIndent()
            )
        )
        assertNull(config.firebase)
    }

    private fun baseJson(extra: String = ""): String =
        """
        {
          "base_url": "http://10.0.2.2:8090",
          "api_key": "api-key"
          $extra
        }
        """.trimIndent()
}
