package com.yubyf.datastore

import androidx.datastore.preferences.core.Preferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TestDelegateCollector : (Preferences) -> Unit {
    var isCalled = false
        private set
    private val mLatch: CountDownLatch = CountDownLatch(1)
    var key: String? = null
        get() {
            check(isCalled) { "Listener was not called" }
            return field
        }
        private set

    fun waitForChange(seconds: Long): Boolean {
        return try {
            mLatch.await(seconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException("Listener wait was interrupted")
        }
    }

    override fun invoke(preferences: Preferences) {
        isCalled = true
        key = null
        mLatch.countDown()
    }
}