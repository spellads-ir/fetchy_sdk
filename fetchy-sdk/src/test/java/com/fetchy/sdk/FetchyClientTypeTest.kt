package com.fetchy.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class FetchyClientTypeTest {

    @Test
    fun fromWireValueOrDefault_returnsFlutterAndroid_forFlutterValue() {
        assertEquals(
            FetchyClientType.FLUTTER_ANDROID,
            FetchyClientType.fromWireValueOrDefault("flutter_android")
        )
    }

    @Test
    fun fromWireValueOrDefault_returnsAndroidNative_forUnknownValue() {
        assertEquals(
            FetchyClientType.ANDROID_NATIVE,
            FetchyClientType.fromWireValueOrDefault("unknown")
        )
    }

    @Test
    fun fromWireValueOrDefault_returnsAndroidNative_forNull() {
        assertEquals(
            FetchyClientType.ANDROID_NATIVE,
            FetchyClientType.fromWireValueOrDefault(null)
        )
    }
}