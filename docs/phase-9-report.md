# Phase 9 — backup, restore and Google Drive: report

Status: **done on CI** — build 135 (run 40232871708): 465 JVM/Robolectric + 39 emulator tests, 0 failures, lint 0 errors, test APK published. Nothing has been tried on a real phone yet; Drive sign-in can't work until the
owner registers the app with Google (below).

## What was built
Settings › **Backup & restore**:
- **Back up to a file**: all notes, drawings, PDFs, photos/attachments, recordings, wallpapers and settings in one
  `.zip` saved wherever the user picks (Downloads, a USB stick, Drive through the file picker…).
- **Restore from a file**: the backup is checked completely first (every file's checksum, the database's own
  integrity check, not made by a newer app version, enough free space). Then a dialog shows its date and size;
  **Restore and restart** replaces everything and restarts the app; "Restore finished." is shown after. If anything
  is wrong, a message says so and nothing on the phone changes.
- **Back up to Google Drive** (sign in with Google): backups go to the app's private folder in the user's Drive
  (only this app can see it; the app can't see the user's other files).
  - **Back up to Drive now** (shows the account and the last backup time), with progress.
  - **Restore from Drive**: pick one of the backups, it is downloaded and checked like a file restore.
  - **Back up every day**: automatic, on Wi-Fi while charging; the newest 5 backups are kept.
  - **Disconnect Google Drive**: cancels the app's access.
- Design: docs/adr/0015-backup-and-drive.md.

## Owner setup needed for Drive (one time, free)
Without this, "Back up to Google Drive" shows "This build isn't registered with Google yet". File backup works anyway.
1. https://console.cloud.google.com → create a project (any name).
2. APIs & Services › Library › **Google Drive API** › Enable.
3. APIs & Services › OAuth consent screen (Google Auth Platform): app name, support email; Audience: External,
   add your own Google account under **Test users** (while the app is in "Testing", only test users can sign in);
   Data access: add scope `.../auth/drive.appdata`.
4. Clients › Create client › **Android**, twice:
   - package `com.appname.notes.debug`, SHA-1 `FE:1D:90:10:AB:2C:50:13:2C:E3:34:EC:8B:80:30:88:B9:92:73:01`
   - package `com.appname.notes`, same SHA-1 (both test APKs are signed with `signing/test-builds.jks`).
   No client ID or secret goes into the app.
5. When the real name and Play signing key exist, add one more Android client with Play's app-signing SHA-1.

## Verification (CI)
- Robolectric, real app and database: back up → change notes and delete a photo → a tampered copy is refused and
  changes nothing → the real backup restores exactly the backed-up notes and the photo's bytes.
- Archive: refuses `../` and absolute paths (nothing written outside), refuses a newer database version.
- Not testable on CI: Google sign-in and the Drive calls (need a registered app and a real account).

## Known limits
- Whole-library backups, not live sync: editing on two phones and merging is not supported; restoring replaces.
- Drive needs Google Play services. Large libraries take a while to upload on slow networks (resumable chunks).
- The automatic backup can't show the Google sign-in screen; if Google asks again, Settings shows a notice.
- The backup isn't encrypted beyond Drive's own storage encryption; a file backup is as private as where it is kept.
- Restoring a backup from an older app version runs the normal database upgrades on it.
