# Phase 5 — Capture (attachments, scanner/OCR, audio, reminders): report

Status: **done on CI** (build, JVM tests, lint, emulator tests and APK publish green from a clean checkout).
Device checks by a person: `phase-5-device-checklist.md` (pending, like the Phase 3 and 4 checklists).

## What was built

### 5a — Attachments (FR-009, ADR-0010)
- Attach photos (system photo picker), camera photos, any file (document picker) and **Share → APP_NAME**
  from Gallery/Camera/chats (a new note with the photos). No storage, photo or camera permission is requested.
- A strip under the note title: photo previews (decoded at display size, EXIF-rotated), PDFs show page 1,
  other files as name · type · size rows. Tap opens (photos in an in-app viewer); ⋮ → Open with, Share,
  Remove (undoable), and for PDFs "Write on this PDF" (opens a copy as a PDF note).
- Library cards show the first photo and an attachment count.
- Files are copied into app-private storage, never modified, shared between duplicated notes, and deleted
  only when nothing refers to them (startup and after permanent delete, 10-minute grace).
- Limits/errors: 250 MB per file, 50 MB must stay free, clear messages for unreadable / too large / storage full.
- Database v4 (`attachments`), generated migration.

### 5b — Scanner and text search in documents (FR-011, ADR-0011)
- **Scan document** in the attach menu: Google's document scanner (edge detection, crop, rotate, clean-up,
  multi-page) → saved as a PDF attachment. Unavailable scanner → message, nothing else breaks.
- **Search inside PDFs and photos**: PDF text layer read with PdfBox; pages without text and photos are OCR'd
  on the device with ML Kit (Latin + Devanagari models bundled, nothing leaves the phone). Results show
  "Page 3" / "In Board.jpg" and a PDF-note hit opens at that page.
- Background WorkManager job, one document at a time, not on low battery; retries failures after 6 h (max 3).
- Settings → Privacy → "Find text in photos and PDFs" (off deletes all extracted text).
- Database v5 (`extracted_text` + FTS index, `text_index_jobs`), generated migration.

### 5c — Audio notes (FR-012)
- Attach menu → **Record audio**. Microphone permission is asked only then; refusing explains and nothing else
  is affected. Recording runs in a foreground service (notification with Stop), AAC 64 kbit/s mono.
- While recording: a red bar with the running time (announced to TalkBack), **Add marker** and **Stop**.
  A marker is a link `audio:<recording>?t=<ms>` written into the text at the cursor ("12:40"), so it is saved,
  undoable and searchable like any text.
- The note shows a player (play/pause, seek bar, time) with the markers as chips that play from that point;
  the cursor on a marker offers "Play from 12:40" in the toolbar.
- A recording cut off by the app being killed is attached to its note at the next start.
- Database v6 (`attachments.durationMs`), generated migration.

### 5d — Reminders (FR-010)
- **Text notes:** ⋮ → *Remind me*; on a checklist item the toolbar offers *Remind me about this task*.
  Chips under the title show each reminder ("Tomorrow, 9:00 AM", repeat icon); tap to edit or delete (undo).
- **Handwritten and PDF notes:** ⋮ → *Remind me*; an alarm button in the top bar lists the note's reminders.
- Dialog: quick choices (Later today, This evening, Tomorrow morning, Next week), date and time pickers,
  repeat (none / daily / weekly / monthly / yearly). Times in the past can't be saved.
- **Notification** per reminder: the task (or note title), the note it's in, tap opens the note, *Snooze 10 min*;
  "Missed · was due …" when it went off late (phone off). A ticked one-off task doesn't remind.
- **One system alarm** for the soonest reminder (bounded battery use); set again after reboot, app update,
  clock/time-zone change and when exact-alarm access changes.
- **Permissions denied without breaking anything:** with notifications refused the reminder is still saved and
  goes off silently; the note shows "Notifications are off, so reminders can't alert you · Turn on". Without
  "Alarms & reminders" access, alarms are inexact and the dialog says they may be a few minutes late.
- Repeats are counted from the picked wall-clock time: "monthly on the 31st" stays on the 31st; 9:00 stays 9:00
  across daylight-saving changes. Missed repeats go off once, not once per missed day.
- Library cards show the next reminder. A note with only a reminder is not discarded as empty.
  Duplicating a note doesn't copy its reminders (no double alerts). Trashed notes don't remind; restoring does.
- Database v7 (`reminders`), generated migration.

## Tests
- JVM: domain rules (repeat maths incl. DST and month ends, quick choices), repositories (attachments, text
  index, reminders), Robolectric flows through the real app (attachments, document search, audio player,
  reminders UI incl. handwritten notes), alarm/notification behaviour with Robolectric's AlarmManager
  and NotificationManager (soonest alarm, inexact fallback, missed, snooze, denied notifications, reboot).
- Emulator (API 35): real files and previews, OCR on generated images/PDFs, recording through the foreground
  service and recovery after a kill, **a real reminder alarm ringing and posting its notification**,
  process-death e2e with PDFs and shared photos, memory under repeated ink/PDF use.
- Migration tests: every committed schema version (v1–v7) upgrades to the latest with data kept.

## Known limitations (deliberate, with follow-up)
1. Pictures can't sit between paragraphs (attachments are a strip) — the multimodal note (Phase 8a) does that.
2. OCR reads printed text, not handwriting; scanned PDFs are OCR'd up to 40 pages.
3. Audio files picked from storage attach as files, not playable recordings; no playback speed or rename yet → 5e.
4. Reminders: changing time zone keeps a repeating reminder's *next* time at the old moment; after it rings it
   follows the new zone's wall clock. Snooze length is fixed at 10 minutes.
5. Reminder notifications can't tick a task ("Done"); the note opens instead (an open editor could overwrite it).
6. The test APK is larger (bundled OCR models); the Play Store build will be split per device.

## Owner actions
- Run `phase-5-device-checklist.md` on your phone (and the pending Phase 3/4 checklists); report any ✗.

## Next
5e (audio extras + English voice typing), then Phase 6 — the new Liquid Glass UI, home, + menu, templates and
page settings (see `roadmap-v2.md`).
