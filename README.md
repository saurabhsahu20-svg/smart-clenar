# S Cache Cleaner — Android

Native Android app for phones and tablets, including Lenovo Tab M11. Supports Android 6.0+ (API 23+).

## Features

- View free and used internal device storage.
- Clean **this app's own cache** in a background task, safely.
- Find installed launchable apps and open their official Android App info to clear their cache manually.
- Open Android storage manager and Downloads.
- Hindi and English interface; no ads, no analytics, no network access, and no dangerous permissions.

**Android privacy restriction:** A normal third-party Android app cannot automatically erase all other apps' cache. To clear another app's cache, select the app, open Android's Storage & cache section and tap **Clear cache**. Do not tap **Clear storage** unless you want to reset its data.

## Build APK on GitHub

Go to **Actions → Build Android APK → Run workflow** (or push to main). After a **successful** workflow run, download the **S-Cache-Cleaner-Debug-APK** artifact from the run. Unzip it to get `app-debug.apk`.

Local build with Android SDK 35, JDK 17 and Gradle 8.9: `gradle :app:assembleDebug`.

This is a debug build for testing, not a Play Store signed release. Device compatibility and build success should be verified before public distribution.
