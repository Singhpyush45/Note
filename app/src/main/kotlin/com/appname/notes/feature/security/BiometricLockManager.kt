package com.appname.notes.feature.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Step 4 — Production BiometricPrompt & `FLAG_SECURE` Screen-Privacy Manager.
 * - Authenticates locked notes via Android `BiometricPrompt` (`BIOMETRIC_STRONG` + `DEVICE_CREDENTIAL`).
 * - Falls back cleanly to your app PIN dialog when biometrics/device credentials are unavailable.
 * - Provides [SecureScreenPrivacyEffect] to set `WindowManager.LayoutParams.FLAG_SECURE`
 *   while a locked note is open, preventing screenshots and Android Recents previews.
 */
object BiometricLockManager {

    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val status = BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        context: Context,
        title: String = "Unlock private note",
        subtitle: String = "Verify your identity to open this locked note",
        onAuthenticated: () -> Unit,
        onFallbackToPin: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val activity = context.findFragmentActivity()
        if (activity == null || !canAuthenticate(context)) {
            onFallbackToPin()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> onFallbackToPin()
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Unit
                        else -> onError(errString.toString())
                    }
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        prompt.authenticate(promptInfo)
    }

    fun Context.findActivity(): Activity? {
        var ctx: Context = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun Context.findFragmentActivity(): FragmentActivity? = findActivity() as? FragmentActivity
}

/**
 * Sets `WindowManager.LayoutParams.FLAG_SECURE` whenever [enabled] is `true`,
 * blocking screenshots, screen recordings, and Android Recents previews of locked notes.
 * Automatically clears the flag when leaving the locked note.
 */
@Composable
fun SecureScreenPrivacyEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val window = with(BiometricLockManager) { context.findActivity()?.window }
        if (enabled && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (enabled && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
