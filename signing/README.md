# Test-build signing key

`test-builds.jks` signs the debug and release APKs that CI builds for **testing on the owner's devices**.
It is committed on purpose, like Android's standard debug key:

- every CI build is signed with the same key, so a newer test build installs **over** the old one and your
  notes are kept (a different key would force an uninstall, which deletes app data);
- it is **not a secret and not the production key**. Anyone could sign an APK with it, so only install test
  builds from this repository's releases page.

The Play Store upload key is created in Phase 8, kept outside the repository (CI secret), and never committed
(SRS 13.1). Apps signed with this test key can't be updated to the store version; uninstall first then.
