package com.appname.notes.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.appname.notes.R

const val LOCK_SCREEN_TAG = "app_lock_screen"

/** Covers the notes while the app is locked; asks for the fingerprint / PIN at once and again on "Unlock". */
@Composable
fun LockScreen(activity: FragmentActivity) {
    val title = stringResource(R.string.lock_prompt_title)
    val ask = { AppLock.prompt(activity, title) { ok -> if (ok) AppLock.unlock() } }
    LaunchedEffect(Unit) { ask() }
    // Back leaves the app instead of reaching the notes underneath.
    androidx.activity.compose.BackHandler { activity.moveTaskToBack(true) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // Every touch stops here: nothing reaches the notes underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .padding(32.dp)
            .testTag(LOCK_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lock_locked), style = MaterialTheme.typography.titleLarge)
        Button(onClick = ask) { Text(stringResource(R.string.lock_unlock)) }
    }
}
