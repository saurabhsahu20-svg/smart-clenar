# S Cleaner v2.2.0 — smoother multi-select and manual file management

S Cleaner is a premium local Android storage assistant for Android 6.0+.

## What's new in v2.2
- **Select All**: select every editable file in the *current* category (Junk, Large Files, Duplicates or Analyze); selecting 1000+ items shows an additional selection prompt.
- **Clear**: deselect everything.
- **Manual selection**: tap individual rows to select/deselect; checkmarks and selected file sizes update dynamically.
- **Rename**: choose exactly one file and tap Rename; name is validated; files are never overwritten, and renaming a provider file depends on Android's Storage Access Framework.
- **Bulk delete**: two independent user confirmations before permanent deletion.
- **Performance**: memory-friendly, recycled ListView rows instead of formatting every scanned item into an extra list on the main thread. Background scans can be interrupted if a new scan is requested. Faster hash-to-hex for SHA-256 duplicate detection.
- **State safety**: no stale selections when switching categories or changing folders; controls are disabled during a scan/delete/rename.

## Supported cleaning
- Clear **S Cleaner's own cache** directly.
- On supported Android 11+ devices, **Clear System External Caches** opens the *Android-controlled confirmation dialog*. It cannot clear every other app's private/internal cache.
- With optional approved **All files access** (Android 11+), scan accessible primary shared storage for junk/duplicate/large files. Protected Android app-private areas cannot be read.
- Without broad storage permission, use **Choose folder** and the standard Storage Access Framework picker to scan a specific permitted folder.
- No personal files are deleted without selection and two confirmation dialogs.

## Limits and Play Store disclosure
Scanning is bounded to protect battery and memory (70,000 files / 12,000 folders / 28 levels for full scans). Hash scanning is bounded to avoid heavy I/O, so some duplicates may not be analyzed. These limits and inaccessible folders may lead to *partial scan* labels. Never treat an empty category as proof that every byte of a device cache has been scanned.
The app requests optional `MANAGE_EXTERNAL_STORAGE` for core shared-storage scanning. Google Play requires eligibility review and declaration for this sensitive permission. To publish, create a release-signed AAB, privacy policy, store listing, and conduct real-device/manual testing. GitHub Actions builds a **debug APK** only.

## Development
GitHub Actions -> Build S Cleaner Android APK -> successful run -> download `S-Cleaner-v2.2-Debug-APK` artifact.

## v2.3: Per-app cache access — Android-compliant
- App Manager makes the official Android **App Info → Storage & cache → Clear cache** route clearer for every launchable app.
- **All Apps & System Apps** opens Android's complete app list, including apps hidden from S Cleaner's launchable-app list.
- **Clear supported external caches** opens the Android-provided confirmation flow if supported (Android 11+ with special all-files access, user-approved). It does **not** delete every app's internal/private cache.
- No misleading claims, hidden accessibility actions, root, privileged permissions, or data deletion without your confirmation.
- Installing this build does not grant S Cleaner direct access to the private cache of every app; Android reserves that capability for privileged system tools.
