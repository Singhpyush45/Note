package com.appname.notes.feature.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long the app may be in the background before it locks again. */
enum class LockAfter(val millis: Long) { IMMEDIATELY(0), ONE_MINUTE(60_000), FIVE_MINUTES(300_000) }

data class LockSettings(
    val enabled: Boolean = false,
    val after: LockAfter = LockAfter.IMMEDIATELY,
    /** FLAG_SECURE: no screenshots, and a blank preview in the recent-apps list. */
    val hideInRecents: Boolean = false,
)

/**
 * App lock (SRS 13.1 "optional app lock / biometric gate"): when on, the notes are covered until the user unlocks
 * with a fingerprint, face or the phone's own PIN/pattern/password (Android's BiometricPrompt; the app never sees
 * them). Locks at start and after the app was in the background for [LockSettings.after]. It hides the notes on
 * screen; it doesn't encrypt files or copies the user exports or shares (said in Settings).
 */
object AppLock {
    private const val PREFS = "app_lock"
    private val _settings = MutableStateFlow(LockSettings())
    val settings: StateFlow<LockSettings> = _settings.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundSince = -1L
    private var initialized = false

    /** Reads the setting (small preferences file) and watches the app going to the background. Call once at start. */
    fun init(context: Context, clock: () -> Long = System::currentTimeMillis) {
        if (initialized) return
        initialized = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = LockSettings(
            enabled = p.getBoolean("enabled", false),
            after = LockAfter.entries.firstOrNull { it.name == p.getString("after", null) } ?: LockAfter.IMMEDIATELY,
            hideInRecents = p.getBoolean("hide", false),
        )
        _locked.value = _settings.value.enabled && canLock(context)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    backgroundSince = clock()
                }

                override fun onStart(owner: LifecycleOwner) {
                    if (backgroundSince >= 0 && shouldLock(_settings.value, backgroundSince, clock())) _locked.value = true
                    backgroundSince = -1
                }
            },
        )
    }

    /** Pure rule, tested: lock again when the lock is on and the app was away at least the chosen time. */
    fun shouldLock(s: LockSettings, awaySince: Long, now: Long): Boolean = s.enabled && now - awaySince >= s.after.millis

    fun update(context: Context, change: (LockSettings) -> LockSettings) {
        val s = change(_settings.value)
        _settings.value = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled)
            .putString("after", s.after.name)
            .putBoolean("hide", s.hideInRecents)
            .apply()
        if (!s.enabled) _locked.value = false
    }

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** The phone has a screen lock (or fingerprint/face) to unlock with. */
    fun canLock(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows Android's unlock prompt; [onResult] true when the user proved it's them. */
    fun prompt(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onResult(false) }
    }

    /** Unlock after a successful prompt. */
    fun unlock() {
        _locked.value = false
    }
}
