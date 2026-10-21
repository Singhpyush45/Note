# App-specific R8 rules. Libraries (Hilt, Compose, kotlinx.serialization, Navigation 3) ship their own
# consumer rules. CI installs and launches the minified release build to catch missing rules early.

# PdfBox-Android (PDF export, ADR-0009 Part B) ships no consumer rules.
# - JPEG 2000 support is an optional extra library (com.gemalto.jp2) we don't include; without it PdfBox
#   skips such images. Its references are expected to be missing.
# - PdfBox creates some classes by reflection (security handlers, filters, fonts), so it is kept whole
#   rather than risking an export that only fails in release builds. Costs some APK size; revisit with a
#   release-build export test.
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
