# Phase 11 — release checklist

Status (26 Oct 2026): the code side of release hardening is done (11a) — CI build 144: 474 JVM/Robolectric + 39
emulator tests, 0 failures, lint 0 errors, test APK published. What is left needs the owner (decisions,
accounts, a real phone).

## 11a — done in code
- **App lock** (Settings › Security & privacy): fingerprint / face / the phone's PIN through Android's own prompt;
  locks at start and after the app is away (at once / 1 min / 5 min); turning it on is confirmed once; Back on the
  lock screen leaves the app; screen readers don't read the notes underneath. Needs a screen lock on the phone.
- **Hide notes in recent apps** (blank preview, screenshots blocked — FLAG_SECURE).
- **"How your data is handled"** notice in the app (what stays on the phone, when the internet is used, deleting).
- **HTTPS only** (network security config; no plain-http traffic). Clipping an http link tries https.
- **Delete backups in Drive** (data deletion for the only cloud copy).
- **Scale (NFR-06)**: a CI test fills 10,000 notes and checks the library list (< 5 s) and search (< 3 s) on the
  CI machine's JVM database; it passes. Real timing on a phone is part of the device checks.
- Already in place from earlier phases: R8 shrinking for release, no secrets in the APK, logs redacted, Android
  auto-backup off, permissions asked only when a feature is used, offline-first, migrations tested, StrictMode.

## Owner decisions needed before the Play Store
1. **Name and package id.** `APP_NAME` and `com.appname.notes` are placeholders. The package id can never change
   after the first Play upload — choose it first (e.g. `com.<yourname>.notes`). Then: app name, icon text, Drive
   OAuth clients (new package + SHA-1).
2. **Upload key** (kept by you, never in the repo):
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
   Keep the file and both passwords safe (password manager + an offline copy). CI gets them as GitHub secrets
   (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_PASSWORD`) to build the signed **.aab**.
   Turn on **Play App Signing** (Google keeps the app signing key).
3. **Google Play developer account** (one-time fee) and a **privacy policy URL** (the in-app notice text can be
   published, e.g. on GitHub Pages).

## Play Console answers (draft)
- **Data safety**: no data collected or shared by the developer. Notes stay on the device. Optional: Google Drive
  backup goes to the user's own Drive (user-initiated, not collected by the developer). Web clipping fetches a page
  the user chose. No ads, analytics, crash reporting or tracking.
- **Permissions**: microphone (audio notes / voice typing, asked when used); notifications (reminders, recording);
  exact alarms (on-time reminders, user setting); internet (Drive backup, clipping — only on user action);
  biometric (app lock). No location, contacts, storage-wide access.
- **Content rating**: utility / productivity, no user-generated content shared with others.
- **Target audience**: 13+ (students and adults).
- **Store listing**: short description (80 chars) and full description, 2–8 phone screenshots (the CI screenshot
  tests can produce them once the final name/theme is set), feature graphic 1024×500, icon 512×512.

## Before uploading
- [ ] Device checklists on a real phone: Phase 3–9 (`docs/phase-*-device-checklist.md`), any ✗ fixed.
- [ ] Ink latency and startup checked on the owner's phone (NFR-02/03): note anything that feels slow.
- [ ] Release build from CI with the upload key; install it once from the .aab via internal testing.
- [ ] Internal testing track → closed testing (Play requires 12 testers for 14 days for new personal accounts)
      → production.
