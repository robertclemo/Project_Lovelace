# Lovelace — Handoff Notes (2026-07-30 night session)

## TL;DR

Re-verified auth, map, and chat still work live (unchanged from last
session). Built and verified **viewport sync (leader-follow)** end-to-end,
including two real bugs caught only by live testing on the emulator, not
code review. Firestore rules for the new collection had to be fixed live
in the Firebase console (nesting mistake — see below).

## What's working right now (verified)

- **Auth**, **Map**, **Chat** — same as last session, re-confirmed live on
  the emulator at the top of tonight's session (Google Sign-In session
  persisted, real IITC map rendered, chat sent/received a fresh message
  through Firestore).
- **Viewport sync (leader-follow)**, §6 of the architecture doc — new
  tonight:
  - `ViewportSyncManager` (`viewportsync/ViewportSyncManager.kt`):
    Firestore-backed pub/sub on a single `viewport_sync/current` doc —
    `becomeLeader`/`stopLeading`/`publishViewport` plus
    `observeLeaderState()` as a Flow.
  - `MapScreen`: a JS bridge hooks Leaflet's `moveend` event and reports
    `{lat, lng, zoom}` to Kotlin. The leader publishes to Firestore on
    every pan/zoom settle; followers get incoming updates applied via
    `window.map.setView(...)`.
  - A "Become Leader" / "Leading — tap to stop" / "Following {name} — tap
    to take over" control sits top-right of the map, below IITC's own
    layer-selector icon (had to move it there — the first placement
    covered IITC's toolbar tabs).
  - Verified for real: claimed leadership on-device, panned the map,
    confirmed the write landed in Firestore (`lat`/`lng`/`zoom` fields on
    `viewport_sync/current`), then edited that doc directly in the
    Firebase console to a fake leader + Tokyo coordinates at zoom 15 and
    watched the app's map jump there — confirms the follower path
    (`setView`) works, not just the publish path.

## Bugs found tonight (all via live testing, not code review)

1. **Firestore rules misplaced.** When adding the rule for the new
   `viewport_sync` collection, it got nested *inside*
   `match /messages/{messageId} { ... }` instead of as a sibling —
   so it only matched `messages/{id}/viewport_sync/{docId}`, a
   subcollection nobody writes to. The real path
   (`viewport_sync/current`) had no matching rule and fell through to
   the implicit default-deny — both reads and writes got
   `PERMISSION_DENIED`. Fixed by moving the block to sit alongside
   `match /messages/{messageId}`, both inside
   `match /databases/{database}/documents`.
2. **`window.map` readiness check was wrong.** `window.map` exists
   early as a placeholder from the *page's own* Google Maps API load,
   well before IITC's async bootstrap replaces it with the real Leaflet
   instance. The original hook checked `if (!window.map)` and attached
   to the wrong object immediately, then threw
   (`window.map.on is not a function`) as soon as a moveend tried to
   fire. Fixed by polling for `typeof window.map.on === 'function'`
   instead of just truthiness.
3. **Recursive self-call in `ViewportBridge`.** The
   `@JavascriptInterface fun onViewportChanged(...)` method and its
   constructor's lambda parameter had the *identical name*
   (`onViewportChanged`). The call inside the method body resolved to
   itself rather than invoking the lambda — infinite recursion, stack
   overflow, silently swallowed by the WebView JS bridge as "Java
   exception was raised during method invocation" with no other detail.
   Fixed by renaming the lambda parameter to `report`.

Bug 3 in particular was invisible without JS-side logging — WebView
doesn't surface `console.log`/errors from injected scripts anywhere by
default. Added a permanent `onConsoleMessage` override on the
`WebChromeClient` in `MapScreen.kt`, tagged `MapScreenJS` in logcat. Kept
it in (not stripped back out) since it's what found two of the three
bugs above and will keep being useful for IITC/JS debugging.

## Next up (pick one)

1. **Signal Protocol E2E encryption for chat** — the deliberate follow-up
   to chat being plaintext. Architecture doc §5 covers sender-keys,
   on-device key storage, and §5a (key rotation on member removal).
2. **App-scoped display name** — right now chat/auth/viewport-sync all
   just use whatever name Google's account gives us. The architecture doc
   (§4) calls for a separate app-chosen display name, decoupled from the
   real identity.
3. Real device retest of chat and viewport sync (both only tested on
   emulator so far).

## Known non-blocking issues

- One leftover JS error in *Ingress's own* dashboard script
  (`Cannot read properties of null (reading 'style')` in
  `gen_dashboard_*.js`) — doesn't break anything, just noise in the
  console. Not investigated further.
- IITC plugins aren't bundled — core script only.
- `google-services.json` is gitignored (as it should be) — if you ever
  reclone this repo fresh, redownload it from Firebase console → Project
  settings → your Android app, and rerun the SHA-1 fingerprint step if
  Google Sign-In needs it again.

## Repo & environment reference

- **GitHub**: `https://github.com/robertclemo/Project_Lovelace` (private)
  - `main` — everything above, all pushed.
  - `debug-apk` — throwaway branch holding a sideloadable debug APK
    (`build-artifacts/lovelace-debug.apk`) for phone testing without a USB
    cable. Safe to delete once you don't need it anymore.
- **Firebase project**: "Roanoke Resistance Lovelace"
  (`roanoke-resistance-lovelace`), Spark (free) plan.
  - Auth: Google provider enabled, debug SHA-1 registered.
  - Firestore: Standard edition, `nam5` region. Rules (managed directly in
    the Firebase console — no `firestore.rules` file in this repo):
    - `messages`: authenticated reads, writes must set `senderUid` to your
      own uid, no update/delete.
    - `viewport_sync`: authenticated read/write, no per-field restriction
      (leadership hand-off means anyone can overwrite the current-leader
      doc — see §6 of the architecture doc for why this is fine at this
      trust level).
- **Package name**: `com.roanokeresistance.lovelace`
- **Local paths**:
  - Project root: `C:\Users\rober\Documents\Ingress`
  - Android SDK: `C:\Users\rober\AppData\Local\Android\Sdk`
  - JDK (for `gradlew` from command line): Android Studio's bundled JBR at
    `C:\Program Files\Android\Android Studio\jbr`
- **Emulator**: AVD `Medium_Phone_API_36.1` (data dir is actually named
  `Medium_Phone.avd`). **Leave `hw.gpu.mode=auto`** — switching it to
  `host` froze the entire emulator SystemUI on this machine.
  Test Google account already added to it: `bertramhiresmith@gmail.com`.
- **Architecture doc**: `ingress-team-app-architecture.md` at repo root.

## How to pick back up tomorrow

Just tell me what from "Next up" you want to tackle, or ask me to
summarize/re-verify current state first. Everything above is committed
and pushed to `main` — a fresh session can rebuild from a clean clone
(modulo `google-services.json`, see above).
