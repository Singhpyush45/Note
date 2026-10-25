package com.appname.notes.feature.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.database.NotesDatabase
import com.appname.notes.di.AppDataEntryPoint
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

/**
 * Backup and restore (FR-025) with the real app database: a backup restores the notes and files exactly as they were
 * when it was made, and a damaged or tampered backup changes nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupRestoreTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val data = EntryPointAccessors.fromApplication(app, AppDataEntryPoint::class.java)

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

    private fun titles(db: NotesDatabase): List<String> =
        db.openHelper.readableDatabase.query("SELECT title FROM notes ORDER BY title").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test
    fun backupRestoresNotesAndFiles_andDamagedBackupChangesNothing() = runBlocking {
        ShadowStatFs.registerStats(app.filesDir.parentFile!!, 1_000_000, 1_000_000, 1_000_000)
        val a = data.notes().createNote(null).ok()
        data.notes().setTitle(a, "Physics").ok()
        val photo = File(app.filesDir, "attachments/photo.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(5000) { it.toByte() }) }

        val zip = ByteArrayOutputStream().also { data.backups().export(it) }.toByteArray()

        // Changes after the backup.
        val b = data.notes().createNote(null).ok()
        data.notes().setTitle(b, "Chemistry").ok()
        photo.delete()

        // A tampered copy is refused and nothing is staged.
        val tampered = retouch(zip, "files/attachments/photo.jpg")
        assertThat(data.backups().prepareRestore(ByteArrayInputStream(tampered))).isEqualTo(BackupManager.Prepared.Damaged)
        assertThat(BackupManager.applyPending(app)).isFalse()

        // The real one is checked, then swapped in at "restart".
        val prepared = data.backups().prepareRestore(ByteArrayInputStream(zip), zip.size.toLong())
        assertThat(prepared).isInstanceOf(BackupManager.Prepared.Ready::class.java)
        assertThat(BackupManager.applyPending(app)).isTrue()
        assertThat(BackupManager.consumeRestoreDone(app)).isTrue()

        val reopened = NotesDatabase.create(app)
        try {
            assertThat(titles(reopened)).contains("Physics")
            assertThat(titles(reopened)).doesNotContain("Chemistry")
        } finally {
            reopened.close()
        }
        assertThat(photo.readBytes()).isEqualTo(ByteArray(5000) { it.toByte() })
    }

    @Test
    fun archiveRefusesUnsafePathsAndNewerBackups() {
        assertThat(BackupArchive.safePath("files/attachments/a.jpg")).isTrue()
        assertThat(BackupArchive.safePath("../evil")).isFalse()
        assertThat(BackupArchive.safePath("/abs")).isFalse()
        assertThat(BackupArchive.safePath("a//b")).isFalse()

        val dir = File(app.cacheDir, "arch-test").apply { deleteRecursively(); mkdirs() }
        val db = File(dir, "notes.db").apply { writeText("x") }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, listOf(BackupArchive.DB_PATH to db), "t", dbVersion = 99, createdAt = 1L)
        val r = BackupArchive.extract(ByteArrayInputStream(out.toByteArray()), File(dir, "x"), maxDbVersion = 10)
        assertThat(r).isEqualTo(BackupArchive.ReadResult.TooNew)

        val evil = ByteArrayOutputStream().also { bo ->
            ZipOutputStream(bo).use { z -> z.putNextEntry(ZipEntry("../../evil.txt")); z.write(1); z.closeEntry() }
        }.toByteArray()
        val r2 = BackupArchive.extract(ByteArrayInputStream(evil), File(dir, "y"), maxDbVersion = 10)
        assertThat(r2).isInstanceOf(BackupArchive.ReadResult.Damaged::class.java)
        assertThat(File(dir.parentFile, "evil.txt").exists()).isFalse()
    }

    /** The same zip with one byte of [path] changed. */
    private fun retouch(zip: ByteArray, path: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(zip)).use { zin ->
            ZipOutputStream(out).use { zout ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val bytes = zin.readBytes()
                    if (e.name == path) bytes[0] = (bytes[0] + 1).toByte()
                    zout.putNextEntry(ZipEntry(e.name))
                    zout.write(bytes)
                    zout.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
