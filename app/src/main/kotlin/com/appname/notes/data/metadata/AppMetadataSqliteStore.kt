package com.appname.notes.data.metadata

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Step 2 — Unified Transactional SQLite Metadata Store (`papriva_metadata.db`).
 * Replaces scattered SharedPreferences files (`noteflow_cover_management_v1`,
 * `noteflow_folder_cover_studio_v1`, `noteflow_image_layout_store_v1`) with an indexed,
 * WAL-mode SQLite database featuring:
 *  - Automatic one-time migration from legacy SharedPreferences (zero data loss)
 *  - Atomic transactions for cover, image-layout, and global preference writes
 *  - Orphaned metadata pruning when notes or folders are permanently deleted
 *  - Explicit WAL checkpointing prior to local `.zip` backup export
 */
class AppMetadataSqliteStore private constructor(
    private val appContext: Context,
) : SQLiteOpenHelper(appContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_COVERS (
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                encoded_config TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (entity_type, entity_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_covers_type ON $TABLE_COVERS(entity_type)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_IMAGE_LAYOUTS (
                image_key TEXT PRIMARY KEY NOT NULL,
                encoded_config TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PREFS (
                pref_key TEXT PRIMARY KEY NOT NULL,
                pref_value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versioned forward-only migrations will be added here when DB_VERSION increments.
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    /**
     * Performs a safe, idempotent one-time migration of legacy SharedPreferences entries
     * into `papriva_metadata.db`. Never deletes legacy keys until the SQLite transaction commits.
     */
    fun migrateLegacyPreferencesIfNeeded() {
        if (getPreference(KEY_LEGACY_MIGRATED) == "1") return
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            // 1. Migrate NoteCoverStore (`noteflow_cover_management_v1`)
            val notePrefs = appContext.getSharedPreferences(LEGACY_NOTE_PREFS, Context.MODE_PRIVATE)
            for ((k, v) in notePrefs.all) {
                val str = v as? String ?: continue
                when {
                    k.startsWith("note_cover_") -> {
                        upsertCoverInternal(db, TYPE_NOTE, k.removePrefix("note_cover_"), str, now)
                    }
                    k.startsWith("notebook_cover_") -> {
                        upsertCoverInternal(db, TYPE_NOTEBOOK, k.removePrefix("notebook_cover_"), str, now)
                    }
                    k == KEY_DISPLAY_MODE -> {
                        upsertPrefInternal(db, KEY_DISPLAY_MODE, str, now)
                    }
                }
            }

            // 2. Migrate FolderCoverStore (`noteflow_folder_cover_studio_v1`)
            val folderPrefs = appContext.getSharedPreferences(LEGACY_FOLDER_PREFS, Context.MODE_PRIVATE)
            for ((k, v) in folderPrefs.all) {
                val str = v as? String ?: continue
                if (k.startsWith("folder_cover_")) {
                    upsertCoverInternal(db, TYPE_FOLDER, k.removePrefix("folder_cover_"), str, now)
                }
            }

            // 3. Migrate NoteImageLayoutStore (`noteflow_image_layout_store_v1`)
            val imagePrefs = appContext.getSharedPreferences(LEGACY_IMAGE_PREFS, Context.MODE_PRIVATE)
            for ((k, v) in imagePrefs.all) {
                val str = v as? String ?: continue
                upsertImageLayoutInternal(db, k, str, now)
            }

            upsertPrefInternal(db, KEY_LEGACY_MIGRATED, "1", now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllCovers(entityType: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        readableDatabase.query(
            TABLE_COVERS,
            arrayOf("entity_id", "encoded_config"),
            "entity_type = ?",
            arrayOf(entityType),
            null,
            null,
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result[c.getString(0)] = c.getString(1)
            }
        }
        return result
    }

    fun putCover(entityType: String, entityId: String, encodedConfig: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertCoverInternal(db, entityType, entityId, encodedConfig, System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteCover(entityType: String, entityId: String) {
        writableDatabase.delete(
            TABLE_COVERS,
            "entity_type = ? AND entity_id = ?",
            arrayOf(entityType, entityId),
        )
    }

    fun getAllImageLayouts(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        readableDatabase.query(
            TABLE_IMAGE_LAYOUTS,
            arrayOf("image_key", "encoded_config"),
            null,
            null,
            null,
            null,
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result[c.getString(0)] = c.getString(1)
            }
        }
        return result
    }

    fun putImageLayout(imageKey: String, encodedConfig: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertImageLayoutInternal(db, imageKey, encodedConfig, System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPreference(key: String): String? {
        readableDatabase.query(
            TABLE_PREFS,
            arrayOf("pref_value"),
            "pref_key = ?",
            arrayOf(key),
            null,
            null,
            null,
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun putPreference(key: String, value: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertPrefInternal(db, key, value, System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Removes orphaned cover rows whose parent notes or folders no longer exist in Room.
     */
    fun pruneOrphanedCovers(activeNoteIds: Set<String>, activeFolderIds: Set<String>): Int {
        var removed = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            val storedNotes = getAllCovers(TYPE_NOTE).keys
            for (id in storedNotes) {
                if (id !in activeNoteIds) {
                    removed += db.delete(TABLE_COVERS, "entity_type = ? AND entity_id = ?", arrayOf(TYPE_NOTE, id))
                }
            }
            val storedFolders = getAllCovers(TYPE_FOLDER).keys
            for (id in storedFolders) {
                if (id !in activeFolderIds) {
                    removed += db.delete(TABLE_COVERS, "entity_type = ? AND entity_id = ?", arrayOf(TYPE_FOLDER, id))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return removed
    }

    /**
     * Forces a full WAL checkpoint so `papriva_metadata.db` is self-contained prior to backup.
     */
    fun checkpointWal() {
        writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
    }

    private fun upsertCoverInternal(
        db: SQLiteDatabase,
        entityType: String,
        entityId: String,
        encodedConfig: String,
        now: Long,
    ) {
        val cv = ContentValues().apply {
            put("entity_type", entityType)
            put("entity_id", entityId)
            put("encoded_config", encodedConfig)
            put("updated_at", now)
        }
        db.insertWithOnConflict(TABLE_COVERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun upsertImageLayoutInternal(
        db: SQLiteDatabase,
        imageKey: String,
        encodedConfig: String,
        now: Long,
    ) {
        val cv = ContentValues().apply {
            put("image_key", imageKey)
            put("encoded_config", encodedConfig)
            put("updated_at", now)
        }
        db.insertWithOnConflict(TABLE_IMAGE_LAYOUTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun upsertPrefInternal(
        db: SQLiteDatabase,
        key: String,
        value: String,
        now: Long,
    ) {
        val cv = ContentValues().apply {
            put("pref_key", key)
            put("pref_value", value)
            put("updated_at", now)
        }
        db.insertWithOnConflict(TABLE_PREFS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        const val DB_NAME = "papriva_metadata.db"
        const val DB_VERSION = 1

        const val TABLE_COVERS = "entity_covers"
        const val TABLE_IMAGE_LAYOUTS = "image_layouts"
        const val TABLE_PREFS = "app_preferences"

        const val TYPE_NOTE = "NOTE"
        const val TYPE_NOTEBOOK = "NOTEBOOK"
        const val TYPE_FOLDER = "FOLDER"

        const val KEY_DISPLAY_MODE = "global_notebook_display_mode"
        private const val KEY_LEGACY_MIGRATED = "legacy_prefs_migrated_v1"

        private const val LEGACY_NOTE_PREFS = "noteflow_cover_management_v1"
        private const val LEGACY_FOLDER_PREFS = "noteflow_folder_cover_studio_v1"
        private const val LEGACY_IMAGE_PREFS = "noteflow_image_layout_store_v1"

        @Volatile
        private var instance: AppMetadataSqliteStore? = null

        fun getInstance(context: Context): AppMetadataSqliteStore {
            return instance ?: synchronized(this) {
                instance ?: AppMetadataSqliteStore(context.applicationContext).also {
                    it.migrateLegacyPreferencesIfNeeded()
                    instance = it
                }
            }
        }

        fun resetInstanceForRestore() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }
    }
}
