# Lovelace — Handoff Notes (2026-07-30 night session)

## TL;DR

Re-verified auth, map, and chat still work live (unchanged from last
session). Built and verified **viewport sync (leader-follow)** and
**app-scoped display names** end-to-end, including several real bugs
caught only by live testing on the emulator, not code review. Firestore
rules had to be fixed/extended live in the Firebase console twice tonight.

## What's working right now (verified)

- **Auth**, **Map**, **Chat** — same as last session, re-confirmed live on
  the emulator at the top of tonight's session (Google Sign-In session
  persisted, real IITC map rendered, chat sent/received a fresh message
  through Firestore).
- **Viewport sync (leader-follow)**, §6 of the architecture doc:
  - `ViewportSyncManager` (`viewportsync/ViewportSyncManager.kt`):
    Firestore-backed pub/sub on a single `viewport_sync/current` doc —
    `becomeLeader`/`stopLeading`/`publishViewport` plus
    `observeLeaderState()` as a Flow.
  - `MapScreen`: a JS bridge hooks Leaflet's `moveend` event and reports
    `{lat, lng, zoom}` to Kotlin. The leader publishes to Firestore on
    every pan/zoom settle; followers get incoming updates applied via
    `window.map.setView(...)`.
  - The leader control is a small crown-emoji button, sized to match
    IITC's own layer-selector icon, sitting just below it (top-right).
    White when idle, blue when you're leading. Tap to claim leadership or
    release it.
  - Verified for real: claimed leadership on-device, panned the map,
    confirmed the write landed in Firestore, then edited that doc
    directly in the Firebase console to a fake leader + Tokyo coordinates
    at zoom 15 and watched the app's map jump there — confirms the
    follower path (`setView`) works, not just the publish path.
- **App-scoped display names**, §4 of the architecture doc:
  - `UserProfileRepository` (`profile/UserProfileRepository.kt`):
    get/set a `displayName` on `users/{uid}` in Firestore.
  - `EntryGateScreen`: new first-hit route after `LovelaceApp`'s NavHost
    starts — checks auth state, then (if signed in) whether a profile
    doc exists yet, and routes to sign-in / name-picker / map
    accordingly. Runs again right after sign-in too.
  - `ChooseDisplayNameScreen`: first-run prompt, pre-filled with the
    Google account name as a *suggestion only* — the point is this name
    is decoupled from the real identity, so it's freely editable.
  - Chat and viewport-sync's leader claim both now resolve and use this
    stored name instead of `currentUser.displayName`/`email`.
  - Verified for real: fresh account with no `users/{uid}` doc got
    prompted for a name on launch; picked something unrelated to the
    Google name ("Agent_Falcon"); confirmed it showed up as the sender
    on a new chat message *and* as `leaderName` on a claimed
    `viewport_sync/current` doc.

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
2. Real device retest of chat, viewport sync, and display names (all
   only tested on emulator so far).
3. Invite-code / admin-approval gate for new sign-ups (§4 of the
   architecture doc mentions this alongside display names, but it's a
   separate feature — not built yet, so right now any Google account can
   sign in and pick a name).

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
- The `debug-apk` branch (local + `origin`) is still sitting around,
  confirmed throwaway — safe to delete whenever, just hasn't been asked
  for yet.

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
    - `users`: authenticated read/write, but only your own doc
      (`request.auth.uid == uid`) — this is where app-scoped display
      names live.
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
  Its app-scoped display name is currently "Agent_Falcon" (picked during
  tonight's testing).
- **Architecture doc**: `ingress-team-app-architecture.md` at repo root.

## How to pick back up tomorrow

Just tell me what from "Next up" you want to tackle, or ask me to
summarize/re-verify current state first. Everything above is committed
and pushed to `main` — a fresh session can rebuild from a clean clone
(modulo `google-services.json`, see above).
