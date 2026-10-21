package com.appname.notes.core.common.result

/**
 * Typed domain errors (SRS Appendix B.2). UI maps these to user-friendly text;
 * raw exception messages and server responses are never shown directly.
 */
sealed interface DomainError {
    /** Input rejected by a business rule, e.g. an empty notebook title. */
    data class Validation(val field: String, val reason: String) : DomainError

    data class NotFound(val entity: String, val id: String) : DomainError

    /** A runtime permission (camera, microphone, notifications) or authorization was denied. */
    data class PermissionDenied(val permission: String) : DomainError

    /** Remote revision differs from our base revision (SRS 11.2 step 6). */
    data class Conflict(val entity: String, val id: String) : DomainError

    /** Local disk/database failure. [lowSpace] lets the UI suggest freeing space (SRS 15.1). */
    data class Storage(val lowSpace: Boolean = false) : DomainError

    data class Network(val retryable: Boolean) : DomainError

    data class UnsupportedFormat(val mimeType: String?) : DomainError

    /** Anything unexpected. [correlationId] links the UI message to redacted diagnostics. */
    data class Unexpected(val correlationId: String) : DomainError
}
