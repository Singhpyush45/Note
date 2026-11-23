# Step 2 — Unified SQLite Metadata Store & Local `.zip` Backup/Restore (`v35`)

## What's Included
1. `AppMetadataSqliteStore.kt`: Transactional WAL-mode SQLite database (`papriva_metadata.db`) replacing plain SharedPreferences for `NoteCoverStore`, `FolderCoverStore`, and `NoteImageLayoutStore`, with automatic one-time migration from existing SharedPreferences (zero data loss) and orphan-row pruning.
2. `PaprivaBackupManager.kt` + `BackupRestoreSettingsCard`: Atomic local `.zip` export (with WAL checkpointing and SHA-256 manifest) and Zip-Slip-safe restore via Android Storage Access Framework.
3. `MetadataAndBackupTest.kt`: Unit tests for Zip-Slip protection and backup manifest serialization.

## How to Wire Into Settings (1 Line)
Inside `SettingsScreen.kt` (under your Data / Storage or Appearance section), add:
```kotlin
com.appname.notes.feature.backup.BackupRestoreSettingsCard(modifier = Modifier.padding(vertical = 8.dp))
```

## Verify
```powershell
.\gradlew.bat testDebugUnitTest assembleDebug --configuration-cache
```
