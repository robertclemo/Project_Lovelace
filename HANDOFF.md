# Lovelace (Project Lovelace)

## What this is

An Android app for a small (~10-person) local Ingress Resistance cell. It
bundles the IITC-enhanced Ingress intel map (via WebView + injected
userscript, same approach as the IITC Mobile project) and a private
real-time team chat into one app, plus a synced "leader-follow" map view
for coordinating during ops. It's a companion tool only — it never touches
Ingress gameplay actions or automates anything; it just displays the same
map info a browser with IITC would show.

Kotlin + Jetpack Compose (Material 3), backed by Firebase (Auth +
Firestore). Currently an early, actively-developed private test build —
distributed to the team by sideloaded APK, not the Play Store (see
"Design decisions" below for why).

## Status

**Working today** (built, live-tested on device/emulator, committed to
`main`):
- Intel map: WebView loads `intel.ingress.com` with a vendored IITC-CE
  core script injected. Players sign into Ingress/Google normally inside
  the WebView; the app doesn't touch that session.
- IITC plugin management: enable/disable Player Activity Tracker, Draw
  Tools, and Bookmarks from an in-app list, synced per-user via Firestore,
  applied by reloading (destroying/recreating) the map WebView.
- Team chat: real-time group chat over Firestore. **Not end-to-end
  encrypted** — treat it like a group text.
- Viewport sync (leader-follow): one teammate leads, everyone else's map
  view pans/zooms to follow, via a Firestore-backed pub/sub doc.
- App-scoped display names, decoupled from each player's Google identity.
- Invite-code gate with admin approval before a new sign-up can use chat
  or viewport sync.
- Google Sign-In via Firebase Auth (separate from the in-WebView Ingress
  login).

**Not started / planned but not built** (see
`ingress-team-app-architecture.md` for full design):
- End-to-end encrypted chat (Signal Protocol, sender-keys for group
  fan-out) — current chat is plaintext-over-Firestore.
- Push notifications (FCM).
- SOS/rally ping, persistent shared pins, presence ("who's active"), op
  checklists, key/resource tracking, after-action notes.
- QR-code invites (only a typed invite code exists today).

## Getting it running

Prerequisites:
- Android Studio (or just the Android SDK + a JDK 17–22, per Gradle 8.9's
  supported range).
- A `google-services.json` for the Firebase project, placed at
  `app/google-services.json`. It's gitignored and not in the repo — get
  it from whoever administers the "Roanoke Resistance Lovelace" Firebase
  project, or point the app at your own Firebase project (Auth +
  Firestore enabled).

Steps:
1. Clone the repo, open in Android Studio, let it sync Gradle.
2. Add `app/google-services.json` (see above).
3. Run the `app` module on an emulator or device (min SDK 26).

From the command line:
```
./gradlew assembleDebug
```
If `gradlew` can't find a JDK, point `JAVA_HOME` at one explicitly (JDK
17–22). On at least one dev machine, Android Studio's own bundled JBR
broke for CLI use after an IDE update while a separate JDK 21 Android
Studio had downloaded (`.jdks/jbr-21.0.11`) still worked — if `gradlew`
fails with a `jvm.cfg` error, look for a working JDK under
`~/.jdks/` or check `.idea/gradle.xml` → `gradleJvm` for what the IDE
itself is using.

Package name: `com.roanokeresistance.lovelace`. `applicationId` and
`namespace` both set in `app/build.gradle.kts`.

## Structure

- `app/src/main/java/com/roanokeresistance/lovelace/`
  - `MainActivity.kt`, `LovelaceApp.kt` — app entry point and Compose
    navigation graph (`entry_gate` → `auth`/`join` → `map`/`chat`, plus a
    `plugins` route reachable from the map screen).
  - `auth/` — Google Sign-In screen (Firebase Auth).
  - `profile/` — `EntryGateScreen`, `JoinScreen` (invite code + display
    name entry), `InviteRepository`, `UserProfileRepository` (app-scoped
    identity, decoupled from Google identity).
  - `map/MapScreen.kt` — the WebView, core-script injection, plugin
    injection, viewport-sync JS bridge, "open plugins" button.
  - `plugins/` — `PluginCatalog` (static list of available IITC
    plugins), `PluginInjector` (wraps raw/unbuilt IITC plugin source into
    something IITC's boot mechanism will register — see "Design
    decisions"), `PluginPreferencesRepository` (Firestore-backed
    enabled-plugin list per user), `PluginsScreen` (checkbox UI).
  - `chat/ChatScreen.kt` — real-time Firestore chat, plaintext.
  - `viewportsync/ViewportSyncManager.kt` — leader-publish /
    follower-apply of `{lat, lng, zoom}` over Firestore.
  - `ui/theme/` — Compose Material 3 theme.
- `app/src/main/assets/` — vendored, ISC-licensed third-party JS:
  `iitc_total_conversion.user.js` (IITC-CE core) and `plugins/*.js`/`.css`
  (Player Activity Tracker, Draw Tools + Leaflet.draw/spectrum externals,
  Bookmarks). See `THIRD_PARTY_NOTICES_IITC-CE_LICENSE.txt`.
- `ingress-team-app-architecture.md` — original/full design doc and
  roadmap (includes planned-but-unbuilt features: E2E encryption, SOS
  ping, shared pins, presence, etc.).
- `TEAM-GUIDE.md` — plain-language, non-technical guide for teammates
  installing and using the app (sideload steps, two-login explanation,
  what's not built yet).
- `README.md` — short public-facing project summary.
- `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts` —
  Gradle 8.9, AGP 8.7.0, Kotlin 2.3.20, compileSdk/targetSdk 36, minSdk
  26.

Branches: `main` (active development) and `debug-apk` (carries a built
`build-artifacts/lovelace-debug.apk` for sideloading; periodically merged
forward from `main` and rebuilt — confirmed throwaway/disposable, not a
long-term release channel).

## Design decisions worth knowing

- **Not on the Play Store, by design.** The app bundles IITC and loads
  the real `intel.ingress.com`, which is exactly why the reference
  project (IITC Mobile) has only ever shipped via F-Droid/direct APK,
  never Play — real risk of takedown or account action, not just listing
  rejection. Distribution is sideloaded APK via the `debug-apk` branch.
  Play Internal Testing track was considered as a lighter-weight fallback
  if sideloading becomes real friction, but not judged worth setting up
  for a ~10-person group.
- **Two separate logins are intentional.** Google Sign-In via Firebase
  Auth (app identity, used for chat/viewport-sync) is kept fully separate
  from the player's Ingress/Google session inside the map WebView. The
  app never intercepts or stores Ingress credentials — this separation is
  also what keeps the app clear of anything resembling gameplay
  automation.
- **IITC plugin source is unbuilt, so injection is hand-rolled.** Plugin
  source fetched straight from the IITC-CE GitHub repo is raw
  (`var setup = function(){...}`) — it's missing the
  `wrapper(plugin_info){...}` + self-inject footer that IITC-CE's own
  Python `build.py` normally adds, and this project doesn't run that
  build tool. `PluginInjector.wrapPluginScript()` wraps the raw source in
  its own IIFE and registers the captured `setup` with IITC's real
  `window.bootPlugins`/`window.iitcLoaded` mechanism manually. Draw
  Tools needed the same treatment one level deeper: its `@include_raw:`/
  `@include_css:` template tokens (referencing vendored Leaflet.draw +
  spectrum.js/css files) were manually substituted with real file
  contents once, by hand, rather than run through `build.py`.
- **Plugin toggles apply on map reload, not live.** There's no clean way
  to "uninject" a running plugin from a live WebView page, so toggling a
  checkbox saves to Firestore immediately but the actual script
  injection only happens the next time the map is reloaded (navigating
  to the map route with `popUpTo(...) { inclusive = true }`, which
  destroys and recreates the WebView).
- **Firestore rules, not a custom backend.** One cell, small team — Auth
  + Firestore (Spark/free plan) covers identity, chat, viewport pub/sub,
  and invite gating with no custom server. Firestore security rules are
  managed directly in the Firebase console, not checked into the repo as
  a `firestore.rules` file.
- **Chat is plaintext today; E2E encryption is designed but deferred.**
  The architecture doc specs Signal Protocol with sender-keys for group
  fan-out, FCM as a silent wake-up trigger only (no plaintext in push
  payloads), and an explicit re-key-on-removal flow — none of that is
  implemented yet. Current chat is real-time but unencrypted Firestore
  documents.

## Known issues / open risks

- Invite-code gate has a known accepted gap: `users/{uid}` writes in
  Firestore rules are `auth.uid`-scoped but not cross-checked against a
  real invite redemption at the rules layer. Closing that fully would
  need a Cloud Function; judged not worth it yet at this scale.
  Documented, not fixed.
- A harmless `"plugin does not have proper wrapper"` console warning can
  still appear for plugins that predate the fix in `PluginInjector`
  (only affects the "About IITC" info panel, not functionality) —
  confirmed harmless by reading the vendored core script's own
  `safeSetup()` fallback behavior.
- One pre-existing, unrelated JS console error from Ingress's own
  dashboard script (`Cannot read properties of null (reading 'style')`
  in `gen_dashboard_*.js`) shows in logcat — noise, not investigated,
  not caused by this app.
- `app/google-services.json` is gitignored; anyone (re)cloning needs to
  fetch it from the Firebase console separately.
- Niantic ToS: IITC has operated for years in informal tolerance, not
  official endorsement. This app doesn't add gameplay-automation risk
  beyond what IITC already carries, but enforcement posture could change
  — worth stating plainly to the team, not just noting internally.

## Next steps

Nothing is currently queued — the last completed unit of work was IITC
plugin management (built, live-tested, committed on `main` and merged
into `debug-apk`). Candidates, in the rough order the architecture doc
prioritizes them:
1. End-to-end encrypted chat (Signal Protocol / sender-keys) — the
   biggest gap between "test build" and something safe to rely on for
   real coordination.
2. SOS/rally ping (high-priority FCM wake, on-device decrypt-then-notify).
3. Persistent shared pins, presence indicator, op checklists — smaller,
   independent features from §9 of the architecture doc.
4. QR-code invites (currently just a typed code).

## Useful context

- **GitHub**: `https://github.com/robertclemo/Project_Lovelace` (private).
  `main` is active development; `debug-apk` is the sideload-distribution
  branch.
- **Firebase project**: "Roanoke Resistance Lovelace"
  (`roanoke-resistance-lovelace`), Spark (free) plan. Collections:
  `messages`, `viewport_sync`, `users`, `invites`; access gated by an
  `isApproved()` rule requiring `users/{uid}.approved == true`.
- **Reference project**: IITC Mobile (open source) — the map-injection
  approach and Play Store avoidance both follow its precedent.
- **License note**: `app/src/main/assets/` vendors ISC-licensed code from
  the IITC-CE project (`github.com/IITC-CE/ingress-intel-total-conversion`),
  not authored by this project. See
  `THIRD_PARTY_NOTICES_IITC-CE_LICENSE.txt`.
- **Audience/constraint**: built for one specific ~10-person Ingress
  Resistance cell, not a general-purpose product — design choices (no
  Play Store, Spark-tier Firebase, rules managed by hand in console) lean
  on that small, trusted scale and would need revisiting before growing
  much beyond it.
