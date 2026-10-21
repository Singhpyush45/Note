package com.appname.notes.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.appname.notes.R
import com.appname.notes.core.common.result.DomainError

/**
 * A one-off message for a snackbar, kept in UI state until shown (Google's recommended pattern: state, not events).
 * [id] makes two identical messages in a row still show twice.
 */
data class UserMessage(
    @param:StringRes val text: Int,
    val args: List<String> = emptyList(),
    /** When set, the snackbar shows "Undo" and calls this if tapped. */
    val undo: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

fun DomainError.toUserMessage(): UserMessage = UserMessage(
    when (this) {
        is DomainError.Storage -> if (lowSpace) R.string.msg_storage_full else R.string.msg_storage_error
        else -> R.string.msg_storage_error
    },
)

/** Shows [message] once, then calls [onShown]. */
@Composable
fun MessageEffect(message: UserMessage?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        val text = context.getString(current.text, *current.args.toTypedArray())
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = if (current.undo != null) context.getString(R.string.action_undo) else null,
            duration = SnackbarDuration.Short,
            withDismissAction = current.undo == null,
        )
        if (result == SnackbarResult.ActionPerformed) current.undo?.invoke()
        onShown()
    }
}
