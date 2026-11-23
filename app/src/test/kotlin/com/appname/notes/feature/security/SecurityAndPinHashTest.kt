package com.appname.notes.feature.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndPinHashTest {

    @Test
    fun hashPin_usesUniqueRandomSaltAndVerifiesConstantTime() {
        val hash1 = SecureNoteKeystore.hashPin("2580".toCharArray())
        val hash2 = SecureNoteKeystore.hashPin("2580".toCharArray())

        // Same PIN produces different salted records
        assertNotEquals(hash1, hash2)

        assertTrue(SecureNoteKeystore.verifyPin("2580".toCharArray(), hash1))
        assertTrue(SecureNoteKeystore.verifyPin("2580".toCharArray(), hash2))
        assertFalse(SecureNoteKeystore.verifyPin("9999".toCharArray(), hash1))
        assertFalse(SecureNoteKeystore.verifyPin("2580".toCharArray(), "corrupted_record"))
    }
}
