# Release and Quality Checklist

S Cleaner v2 is a working **debug build foundation**, not a guaranteed error-free or automatically approved Play Store release.

## Automated checks
- Android API 36 project compiles as debug APK with Java 17 + Gradle 8.13 + AGP 8.11.1.
- Android Lint is executed in GitHub Actions for baseline static analysis.
- No INTERNET, MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES or Accessibility Service permissions.
- Folder scanner works through SAF and limits browsing to 5,000 entries, 12 levels.
- SHA-256 verifies candidate duplicates only; data not read into memory wholesale.
- File deletion requires explicit checked-file selection and a final confirmation.
- Storage usage monitoring is foreground-only, with updates at most once every 30 seconds.

## Manual device tests required (not performed by GitHub build)
- Lenovo Tab M11 (portrait and landscape), Samsung / Motorola / Xiaomi phones and Android 6–16.
- Android 11+ folder picker restrictions including Download root.
- Grant and revoke folder access; retry scan; large directories and empty directories.
- Duplicate detection: same filename with different content; same content with different names.
- Deletion: canceled, read-only provider, partial failure, selected-file-only behavior.
- App Manager for launchable apps; non-launchable system apps may not be listed.
- Dark and light mode, accessibility font sizes, TalkBack, screen rotation.
- Battery use, memory stability, crash testing and Android Vitals monitoring.

## Play Store submission
1. Obtain secure signing keys / Google Play App Signing.
2. Build and sign a **release Android App Bundle (AAB)**; do not upload debug APK as a public release.
3. Host a privacy policy with publisher contact details; complete accurate Data safety and content declarations.
4. Create app screenshots, launcher assets, store listing, and correct content rating.
5. Test with internal / closed testing tracks and complete Play Console review and compliance checks.

## Android restrictions
A third-party app cannot remove all other apps' private caches silently. Storage Access Framework may not offer the top-level Downloads folder or internal-storage root on Android 11+. For affected directories, use device Settings or select supported subfolders.
