# S Cleaner 2.1.0 — Shared Storage & System Cache Update

## What changed
- **Scan shared storage**: with optional, explicitly approved Android **All files access** permission, S Cleaner can scan the accessible files in primary shared storage — including PDFs, archives, downloads, images, music and videos — for potential junk, duplicates and large files. It does **not** access Android-protected private app folders or claim to scan them.
- **Single-folder privacy alternative**: select a permitted folder using the Android document picker without broad file access.
- **Correct cache feedback**: one-tap app cleanup only removes **S Cleaner's own cache** and labels it accordingly; no misleading "your entire device is clean" status.
- **System external cache cleanup**: Android 11+ exposes an optional system-provided confirmation dialog through `StorageManager.ACTION_CLEAR_APP_CACHE` (requires All files access). This can clear *external cache directories* of apps **after the user's Android confirmation**. If the device doesn't offer this dialog, the app opens manual App Manager shortcuts. It does not clear every app's private/internal cache.
- **Manual file deletion**: zero files selected automatically, explicit review and confirmation before any user file deletion.
- **Scan is local and bounded**: at most 70,000 accessible files, 12,000 directories, depth 28 for the primary device scan; the interface shows "Partial scan" when limits are hit. Hash-based duplicate detection is bounded to avoid excessive data/battery use.

## Storage access limitations
Android 11+ blocks private locations such as other apps' `Android/data` directories even with All files access. On older Android versions, storage permissions and the availability of files vary by API level/OEM. System app caches and Android OS caches are **not** comprehensively cleared. No root, accessibility abuse, hidden deletion or device compromise.

## Privacy
There is no internet permission, analytics or upload. All results remain on device. Storage permission can be revoked in Android Settings. This release includes `MANAGE_EXTERNAL_STORAGE`, a **sensitive restricted permission**. Google Play requires a **permission declaration and approval** for eligible file-management use; it is not automatically Play Store approved. A release-signed AAB, real device tests, Data safety answers and store review are required before publishing.

## Build test APK
GitHub Actions → `Build S Cleaner Android APK` → successful run → `S-Cleaner-v2.1-Debug-APK` artifact ZIP → extract APK. Android SDK 36, Java 17. The build artifact is for device testing, not a signed public release.
