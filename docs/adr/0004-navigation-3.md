# ADR-0004: Navigation 3 with type-safe keys

Status: Accepted · 2026-10-21

## Decision
Use `androidx.navigation3` 1.2.0. Each screen is a `@Serializable` key implementing `NavKey`; the back stack is
an observable list the app owns (`rememberNavBackStack`), so it survives rotation and process death.
Top-level switching is a pure function (`navigateToTopLevel`) with unit tests.
Shell layout uses Material 3 `NavigationSuiteScaffold` (bottom bar on phones, rail on tablets — SRS 8.1).

## Why not Navigation 2 (`navigation-compose`)?
Navigation 3 is built for Compose state, makes list-detail layouts on tablets simpler (needed for the
notes library + editor), and the back stack is plain data we can test without a device.
