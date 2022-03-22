package com.yubyf.datastore

import androidx.datastore.preferences.core.Preferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TestDelegateCollector : (Preferences, Preferences.Key<*>?) -> Unit {
    var isCalled = false
        private set
    private var mLatch: CountDownLatch = CountDownLatch(1)
    private var waitingKey: String? = null
        get() {
            check(isCalled) { "Listener was not called" }
            return field
        }
        private set

    fun waitForChange(seconds: Long, waitingKey: String?): Boolean {
        this.waitingKey = waitingKey
        return try {
            mLatch.await(seconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            throw IllegalStateException("Listener wait was interrupted")
        }
    }

    override fun invoke(prefs: Preferences, key: Preferences.Key<*>?) {
        isCalled = true
        if (waitingKey == key?.name) {
            mLatch.countDown()
        }
    }
}