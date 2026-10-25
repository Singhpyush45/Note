# ADR-0015: Backup and restore; Google Drive app folder

Status: accepted (Phase 9, 26 Oct 2026). Owner choice: "Google Drive app folder"; AI (Phase 10) skipped for now.

## Decision
- **One backup format for everything**: a zip with `db/notes.db` (+ `-wal` if any), `files/<attachments|pdfs|
  recordings|wallpapers|datastore>/…` and `manifest.json` (format, app id, app version, database version, time, and
  for every file its size and SHA-256). Written last, so the hashes are computed while streaming.
- **Consistent database copy**: `PRAGMA wal_checkpoint(TRUNCATE)`, then the database and any log are copied while a
  write transaction is held (other writers wait, so the pair is one state).
- **Restore never half-applies** (FR-025): unpack into `restore-staging/`, verify every checksum, refuse zip-slip
  paths, refuse a newer format or database version, fold the log into the staged database and run
  `PRAGMA integrity_check`. Only then rename to `restore-pending/` with a `READY` marker and restart. At the next
  start, *before Hilt* (so before Room or DataStore open anything) the current files are moved to `restore-old/` and
  the staged ones moved in; any failure moves the originals back. `restore-old/` is removed at the following start.
  An older backup is migrated by Room's normal (non-destructive) migrations when it opens.
- **Drive**: Google Identity `AuthorizationClient` asking only for `https://www.googleapis.com/auth/drive.appdata`
  (a hidden per-app folder; the app cannot see the user's other files). No API key or client secret in the app —
  Google identifies the app by package name + signing certificate SHA-1 registered by the owner as an
  "Android" OAuth client. REST calls over `HttpURLConnection`: resumable upload in 8 MiB chunks, list, download,
  delete; the newest 5 backups are kept. Disconnect revokes the token at `oauth2.googleapis.com/revoke`.
- **Automatic backup** is off by default; when on: WorkManager once a day, unmetered network, charging, storage not
  low. If Google wants the user to sign in again, the worker doesn't show UI; Settings shows a notice.
- `INTERNET` permission added; nothing is sent anywhere unless the user connects Drive.

## Not chosen
- Per-note two-way sync (merging edits from two phones): needs change tracking and conflict rules in the database;
  a later phase. Whole backups are simple and can't corrupt notes.
- Drive Java client library: large, and the needed calls are few.
- Android Auto Backup: limited to 25 MB and not user-controlled per SRS 13.2 (still excluded).
