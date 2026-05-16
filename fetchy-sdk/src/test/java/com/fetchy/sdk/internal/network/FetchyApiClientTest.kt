package com.fetchy.sdk.internal.network

import com.fetchy.sdk.internal.model.AckLinkRequest
import com.fetchy.sdk.internal.model.FetchyScope
import com.fetchy.sdk.internal.model.FetchySource
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
}
