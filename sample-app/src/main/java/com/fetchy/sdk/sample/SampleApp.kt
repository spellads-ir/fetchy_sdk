package com.fetchy.sdk.sample

import android.app.Application
import com.fetchy.sdk.Fetchy

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fetchy.initialize(this)
    }
}
