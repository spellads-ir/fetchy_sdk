package com.fetchy.sdk.internal

import com.fetchy.sdk.FetchyLogLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchyLogTest {
    @After
    fun resetLevel() {
        FetchyLog.level = FetchyLogLevel.NONE
    }

    @Test
    fun redactKeepsOnlyTheFirstEightCharacters() {
        assertEquals("abcdefgh…", FetchyLog.redact("abcdefghijklmnop"))
        assertEquals("short…", FetchyLog.redact("short"))
        assertEquals("", FetchyLog.redact(null))
        assertEquals("", FetchyLog.redact(""))
    }

    @Test
    fun noneSuppressesEveryLevel() {
        FetchyLog.level = FetchyLogLevel.NONE
        assertFalse(FetchyLog.enabled(FetchyLogLevel.ERROR))
        assertFalse(FetchyLog.enabled(FetchyLogLevel.DEBUG))
        FetchyLog.e("silent")
        FetchyLog.i("silent")
        FetchyLog.d("silent")
    }

    @Test
    fun infoIncludesErrorsAndDropsDebug() {
        FetchyLog.level = FetchyLogLevel.INFO
        assertTrue(FetchyLog.enabled(FetchyLogLevel.ERROR))
        assertTrue(FetchyLog.enabled(FetchyLogLevel.INFO))
        assertFalse(FetchyLog.enabled(FetchyLogLevel.DEBUG))
    }
}
