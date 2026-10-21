# ADR-0006: Logging through an interface with redaction

Status: Accepted · 2026-10-21

## Decision
- Code logs only via `AppLogger` (`:core:common`). No direct `android.util.Log` calls in features.
- Every message passes through `LogRedactor` before output. It masks: email addresses, `Bearer`/JWT tokens,
  `password|token|secret|api_key = value` pairs, and digit runs of 12+ (card/account-like numbers).
- Release builds drop DEBUG/VERBOSE logs.
- Policy (SRS 15.2): never log note bodies, handwriting points, filenames or OCR text — redaction is a safety
  net, not permission.

No third-party logging library is needed (keeps dependencies minimal).
