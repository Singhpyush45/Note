package com.appname.notes.feature.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.SettingsActionRow
import com.appname.notes.core.designsystem.component.SettingsCard
import com.appname.notes.core.designsystem.component.SettingsSwitchRow

object SecurityTestTags {
    const val LOCK = "settings_app_lock"
    const val HIDE = "settings_hide_recents"
    const val PRIVACY = "settings_privacy_notice"
    const val PRIVACY_DIALOG = "privacy_notice_dialog"
}

/** Settings › Security & privacy: app lock, hide in recent apps, and how data is handled. */
@Composable
fun SecurityCard() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: androidx.activity.compose.LocalActivity.current as? FragmentActivity
    val s by AppLock.settings.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    var notice by rememberSaveable { mutableStateOf(false) }
    val confirmTitle = stringResource(R.string.lock_confirm_title)
    SettingsCard(stringResource(R.string.settings_section_security), icon = Icons.Outlined.Lock) {
        SettingsSwitchRow(
            title = stringResource(R.string.lock_app),
            summary = stringResource(R.string.lock_app_summary),
            checked = s.enabled,
            onCheckedChange = { on ->
                message = null
                if (!on) {
                    AppLock.update(context) { it.copy(enabled = false) }
                } else if (!AppLock.canLock(context) || activity == null) {
                    message = R.string.lock_needs_screen_lock
                } else {
                    // Turning it on is confirmed once, so nobody locks themselves out by mistake.
                    AppLock.prompt(activity, confirmTitle) { ok -> if (ok) AppLock.update(context) { it.copy(enabled = true) } }
                }
            },
            modifier = Modifier.testTag(SecurityTestTags.LOCK),
        )
        if (s.enabled) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LockAfter.entries.forEach { a ->
                    FilterChip(
                        selected = s.after == a,
                        onClick = { AppLock.update(context) { it.copy(after = a) } },
                        label = {
                            Text(
                                stringResource(
                                    when (a) {
                                        LockAfter.IMMEDIATELY -> R.string.lock_after_now
                                        LockAfter.ONE_MINUTE -> R.string.lock_after_1
                                        LockAfter.FIVE_MINUTES -> R.string.lock_after_5
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        message?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        SettingsSwitchRow(
            title = stringResource(R.string.lock_hide_recents),
            summary = stringResource(R.string.lock_hide_recents_summary),
            checked = s.hideInRecents,
            onCheckedChange = { on -> AppLock.update(context) { it.copy(hideInRecents = on) } },
            modifier = Modifier.testTag(SecurityTestTags.HIDE),
        )
        SettingsActionRow(
            title = stringResource(R.string.privacy_notice),
            summary = stringResource(R.string.privacy_notice_summary),
            onClick = { notice = true },
            modifier = Modifier.testTag(SecurityTestTags.PRIVACY),
        )
    }
    if (notice) {
        AlertDialog(
            onDismissRequest = { notice = false },
            modifier = Modifier.testTag(SecurityTestTags.PRIVACY_DIALOG),
            title = { Text(stringResource(R.string.privacy_notice)) },
            text = { Text(stringResource(R.string.privacy_notice_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { notice = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
