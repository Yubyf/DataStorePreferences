# DataStorePreferences

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yubyf.datastorepreferences/datastorepreferences?color=brightgreen&label=Maven%20Central)](https://search.maven.org/artifact/io.github.yubyf.datastorepreferences/datastorepreferences)
[![API](https://img.shields.io/badge/API-21%2B-blue.svg?style=flat)](https://developer.android.com/reference/android/os/Build.VERSION_CODES.html#LOLLIPOP)
[![License](https://img.shields.io/github/license/Yubyf/DataStorePreferences)](https://github.com/Yubyf/DataStorePreferences/blob/master/LICENSE)

A simple implementation of the Android SharedPreferences interface
for [DataStore](https://developer.android.google.cn/topic/libraries/architecture/datastore).

You can use the synchronous and asynchronous methods of this library like in the instance generated
by `Context#getSharedPreferences()`.

**NOTICE**: this implementation **CANNOT** observe the changed key of the preference
through `registerOnSharedPreferenceChangeListener()` according to
the [DataStore documentation](https://developer.android.google.cn/topic/libraries/architecture/datastore):

> Note: If you need to support large or complex datasets, partial updates, or referential integrity, consider using Room instead of DataStore. DataStore is ideal for small, simple datasets and **does not support partial updates or referential integrity**.

## Installation

1\. Add `mavenCentral()` to your `repositories`:

 ```
 repositories {
     ...
     mavenCentral()
 }
 ```

2\. Added the dependency:

Kotlin DSL

 ```Kotlin
 implementation("io.github.yubyf.datastorepreferences:datastorepreferences:$latest_version")
 ```

Groovy

 ```groovy
 implementation 'io.github.yubyf.datastorepreferences:datastorepreferences:${latest_version}'
 ```

## Usage

### SharedPreferences

Since this library used the Kotlin coroutine, it is recommended to use in Kotlin.

Library provides an extension method of Android Context. You can simply use it like this:

```Kotlin
// The scope in which IO operations and transform functions will execute.
val scope = CoroutineScope(Dispatchers.IO)
// Ture if you want to migrate the SharedPreferences file with current name to DataStore file.
val migrateFromSharedPreferences = true
SharedPreferences preferences = context.getDataStorePreferences (
        "prefFileName",
        scope, // Optional, default is Dispatchers.IO
        migrateFromSharedPreferences, // Optional, default is false.
)
```

Library also provides a series of static overloaded methods for Java:

```Java
SharedPreferences preferences;
preferences = DataStorePreferences.getDataStorePreferences(context, "prefFileName")
preferences = DataStorePreferences.getDataStorePreferences(context, "prefFileName", coroutineScope)
preferences = DataStorePreferences.getDataStorePreferences(context, "prefFileName", coroutineScope, true)
```

**Notice: The received context should be `ApplicationContext` to avoid possible memory leaks.**

### DataStore

You can also use the DataStoreDelegate class to access DataStore preferences when you don't need to
use the SharePreferences interface:

```kotlin
val delegate = context.getDataStoreDelegate(PREF_SP_NAME)
// put value(async)
delegate.put("string", "text")
delegate.put("set", setOf("text"))
delegate.put("int", 42)
delegate.put("float", 42F)
delegate.put("long", 42L)
delegate.put("boolean", true)
// get value(flow)
val all: Flow<Map<String, *>> = delegate.getAll()
val string: Flow<String?> = delegate.getString("string", "default")
val set: Flow<Set<String>?> = delegate.getStringSet("set", null)
val int: Flow<Int> = delegate.getInt("int", 42)
val float: Flow<Float> = delegate.getFloat("float", 42F)
val long: Flow<Long> = delegate.getLong("long", 42L)
val boolean: Flow<Boolean> = delegate.getBoolean("boolean", false)
// contains
val result: Flow<Boolean> = delegate.contains("key")
```

`contain` and all `get` functions have corresponding suspending functions, you can use them in your
own coroutine:

```Kotlin
val delegate = context.getDataStoreDelegate(PREF_SP_NAME)
val result: Boolean = delegate.containsSuspend("key")
val value: (String/Set/Int...) = get(String/StringSet/Int...)Suspend("key", defValue: (String/Set/Int...)(?))
```

## License

```
MIT License

Copyright (c) 2022 Alex Liu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
