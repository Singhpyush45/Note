package com.appname.notes.feature.backup

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MetadataAndBackupTest {

    @Test
    fun resolveSafeZipTarget_blocksZipSlipPathTraversal() {
        val root = File(System.getProperty("java.io.tmpdir"), "papriva_zip_test").apply { mkdirs() }
        try {
            assertNull(PaprivaBackupManager.resolveSafeZipTarget(root, "../evil.db"))
            assertNull(PaprivaBackupManager.resolveSafeZipTarget(root, "databases/../../etc/passwd"))
            assertNull(PaprivaBackupManager.resolveSafeZipTarget(root, "/absolute/path.db"))
            val valid = PaprivaBackupManager.resolveSafeZipTarget(root, "databases/papriva_metadata.db")
            assertNotNull(valid)
            assertTrue(valid!!.canonicalPath.startsWith(root.canonicalPath))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun backupManifest_serializesValidJsonFields() {
        val manifest = BackupManifest(
            createdAtUtcMillis = 1700000000000L,
            fileCount = 7,
            totalBytes = 42_500L,
            sha256Summary = "abc123def456",
        )
        val json = manifest.toJsonString()
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"fileCount\": 7"))
        assertTrue(json.contains("\"totalBytes\": 42500"))
        assertTrue(json.contains("\"sha256Summary\": \"abc123def456\""))
    }
}
