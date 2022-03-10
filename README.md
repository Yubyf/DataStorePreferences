# DataStorePreferences

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yubyf.datastorepreferences/datastorepreferences?color=brightgreen&label=Maven%20Central)](https://search.maven.org/artifact/io.github.yubyf.datastorepreferences/datastorepreferences)
[![API](https://img.shields.io/badge/API-21%2B-blue.svg?style=flat)](https://developer.android.com/reference/android/os/Build.VERSION_CODES.html#LOLLIPOP)
[![License](https://img.shields.io/github/license/Yubyf/DataStorePreferences)](https://github.com/Yubyf/DataStorePreferences/blob/master/LICENSE)

A simple implementation of the Android SharedPreferences interface
for [DataStore](https://developer.android.google.cn/topic/libraries/architecture/datastore).

You can use the synchronous and asynchronous methods of this library like in the instance generated
by `Context#getSharedPreferences()`.

**NOTICE**: this implementation **cannot** observe the changed key of the preference through `registerOnSharedPreferenceChangeListener()` according to the [DataStore documentation](https://developer.android.google.cn/topic/libraries/architecture/datastore):

> Note: If you need to support large or complex datasets, partial updates, or referential integrity, consider using Room instead of DataStore. DataStore is ideal for small, simple datasets and **does not support partial updates or referential integrity**.

## Installation

1. Simply copy the source code of the file DataStorePreferences.kt into your project to use it.

   And don't forget to add the "androidx.datastore:datastore-preferences" dependency to your
   build.gradle file.

2. Gradle

    1. Add `mavenCentral()` to your `repositories`:

        ```
        repositories {
            ...
            mavenCentral()
        }
        ```

    2. Added the dependency:

       Kotlin

        ```Kotlin
        implementation("io.github.yubyf.datastorepreferences:datastorepreferences:1.0.0")
        ```

       Groovy

        ```groovy
        implementation 'io.github.yubyf.datastorepreferences:datastorepreferences:1.0.0'
        ```

## Usage

Since this library used the Kotlin coroutine, it is recommanded to use in Kotlin.

Library provides an extension method of Android Context. You can simply use it like this:

```Kotlin
// The scope in which IO operations and transform functions will execute.
val scope = CoroutineScope(Dispatchers.IO)
// Ture if you want to migrate the SharedPreferences file with current name to DataStore file.
val migrateFromSharedPreferences = true
SharedPreferences preferences = context.getDataStorePreferences (
    "prefFileName",
    scope, // Optional, defalut is Dispatchers.IO
    migrateFromSharedPreferences, // Optional, defalut is false.
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
