# S Cleaner Privacy Policy (Draft)

**Effective date:** 22 November 2026

S Cleaner is a local Android storage-management application. It does not collect, transmit, sell or upload files, filenames, images, documents, device IDs or analytics to any server. No internet permission is requested in this build.

**Access:** The application requests optional All files access on Android 11+ only when the user activates full shared-storage scanning or the Android system cache-cleanup dialog. A user may instead choose a folder via Android's system document picker. On older Android versions it may request legacy storage permissions.

**Processing:** Files and folder metadata are scanned locally. SHA-256 hashes of eligible duplicates are processed on-device. The app does not store the contents or send them elsewhere. Results and file paths are displayed for user review. Android Settings can revoke permissions at any time.

**Deletion:** Personal files are never deleted automatically. The user must select files and confirm a deletion dialog; deletion may be permanent. System external app-cache cleanup is performed only after user confirmation in Android's system UI. The app can separately erase only its own temporary caches.

**Limitations:** Other applications' private data folders and internal caches remain restricted by Android. Google Play may require separate approval of All files access. This draft should be reviewed and a contact address added by the developer before publishing.
