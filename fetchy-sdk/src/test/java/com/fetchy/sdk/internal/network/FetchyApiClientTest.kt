package com.fetchy.sdk.internal.network

import com.fetchy.sdk.internal.model.AckLinkRequest
import com.fetchy.sdk.internal.model.FetchyScope
import com.fetchy.sdk.internal.model.FetchySource
import com.fetchy.sdk.internal.model.RegisterTokenRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FetchyApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getFeed_usesLinkUrlAndFallsBackToLegacyDeepLink() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "notifications": [
                    {
                      "id": 101,
                      "title": "n1",
                      "body": "b1",
                      "link_url": "https://example.com/a",
                      "created_at": "2026-05-03T11:00:00.000+03:30"
                    },
                    {
                      "id": 102,
                      "title": "n2",
                      "body": "b2",
                      "deep_link": "myapp://screen/1",
                      "created_at": "2026-05-03T11:00:01.000+03:30"
                    }
                  ],
                  "exclusive_notifications": [
                    {
                      "id": 201,
                      "title": "e1",
                      "body": "be1",
                      "link_url": "myapp://offers/201",
                      "created_at": "2026-05-03T11:00:02.000+03:30"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val client = FetchyApiClient(server.url("/").toString().removeSuffix("/"))
        val feed = client.getFeed(token = "backend-token", lastRetrieve = 0)

        assertEquals(2, feed.notifications.size)
        assertEquals(FetchySource.PULL, feed.notifications[0].source)
        assertEquals(FetchyScope.BROADCAST, feed.notifications[0].scope)
        assertEquals("https://example.com/a", feed.notifications[0].linkUrl)

        assertEquals("myapp://screen/1", feed.notifications[1].linkUrl)
        assertTrue(feed.notifications[1].linkUrl!!.startsWith("myapp://"))

        assertEquals(1, feed.exclusiveNotifications.size)
        assertEquals("myapp://offers/201", feed.exclusiveNotifications[0].linkUrl)
    }

    @Test
    fun ackLink_postsToAckLinkEndpointWithLinkUrlField() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val client = FetchyApiClient(server.url("/").toString().removeSuffix("/"))
        client.ackLink(
            AckLinkRequest(
                notificationId = 1201,
                orgId = 55,
                appId = 77,
                linkUrl = "myapp://news/1201",
                signature = "sig-1"
            )
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/clicks/ack/link", request.path)

        val body = request.body.readUtf8()
        assertTrue(body.contains("\"link_url\":\"myapp://news/1201\""))
        assertFalse(body.contains("deep_link"))
        assertTrue(body.contains("\"signature\":\"sig-1\""))
    }

    @Test
    fun getFeed_skipsUnparseableItemAndKeepsTheRest() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "notifications": [
                    {"id": 1, "title": "bad", "body": "b", "created_at": "garbage"},
                    {"id": 2, "title": "good", "body": "ok", "created_at": "2026-05-03T11:00:00Z"}
                  ],
                  "exclusive_notifications": [
                    {"id": 9, "title": "bad-exclusive", "created_at": "not-a-timestamp"},
                    {"id": 10, "title": "good-exclusive", "created_at": "2026-05-03T11:00:01Z"}
                  ]
                }
                """.trimIndent()
            )
        )

        val client = FetchyApiClient(server.url("/").toString().removeSuffix("/"))
        val feed = client.getFeed(token = "backend-token", lastRetrieve = 0)

        assertEquals(1, feed.notifications.size)
        assertEquals(2L, feed.notifications[0].remoteNotificationId)
        assertEquals("good", feed.notifications[0].title)
        assertEquals(1, feed.exclusiveNotifications.size)
        assertEquals(10L, feed.exclusiveNotifications[0].remoteNotificationId)
    }

    @Test
    fun getFeed_readsNextCursorAndEndTime() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "notifications": [
                    {
                      "id": 3,
                      "title": "timed",
                      "body": "b",
                      "created_at": "2026-05-03T11:00:00Z",
                      "end_time": "2026-05-04T11:00:00Z",
                      "push_schedule_type": "recurring",
                      "run_id": 9
                    }
                  ],
                  "next_cursor": 1700000000000
                }
                """.trimIndent()
            )
        )

        val client = FetchyApiClient(server.url("/").toString().removeSuffix("/"))
        val feed = client.getFeed(token = "backend-token", lastRetrieve = 0)
        assertEquals(1_700_000_000_000L, feed.nextCursor)
        assertEquals("broadcast:3:9", feed.notifications.single().dedupeKey())
        assertEquals(
            feed.notifications.single().createdAtEpochMs!! + 24L * 60L * 60L * 1000L,
            feed.notifications.single().endTimeEpochMs
        )
    }

    @Test
    fun getFeed_leavesNextCursorNullWhenTheBackendOmitsIt() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"notifications":[{"id":1,"title":"t","body":"b","created_at":"2026-05-03T11:00:00Z"}]}
                """.trimIndent()
            )
        )
        val client = FetchyApiClient(server.url("/").toString().removeSuffix("/"))
        val feed = client.getFeed(token = "backend-token", lastRetrieve = 0)
        assertEquals(null, feed.nextCursor)
    }

    @Test
    fun registerToken_includesFcmToken() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"device-1"}"""))

        val client = FetchyApiClient(server.url("/").toString().removeSuffix("/"))
        val token = client.registerToken(
            RegisterTokenRequest(
                appApiKey = "api-key",
                existingToken = null,
                clientType = "android_native",
                fcmToken = "fcm-abc",
                deviceBrand = "Google",
                deviceModel = "Pixel",
                androidVersion = "14",
                androidApiLevel = 34,
                appVersion = "1.0",
                sdkVersion = "1.4.0"
            )
        )
        assertEquals("device-1", token)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"fcm_token\":\"fcm-abc\""))
        assertFalse(body.contains("fcm_token_status"))
    }

    @Test
    fun registerToken_sendsUnavailableStatusWhenFirebaseCannotBeRead() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"device-1"}"""))

        val client = FetchyApiClient(server.url("/").toString().removeSuffix("/"))
        client.registerToken(
            RegisterTokenRequest(
                appApiKey = "api-key",
                existingToken = "device-1",
                clientType = "android_native",
                fcmToken = null,
                fcmTokenStatus = "unavailable",
                deviceBrand = "Google",
                deviceModel = "Pixel",
                androidVersion = "14",
                androidApiLevel = 34,
                appVersion = "1.0",
                sdkVersion = "1.5.0"
            )
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"fcm_token_status\":\"unavailable\""))
    }
}
