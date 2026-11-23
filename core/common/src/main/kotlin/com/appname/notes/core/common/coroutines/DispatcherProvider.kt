package com.appname.notes.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Where work runs. Disk/network work must use [io] and CPU-heavy work [default], never [main] (NFR-01).
 * Injected so tests can swap in a single test dispatcher.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}
