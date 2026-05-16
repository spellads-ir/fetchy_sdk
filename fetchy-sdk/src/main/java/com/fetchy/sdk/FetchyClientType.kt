package com.fetchy.sdk

enum class FetchyClientType(internal val wireValue: String) {
    ANDROID_NATIVE("android_native"),
    FLUTTER_ANDROID("flutter_android");

    companion object {
        fun fromWireValueOrDefault(value: String?): FetchyClientType {
            return entries.firstOrNull { it.wireValue == value } ?: ANDROID_NATIVE
        }
    }
}