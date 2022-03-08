package com.yubyf.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yubyf.datastore.DataStorePreferences.Companion.getDataStorePreferences
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [Reference](https://github.com/apsun/RemotePreferences/blob/master/testapp/src/androidTest/java/com/crossbowffs/remotepreferences/RemotePreferencesTest.java)
 */

private const val PREF_SP_NAME = "default"

@RunWith(AndroidJUnit4::class)
class DataStorePreferencesTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private fun getDataStoreSpImpl(): SharedPreferences {
        return context.getDataStorePreferences(PREF_SP_NAME);
    }

    @Before
    fun resetPreferences() {
        getDataStoreSpImpl().edit().clear().commit()
    }

    @Test
    fun testBasicWriteAndRead() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putString("string", "foobar")
                .putInt("int", -0x1314cfda)
                .putFloat("float", 3.14f)
                .putBoolean("bool", true)
                .apply()
        }
        Assert.assertEquals("foobar", prefs.getString("string", null))
        Assert.assertEquals(-0x1314cfda, prefs.getInt("int", 0))
        Assert.assertEquals(3.14f, prefs.getFloat("float", 0f), 0.0F)
        Assert.assertEquals(true, prefs.getBoolean("bool", false))
    }

    @Test
    fun testRemove() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putString("string", "foobar")
                .putInt("int", -0x1314cfda)
                .apply()
        }
        prefs.edit().remove("string").apply()
        Assert.assertEquals("default", prefs.getString("string", "default"))
        Assert.assertEquals(-0x1314cfda, prefs.getInt("int", 0))
    }

    @Test
    fun testClear() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putString("string", "foobar")
                .putInt("int", -0x1314cfda)
                .apply()
        }
        prefs.edit().clear().apply()
        Assert.assertEquals(0, prefs.all.size.toLong())
        Assert.assertEquals("default", prefs.getString("string", "default"))
        Assert.assertEquals(0, prefs.getInt("int", 0))
    }

    @Test
    fun testGetAll() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putString("string", "foobar")
                .putInt("int", -0x1314cfda)
                .putFloat("float", 3.14f)
                .putBoolean("bool", true)
                .apply()
        }
        val prefsAll: Map<String, *> = prefs.all
        Assert.assertEquals("foobar", prefsAll["string"])
        Assert.assertEquals(-0x1314cfda, prefsAll["int"])
        Assert.assertEquals(3.14f, prefsAll["float"])
        Assert.assertEquals(true, prefsAll["bool"])
    }

    @Test
    fun testContains() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putString("string", "foobar")
                .putInt("int", -0x1314cfda)
                .putFloat("float", 3.14f)
                .putBoolean("bool", true)
                .apply()
        }
        Assert.assertTrue(prefs.contains("string"))
        Assert.assertTrue(prefs.contains("int"))
        Assert.assertFalse(prefs.contains("nonexistent"))
    }

    @Test
    fun testReadNonexistentPref() {
        val prefs = getDataStoreSpImpl()
        Assert.assertEquals("default", prefs.getString("nonexistent_string", "default"))
        Assert.assertEquals(1337, prefs.getInt("nonexistent_int", 1337))
    }

    @Test
    fun testStringSetWriteAndRead() {
        val set = HashSet<String>()
        set.add("Chocola")
        set.add("Vanilla")
        set.add("Coconut")
        set.add("Azuki")
        set.add("Maple")
        set.add("Cinnamon")
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putStringSet("pref", set)
                .apply()
        }
        Assert.assertEquals(set, prefs.getStringSet("pref", null))
    }

    @Test
    fun testEmptyStringSetWriteAndRead() {
        val set = HashSet<String>()
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putStringSet("pref", set)
                .apply()
        }
        Assert.assertEquals(set, prefs.getStringSet("pref", null))
    }

    @Test
    fun testSetContainingEmptyStringWriteAndRead() {
        val set = HashSet<String>()
        set.add("")
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putStringSet("pref", set)
                .apply()
        }
        Assert.assertEquals(set, prefs.getStringSet("pref", null))
    }

    @Test
    fun testReadStringAsStringSetFail() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putString("pref", "foo;bar;")
                .apply()
        }
        try {
            prefs.getStringSet("pref", null)
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testReadStringSetAsStringFail() {
        val set = HashSet<String>()
        set.add("foo")
        set.add("bar")
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putStringSet("pref", set)
                .apply()
        }
        try {
            prefs.getString("pref", null)
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testReadBooleanAsIntFail() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putBoolean("pref", true)
                .apply()
        }
        try {
            prefs.getInt("pref", 0)
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testReadIntAsBooleanFail() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putInt("pref", 42)
                .apply()
        }
        try {
            prefs.getBoolean("pref", false)
            Assert.fail()
        } catch (e: ClassCastException) {
            // Expected
        }
    }

    @Test
    fun testPreferenceChangeListener() {
        val prefs = getDataStoreSpImpl()
        val listener = TestPreferenceListener()
        try {
            prefs.registerOnSharedPreferenceChangeListener(listener)
            prefs.edit()
                .putInt("foobar", 1337)
                .apply()
            Assert.assertTrue(listener.waitForChange(1))
            Assert.assertTrue(listener.isCalled)
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test
    fun testPreferenceChangeListenerClear() {
        val prefs = getDataStoreSpImpl().apply {
            edit()
                .putInt("foobar", 1337)
                .apply()
        }
        val listener = TestPreferenceListener()
        try {
            prefs.registerOnSharedPreferenceChangeListener(listener)
            prefs.edit()
                .clear()
                .apply()
            Assert.assertTrue(listener.waitForChange(1))
            // DataStore does not support partial updates.
            Assert.assertNull(listener.key)
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test
    fun testUnregisterPreferenceChangeListener() {
        val prefs = getDataStoreSpImpl()
        val listener = TestPreferenceListener()
        try {
            prefs.registerOnSharedPreferenceChangeListener(listener)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            prefs.edit()
                .putInt("foobar", 1337)
                .apply()
            Assert.assertFalse(listener.waitForChange(1))
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}