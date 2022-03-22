package com.yubyf.datastore

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [Reference](https://github.com/apsun/RemotePreferences/blob/master/testapp/src/main/java/com/crossbowffs/remotepreferences/testapp/TestPreferenceListener.java)
 */
class TestPreferenceListener : OnSharedPreferenceChangeListener {
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        isCalled = true
        this.key = key
        mLatch.countDown()
    }

}