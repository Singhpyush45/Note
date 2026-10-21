# ADR-0005: SDK levels

Status: Accepted · 2026-10-21 (revisit at Phase 3 after the stylus device matrix is known)

| Setting | Value | Reason |
|---|---|---|
| minSdk | 26 (Android 8.0) | Native `java.time`, adaptive icons; covers almost all active devices. Recent AndroidX releases already require ≥24. |
| compileSdk / targetSdk | 37 (Android 17) | Latest stable platform on CI; Play requires recent targets. |
| JVM target | 17 | Required by AGP 9. |

Features needing newer APIs (dynamic colour = 31+, low-latency ink front-buffer = 29+) check the API level at
runtime and degrade gracefully (SRS 1.3).
