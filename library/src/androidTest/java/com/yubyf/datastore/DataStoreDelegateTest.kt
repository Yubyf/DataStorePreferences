package com.yubyf.datastore

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yubyf.datastore.DataStoreDelegate.Companion.getDataStoreDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val PREF_SP_NAME = "default"

@RunWith(AndroidJUnit4::class)
class DataStoreDelegateTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private fun getDataStoreDelegate(): DataStoreDelegate {
        return context.getDataStoreDelegate(PREF_SP_NAME)
    }

    @Before
    fun resetPreferences() {
        val delegate = getDataStoreDelegate().apply {
            this.clear()
        }
        val prefsAll: Map<String, *> = runBlocking { delegate.getAllSuspend() }
        Log.d("TestRunner", "-------------Before Map $prefsAll")
    }

    @Test
    fun testBasicWriteAndRead() {
        val delegate = getDataStoreDelegate().apply {
            put("string", "foobar")
            put("int", -0x1314cfda)
            put("float", 3.14f)
            put("bool", true)
        }
        runBlocking {
            Assert.assertEquals("foobar",
                delegate.getStringSuspend("string", null))
            Assert.assertEquals(-0x1314cfda,
                delegate.getIntSuspend("int", 0))
            Assert.assertEquals(3.14f,
                delegate.getFloatSuspend("float", 0f), 0.0F)
            Assert.assertTrue(delegate.getBooleanSuspend("bool", false))
        }
    }

    @Test
    fun testRemove() {
        val delegate = getDataStoreDelegate().apply {
            put("string", "foobar")
            put("int", -0x1314cfda)
            remove("string")
        }
        runBlocking {
            Assert.assertEquals("default",
                delegate.getStringSuspend("string", "default"))
            Assert.assertEquals(-0x1314cfda,
                delegate.getIntSuspend("int", 0))
        }
    }

    @Test
    fun testClear() {
        val delegate = getDataStoreDelegate().apply {
            put("string", "foobar")
            put("int", -0x1314cfda)
            put("string1", "foobar1")
            put("int1", 300)
            put("string2", "foobar2")
            put("int2", 500)
            clear()
        }
        runBlocking {
            Assert.assertEquals(0, delegate.getAllSuspend().size)
            Assert.assertEquals("default",
                delegate.getStringSuspend("string", "default"))
            Assert.assertEquals(0, delegate.getIntSuspend("int", 0))
        }
    }

    @Test
    fun testGetAll() {
        val delegate = getDataStoreDelegate().apply {
            put("string", "foobar")
            put("int", -0x1314cfda)
            put("float", 3.14f)
            put("bool", true)
        }
        val prefsAll: Map<String, *> = runBlocking { delegate.getAllSuspend() }
        Assert.assertEquals("foobar", prefsAll["string"])
        Assert.assertEquals(-0x1314cfda, prefsAll["int"])
        Assert.assertEquals(3.14f, prefsAll["float"])
        Assert.assertTrue(prefsAll["bool"] == true)
    }

    @Test
    fun testContains() {
        val delegate = getDataStoreDelegate().apply {
            put("string", "foobar")
            put("int", -0x1314cfda)
            put("float", 3.14f)
            put("bool", true)
        }
        runBlocking {
            Assert.assertTrue(delegate.containsSuspend("string"))
            Assert.assertTrue(delegate.containsSuspend("int"))
            Assert.assertFalse(delegate.containsSuspend("nonexistent"))
        }
    }

    @Test
    fun testReadNonexistentPref() {
        val delegate = getDataStoreDelegate()
        runBlocking {
            Assert.assertEquals("default",
                delegate.getStringSuspend("nonexistent_string", "default"))
            Assert.assertEquals(1337,
                delegate.getIntSuspend("nonexistent_int", 1337))
        }
    }

    @Test
    fun testStringSetWriteAndRead() {
        val set = setOf("Chocola", "Vanilla", "Coconut", "Azuki", "Maple", "Cinnamon")
        val delegate = getDataStoreDelegate().apply {
            put("pref", set)
        }
        Assert.assertEquals(set, runBlocking { delegate.getStringSetSuspend("pref", null) })
    }

    @Test
    fun testEmptyStringSetWriteAndRead() {
        val set = setOf<String>()
        val delegate = getDataStoreDelegate().apply {
            put("pref", set)
        }
        Assert.assertEquals(set, runBlocking { delegate.getStringSetSuspend("pref", null) })
    }

    @Test
    fun testSetContainingEmptyStringWriteAndRead() {
        val set = setOf("")
        val delegate = getDataStoreDelegate().apply {
            put("pref", set)
        }
        Assert.assertEquals(set, runBlocking { delegate.getStringSetSuspend("pref", null) })
    }

    @Test
    fun testReadStringAsStringSetFail() {
        val delegate = getDataStoreDelegate().apply {
            put("pref", "foo;bar;")
        }
        try {
            runBlocking {
                val set = delegate.getStringSetSuspend("pref", null)
            }
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testReadStringSetAsStringFail() {
        val set = setOf("foo", "bar")
        val delegate = getDataStoreDelegate().apply {
            put("pref", set)
        }
        try {
            runBlocking {
                val str = delegate.getStringSuspend("pref", null)
            }
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testReadBooleanAsIntFail() {
        val delegate = getDataStoreDelegate().apply {
            put("pref", true)
        }
        try {
            runBlocking {
                delegate.getIntSuspend("pref", 0)
            }
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testReadIntAsBooleanFail() {
        val delegate = getDataStoreDelegate().apply {
            put("pref", 42)
        }
        try {
            runBlocking {
                delegate.getBooleanSuspend("pref", false)
            }
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testPreferenceChangeCollector() {
        val delegate = getDataStoreDelegate()
        val collector = TestDelegateCollector()
        var collectorJob: Job? = null
        try {
            collectorJob = delegate.collect(collector)
            delegate.put("foobar", 1337)
            Assert.assertTrue(collector.waitForChange(1))
            Assert.assertTrue(collector.isCalled)
        } finally {
            collectorJob?.cancel()
        }
    }

    @Test
    fun testPreferenceChangeCollectorClear() {
        val delegate = getDataStoreDelegate().apply {
            put("foobar", 1337)
        }
        val collector = TestDelegateCollector()
        var collectorJob: Job? = null
        try {
            collectorJob = delegate.collect(collector)
            delegate.clear()
            Assert.assertTrue(collector.waitForChange(1))
            // DataStore does not support partial updates.
            Assert.assertNull(collector.key)
        } finally {
            collectorJob?.cancel()
        }
    }

    @Test
    fun testCancelPreferenceCollector() {
        val delegate = getDataStoreDelegate()
        val collector = TestDelegateCollector()
        var collectorJob: Job? = null
        try {
            collectorJob = delegate.collect(collector)
            collectorJob.cancel()
            delegate.put("foobar", 1337)
            Assert.assertFalse(collector.waitForChange(1))
        } finally {
            collectorJob?.cancel()
        }
    }

    @Test
    fun testRecollectPreference() {
        val delegate = getDataStoreDelegate()
        val collector = TestDelegateCollector()
        var collectorJob: Job? = null
        try {
            collectorJob = delegate.collect(collector)
            collectorJob.cancel()
            delegate.put("foobar", 1337)
            Assert.assertFalse(collector.waitForChange(1))
            collectorJob = delegate.collect(collector)
            delegate.clear()
            Assert.assertTrue(collector.waitForChange(1))
            // DataStore does not support partial updates.
            Assert.assertNull(collector.key)
        } finally {
            collectorJob?.cancel()
        }
    }
}