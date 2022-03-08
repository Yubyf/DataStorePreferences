# DataStorePreferences

A simple implementation of the Android SharedPreferences interface for [DataStore](https://developer.android.google.cn/topic/libraries/architecture/datastore).

You can use the synchronous and asynchronous methods of this library like in the instance generated by `Context#getSharedPreferences()`.

    Note: If you need to support large or complex datasets, partial updates, or referential integrity, consider using Room instead of DataStore. DataStore is ideal for small, simple datasets and does not support partial updates or referential integrity.

## Installation

1. Simply copy the source code of the file DataStorePreferences.kt into your project to use it.

    And don't forget to add the "androidx.datastore:datastore-preferences" dependency to your build.gradle file.

2. I'll upload the library to MavenCentral later if needed.