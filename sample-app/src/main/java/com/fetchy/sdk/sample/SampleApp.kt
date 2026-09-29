package com.fetchy.sdk.sample

import android.app.Application
import com.fetchy.sdk.Fetchy
import com.fetchy.sdk.FetchyLogLevel

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fetchy.setLogLevel(FetchyLogLevel.DEBUG)
        Fetchy.initialize(this)
    }
}
