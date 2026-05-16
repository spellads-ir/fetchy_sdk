package com.fetchy.sdk.internal.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchyLinkPolicyTest {
    @Test
    fun isHttpUrl_returnsTrueForHttpAndHttps() {
        assertTrue(FetchyLinkPolicy.isHttpUrl("http://example.com"))
        assertTrue(FetchyLinkPolicy.isHttpUrl("https://example.com"))
        assertTrue(FetchyLinkPolicy.isHttpUrl("  HTTPS://example.com/path"))
    }

    @Test
    fun isHttpUrl_returnsFalseForInternalLinks() {
        assertFalse(FetchyLinkPolicy.isHttpUrl("myapp://home"))
        assertFalse(FetchyLinkPolicy.isHttpUrl("app://screen/123"))
    }

    @Test
    fun shouldSendDirectClickAck_onlyForInternalLinks() {
        assertFalse(FetchyLinkPolicy.shouldSendDirectClickAck(null))
        assertFalse(FetchyLinkPolicy.shouldSendDirectClickAck(""))
        assertFalse(FetchyLinkPolicy.shouldSendDirectClickAck("https://example.com/a"))
        assertFalse(FetchyLinkPolicy.shouldSendDirectClickAck("http://example.com/a"))
        assertTrue(FetchyLinkPolicy.shouldSendDirectClickAck("myapp://home"))
    }
}
