package com.appname.notes.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.id.UuidV7Generator
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.time.FixedClock
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.DocumentJson
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.SearchFilters
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades real old-version database files (SRS 16.2: migrations never lose data).
 *
 * How: every schema version Room ever exported is committed under `schemas/`. For each old version we
 * build the database exactly as that app version would have (same CREATE statements, same identity
 * hash), fill it, then open it with today's Room. Room runs the migrations and then checks the result
 * against the current schema, failing loudly if anything differs.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schemaDir = File("schemas/${NotesDatabase::class.java.name}")
    private val clock = FixedClock(Instant.parse("2026-10-21T10:00:00Z"))
    private val ids = UuidV7Generator(clock)

    private fun schema(version: Int): JsonObject =
        Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject["database"]!!.jsonObject

    private fun committedVersions(): List<Int> =
        schemaDir.listFiles()!!.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()

    /** Creates the database file of an old app version, empty. */
    private fun createOldDatabase(version: Int, name: String): SQLiteDatabase {
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).forEach { it.delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val s = schema(version)
        assertThat(s["version"]!!.jsonPrimitive.int).isEqualTo(version)
        for (e in s["entities"]!!.jsonArray) {
            val o = e.jsonObject
            val table = o["tableName"]!!.jsonPrimitive.content
            fun sql(v: kotlinx.serialization.json.JsonElement) = v.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            db.execSQL(sql(o["createSql"]!!))
            o["indices"]?.jsonArray?.forEach { db.execSQL(sql(it.jsonObject["createSql"]!!)) }
            o["contentSyncTriggers"]?.jsonArray?.forEach { db.execSQL(sql(it)) }
        }
        s["views"]?.jsonArray?.forEach { v ->
            val o = v.jsonObject
            db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", o["viewName"]!!.jsonPrimitive.content))
        }
        s["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = version
        return db
    }

    private fun openCurrent(name: String) = Room.databaseBuilder(context, NotesDatabase::class.java, name).build()

    @Test
    fun `latest schema is committed`() {
        // CI also fails when the exported schema differs from the committed one.
        assertThat(committedVersions().last()).isEqualTo(2)
    }

    @Test
    fun `every old version upgrades to the latest schema`() = runTest {
        for (v in committedVersions().dropLast(1)) {
            val name = "upgrade-from-$v.db"
            createOldDatabase(v, name).close()
            val db = openCurrent(name)
            // Opening runs the migrations and Room's schema validation; any query forces the open.
            db.noteDao().liveNoteCount()
            db.close()
        }
    }

    @Test
    fun `version 1 notes survive the upgrade and handwriting works afterwards`() = runTest {
        val name = "v1-with-data.db"
        val old = createOldDatabase(1, name)
        val now = clock.now().toEpochMilli()
        val document = NoteDocument(blocks = listOf(TextBlock("b1", BlockKind.PARAGRAPH, RichText("Revise optics chapter"))))
        old.insertOrThrow("notebooks", null, ContentValues().apply {
            put("id", "nb1"); put("title", "Physics"); put("sortOrder", 0); put("createdAt", now); put("updatedAt", now)
        })
        old.insertOrThrow("notes", null, ContentValues().apply {
            put("id", "n1"); put("notebookId", "nb1"); put("title", "Exam plan")
            put("contentJson", DocumentJson.encode(document))
            put("plainText", "Revise optics chapter"); put("preview", "Revise optics chapter"); put("noteType", "TEXT")
            put("isPinned", 1); put("isFavourite", 0); put("isArchived", 0)
            put("createdAt", now); put("updatedAt", now); put("revision", 3)
        })
        old.insertOrThrow("tags", null, ContentValues().apply { put("id", "t1"); put("name", "exam"); put("createdAt", now) })
        old.insertOrThrow("note_tags", null, ContentValues().apply { put("noteId", "n1"); put("tagId", "t1") })
        old.close()

        val db = openCurrent(name)
        val notes = RoomNoteRepository(db, clock, ids, NoOpLogger, zone = { ZoneOffset.UTC })
        val note = notes.getNote(NoteId("n1"))!!
        assertThat(note.title).isEqualTo("Exam plan")
        assertThat(note.document).isEqualTo(document)
        assertThat(note.isPinned).isTrue()
        assertThat(note.tags.map { it.name }).containsExactly("exam")
        assertThat(note.notebookId?.value).isEqualTo("nb1")
        // Search index survived too.
        assertThat(notes.search("optics", SearchFilters()).first().map { it.note.id.value }).containsExactly("n1")

        // The new tables work on the upgraded file.
        val ink = RoomInkRepository(db, clock, ids, NoOpLogger)
        val inkNote = (ink.createInkNote(null, PageTemplate.LINED) as AppResult.Success).value
        val page = (ink.loadPages(inkNote) as AppResult.Success).value.single()
        val pts = StrokePoints(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), intArrayOf(0, 8))
        val stroke = StrokeItem("s1", 1, InkBrush(InkTool.PEN, -16777216, 2f), pts)
        assertThat(ink.commit(page.info.id, InkEdit.add(stroke))).isEqualTo(AppResult.Success(Unit))
        assertThat((ink.loadPages(inkNote) as AppResult.Success).value.single().items).containsExactly(stroke)
        assertThat(notes.observeNotes(NoteCollection.All, NoteSort.MODIFIED).first()).hasSize(2)
        db.close()
    }
}
