package com.appname.notes.core.common.logging

/**
 * Masks sensitive values in log text (SRS 13.1, 15.2). Order matters: specific patterns run before the
 * generic digit rule so a token is reported as a token.
 */
object LogRedactor {

    const val MASK = "[REDACTED]"

    private val rules: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        // "Authorization: Bearer abc.def" -> "Authorization: Bearer [REDACTED]"
        Regex("""(?i)\b(bearer)\s+[A-Za-z0-9\-._~+/]+=*""") to { m -> "${m.groupValues[1]} $MASK" },
        // JSON Web Tokens: three base64url parts starting with "eyJ".
        Regex("""\beyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+""") to { _ -> MASK },
        // key=value / key: value / "key":"value" for sensitive key names.
        Regex(
            """(?i)(["']?\b(?:password|passwd|pwd|secret|token|access_token|refresh_token|api[_-]?key|apikey|authorization)\b["']?\s*[:=]\s*)(["']?)[^\s"',;&}]+(["']?)""",
        ) to { m -> "${m.groupValues[1]}${m.groupValues[2]}$MASK${m.groupValues[3]}" },
        // Email addresses.
        Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""") to { _ -> MASK },
        // 12+ digits, optionally separated by spaces/dashes (card, account, phone-like numbers).
        Regex("""\b\d(?:[ \-]?\d){11,}\b""") to { _ -> MASK },
    )

    fun redact(text: String): String =
        rules.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
}
