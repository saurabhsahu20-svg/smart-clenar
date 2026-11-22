# S Cleaner v2.0.0

Premium native Android storage assistant, Android 6.0+ and target SDK 36.

## Features
- Dark blue + mint **premium dashboard**, animated 3D-inspired storage ring, dark/light toggle, responsive two-column tool cards.
- One-tap **safe cleanup** removes only S Cleaner's own temporary cache.
- **Junk file review, large files, duplicate finder, file categories and storage analysis** work only inside a folder chosen via Android's system folder picker (Storage Access Framework, SAF).
- **Duplicate detection** verifies contents using SHA-256, compares only size matches, and keeps an original file untouched by default.
- Explicit multi-selection and a separate confirmation dialog are always required before deleting **any personal or shared file**. Deletion cannot be undone.
- App Manager lists discoverable launchable apps and opens Android's own per-app cache screen.
- Downloads and system storage manager shortcuts.
- Foreground-only storage monitoring, no persistent background service.
- No ads, internet access, analytics, broad file permission, QUERY_ALL_PACKAGES, accessibility permissions, or root.

## Important limitations
Android cannot grant this app unrestricted access to other applications' private cache folders. One-tap cleaning therefore **does not** clear cache from other apps. Use App Manager to open official system App Info and select 'Storage & cache → Clear cache'.

On Android 11 and newer, Android's system picker blocks selecting some roots, including the top-level internal storage root and top-level Downloads. Choose a permitted subfolder instead. Scans are bounded (up to 5,000 entries, 12 levels deep); when the limit is reached the UI says results are partial. Duplicates are content-hashed only when eligible within scan budgets. No false 'everything clean' claims.

## Build
GitHub Actions → Build S Cleaner Android APK → a successful run → Artifacts → S-Cleaner-v2-Debug-APK. The downloaded artifact ZIP contains app-debug.apk.

For Google Play, build and sign a **release AAB with a secure signing key**, complete Data safety and store listing / privacy policy, and perform device tests, accessibility testing and Play Console review. This repo's workflow creates a debug APK for testing; it is **not a published Play Store release**.
