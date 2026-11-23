# Step 4 — BiometricPrompt, Android Keystore & `FLAG_SECURE` Privacy (`v37`)

## What's Included
1. `SecureNoteKeystore.kt`: Hardware-backed `AndroidKeyStore` AES-256-GCM encryption + salted PBKDF2-HMAC-SHA256 (120,000 iterations) constant-time PIN verifier.
2. `BiometricLockManager.kt`: `BiometricPrompt` (`BIOMETRIC_STRONG` + `DEVICE_CREDENTIAL`) with automatic PIN fallback, plus `SecureScreenPrivacyEffect(enabled)` for `FLAG_SECURE` screenshot/Recents protection.
3. `SecurityAndPinHashTest.kt`: Unit tests for salted PBKDF2 hashing and constant-time verification.

## Quick Wiring (3 Lines)
1. In `app/build.gradle.kts` (`dependencies`), add:
   ```kotlin
   implementation("androidx.biometric:biometric:1.1.0")
   ```
2. Ensure your `MainActivity` extends `FragmentActivity` (or `AppCompatActivity`, which already extends `FragmentActivity`).
3. Inside `NoteEditorScreen.kt` and `InkEditorScreen.kt`, add:
   ```kotlin
   com.appname.notes.feature.security.SecureScreenPrivacyEffect(enabled = isLocked)
   ```
   and call `BiometricLockManager.authenticate(...)` when opening a locked note in `LibraryScreen.kt`.
