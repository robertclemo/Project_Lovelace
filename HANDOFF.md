# Lovelace — Handoff Notes (2026-07-30 night session, continued)

## IITC Plugin Management — done, committed

All three plugins (Player Activity Tracker, Draw Tools, Bookmarks) are
built, live-tested error-free on the emulator, and committed. See
"Previously completed" for the recap; details of how Draw Tools got
fixed are kept below for reference.

## IITC Plugin Management — background

Goal (user's ask): let users check/uncheck IITC plugins from a list (like
IITC Mobile's own plugins tab) and reload the map to apply them, synced
per-user via Firestore. Three plugins requested: Player Activity Tracker
(agent tracker), Draw Tools, Bookmarks.

### What's built

- `plugins/PluginCatalog.kt` — static list of 3 `IitcPlugin`s (id,
  display name, description, asset paths).
- `plugins/PluginInjector.kt` — `wrapPluginScript()`/`wrapPluginStyle()`.
  **Important context**: plugin source fetched straight from the
  IITC-CE GitHub repo is *unbuilt* — it's just `var setup = function()
  {...}`, missing the `wrapper(plugin_info){...}` + self-inject footer
  that IITC-CE's own Python `build.py` normally adds. We don't run that
  build tool, so `wrapPluginScript` wraps the raw source in our own IIFE
  and registers the captured `setup` with IITC's real
  `window.bootPlugins`/`window.iitcLoaded` mechanism ourselves — same
  idea as `patchIitcScript` already does for core, just a different
  shape since plugins aren't pre-built like core is.
- `plugins/PluginPreferencesRepository.kt` — `enabledPlugins: string[]`
  field on the existing `users/{uid}` doc. No new Firestore rule needed
  — that doc's already owner-read/write-only.
- `plugins/PluginsScreen.kt` — checkbox list + "Reload map" button.
  Toggling a checkbox saves to Firestore immediately; actual
  injection only happens on reload (there's no clean way to "uninject"
  a running plugin from a live WebView page).
- `LovelaceApp.kt` — new `PLUGINS` route. "Reload map" navigates to
  `Routes.MAP` with `popUpTo(Routes.MAP){inclusive=true}`, which
  destroys and recreates the WebView (via `onRelease{it.destroy()}` +
  a fresh `AndroidView` factory call) — that's the actual "reload".
- `MapScreen.kt` — new 🧩 puzzle-piece button stacked below the crown
  (top-right, same square-icon treatment). Fetches `enabledPluginIds`
  via `LaunchedEffect`, injects each enabled plugin's script (+ optional
  CSS) in `onPageFinished`, right after the core script and viewport
  hook.
- Assets vendored at `app/src/main/assets/plugins/`:
  `player-activity-tracker.js` + `player-tracker.css`, `draw-tools.js`,
  `bookmarks.js` + `bookmarks.css` — all raw/unbuilt source pulled
  directly from
  `github.com/IITC-CE/ingress-intel-total-conversion/tree/master/plugins`
  (ISC-licensed, same project as the vendored core script).

  Note: "Player Tracker" was renamed upstream to "Player activity
  tracker" (`player-activity-tracker.js`) — the old `player-tracker.css`
  filename survives as its styling companion, that's not a mismatch.

### Live-tested tonight, on the emulator

- Plugin list renders correctly, checkboxes toggle and persist to
  Firestore (`enabledPlugins: ["player-activity-tracker","draw-tools"]`
  confirmed in the console).
- **Player Activity Tracker**: boots clean, no errors.
- **Draw Tools**: **broken**. Fails with `leaflet.draw-src.js loading
  failed` / `TypeError: Cannot read properties of undefined (reading
  'Polyline')`. Root cause: `draw-tools.js`'s `loadExternals()` function
  is *also* unbuilt source — it contains literal, never-substituted
  `'@include_raw:external/leaflet.draw-src.js@';` and
  `'@include_css:external/...@'` template tokens (same
  `build.py`-template-substitution issue as the plugins themselves, one
  level deeper: draw-tools depends on IITC-CE's vendored copy of the
  external Leaflet.draw library, not just IITC core).
- **Bookmarks**: not yet tested.
- A `"plugin does not have proper wrapper"` console warning appears for
  plugins where our injection harness doesn't set a `.info` property on
  the captured `setup` function (real `build.py` output sets
  `setup.info = plugin_info`). **Confirmed harmless** — read the
  vendored core script's own `safeSetup()` (search
  `iitc_total_conversion.user.js` for `plugin does not have proper
  wrapper`, ~line 4285): it logs the warning, falls back to
  `info = {}`, and *still calls `setup.call(this)` regardless*. Only
  affects the "About IITC" plugin-info panel, not functionality. Worth
  fixing for cleanliness (have the harness set `.info`) but not
  blocking anything.
- Also spotted, unrelated to tonight's work: the vendored
  `iitc_total_conversion.user.js` core itself appears to already bundle
  some older default plugin(s) — one throws the same "does not have
  proper wrapper" warning referencing `window.plugin.playerTracker` /
  unsubstituted `@include_img:...@` tokens, structurally different from
  our new `player-activity-tracker.js`. Pre-existing, silent (same
  harmless-per-safeSetup reasoning), was presumably happening every
  session before tonight too — nobody had looked at `MapScreenJS`
  console output closely enough to notice until now. Not investigated
  further; not in scope.

### How Draw Tools got fixed

Found one more gap than the previous session had noted: `loadExternals()`
also needs `external/spectrum.js` + `external/spectrum.css` (the color
picker used by the Draw Tools Options dialog) — not just the 6
leaflet.draw files. Confirmed via GitHub API listing of
`plugins/external/` that both exist upstream and fetched them too, so
8 files total got spliced in.

Wrote a one-off Python script (not persisted — was scratch, splice is
already applied to the committed `draw-tools.js`) that did what
`build.py` does by hand: `@include_raw:...@` tokens got replaced with
the literal, unquoted file contents (so they execute as real code
instead of dead string-literal statements); `@include_css:...@` /
`@include_string:...@` tokens got replaced with a JSON-escaped version
of the CSS, staying inside the surrounding JS string. Verified
afterward that zero `@include_*@` tokens remain in the file.

Also fixed the cosmetic `"plugin does not have proper wrapper"` console
warning while in there — `PluginInjector.kt`'s boot harness now sets
`pluginSetup.info = {}` before registering it with IITC, matching what
real `safeSetup()` falls back to anyway, just without the warning.

**Live-tested on the emulator, cold rebuild + reinstall:** all three
plugins (Player Activity Tracker, Draw Tools, Bookmarks) enabled
together, "Reload map" tapped, full logcat scan for `MapScreenJS`
showed zero errors/warnings from any plugin — no `leaflet.draw-src.js
loading failed`, no `Cannot read properties of undefined (reading
'Polyline')`, no `proper wrapper` warning. Bookmarks confirmed via the
layers panel showing a new "Bookmarked Portals" overlay entry. The two
`Uncaught SyntaxError` / `Cannot read properties of null (reading
'style')` errors that still show in logcat are the pre-existing,
already-documented Ingress-site noise (see "Known non-blocking
issues"), unrelated to plugins.

Feature is done: built, live-tested, committed.

## Previously completed (committed + pushed to `main`)

- **Re-verified** auth, map, chat still work live (this was step 1 of
  tonight's session, long before the plugin work above).
- **Viewport sync (leader-follow)** — `viewportsync/` package, crown
  button on map, Firestore `viewport_sync/current` doc. Verified
  leader-publish and follower-apply paths both work for real (including
  a live cross-client test via editing Firestore directly in console).
- **App-scoped display names** — `profile/UserProfileRepository.kt`,
  decoupled from Google identity, used by chat + viewport-sync leader
  claims.
- **Invite-code admin-approval gate** — `profile/InviteRepository.kt` +
  `JoinScreen.kt` (replaced the old separate display-name screen —
  invite code and display name are now collected together on first
  run). Firestore rules now gate `messages`/`viewport_sync` read/write
  behind a new `isApproved()` function requiring
  `users/{uid}.approved == true`. Live invite code `LOVELACE7` exists in
  Firestore (`invites/LOVELACE7`), maxUses 10, usedCount 1 so far.
  Known accepted gap: `users/{uid}` writes are only
  `auth.uid`-scoped, not cross-checked against a real invite redemption
  at the rules layer — closing that fully needs a Cloud Function, which
  is more than this app needs yet. Documented, not fixed.
- **Debug APK for sideloading** — `debug-apk` branch,
  `build-artifacts/lovelace-debug.apk`, updated tonight with everything
  through the invite-code gate (predates the plugin work above, so it's
  one step behind currently).
- Three real bugs found via live device/emulator testing (not code
  review) during viewport sync work — Firestore rules nesting mistake,
  `window.map` readiness check, a recursive self-call in
  `ViewportBridge` — full detail was in the previous version of this
  file; see git history (`761aa0a`) if needed, trimmed here for length.

## Known non-blocking issues

- One leftover JS error in *Ingress's own* dashboard script
  (`Cannot read properties of null (reading 'style')` in
  `gen_dashboard_*.js`) — noise, not investigated.
- `google-services.json` is gitignored — redownload from Firebase
  console if recloning fresh.
- `debug-apk` branch is confirmed throwaway, safe to delete whenever.
- **Do NOT publish to the Play Store** — discussed with the user
  tonight and talked out of it. This app bundles IITC and loads the
  real `intel.ingress.com`; that's exactly why the reference project
  (IITC Mobile) has never been on Play, only F-Droid/direct APK. Real
  risk of takedown/account action, not just rejection. Sideloading
  (`debug-apk` branch) is the right distribution channel for a ~10
  person friend group. Internal Testing track is a lighter-weight
  option if sideloading becomes real friction later, but wasn't judged
  worth setting up yet.

## Environment note from tonight

Android Studio got updated mid-session. Its bundled `jbr` (used for
`gradlew` from the command line) ended up broken (`could not open
...jbr\lib\jvm.cfg`) — **but** Android Studio downloaded its own working
JDK 21 to `C:\Users\rober\.jdks\jbr-21.0.11` (found via
`.idea/gradle.xml` → `gradleJvm` and the current profile's
`jdk.table.xml` under
`%APPDATA%\Google\AndroidStudio2026.1.3\options\`). **Use that path for
`JAVA_HOME`** when running `gradlew` from the command line, not the
Android-Studio-bundled one:
```
export JAVA_HOME="C:\Users\rober\.jdks\jbr-21.0.11"
```
Gradle 8.9 (pinned in this project) only supports JDK 8–22 anyway, so
the newer bundled JBR 25 wouldn't have worked even if it weren't broken.

## Repo & environment reference

- **GitHub**: `https://github.com/robertclemo/Project_Lovelace` (private)
  - `main` — everything in "Previously completed" above, pushed.
  - `debug-apk` — sideload APK branch, one step behind main currently.
- **Firebase project**: "Roanoke Resistance Lovelace"
  (`roanoke-resistance-lovelace`), Spark (free) plan. Firestore rules
  managed directly in console (no `firestore.rules` file in repo) —
  current rules: `isApproved()` helper, `messages`, `viewport_sync`,
  `users`, `invites` collections. See git history of this file for the
  exact rule text if you need to reconstruct it.
- **Package name**: `com.roanokeresistance.lovelace`
- **Local paths**:
  - Project root: `C:\Users\rober\Documents\Ingress`
  - Android SDK: `C:\Users\rober\AppData\Local\Android\Sdk`
  - JDK for `gradlew`: `C:\Users\rober\.jdks\jbr-21.0.11` (see
    environment note above — NOT the Android Studio bundled `jbr`)
- **Emulator**: AVD `Medium_Phone_API_36.1`. Leave `hw.gpu.mode=auto`.
  Test account `bertramhiresmith@gmail.com`, display name
  "Agent_Falcon", approved (redeemed `LOVELACE7`).
- **Architecture doc**: `ingress-team-app-architecture.md` at repo root.

## How to pick back up

IITC plugin management is done and committed. Nothing queued — ask the
user what's next.
