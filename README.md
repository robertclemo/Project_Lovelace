# Lovelace

An Android companion app for a local Ingress Resistance cell. It bundles the
IITC-enhanced intel map and a private team chat into one app, with a synced
"leader-follow" map view for coordinating during ops.

> Private test build for a ~10-person friend group. Not on the Play Store —
> see [Distribution](#distribution) below.

## What it does today

- **Intel map** — WebView loading `intel.ingress.com` with the IITC-CE
  userscript injected, same approach as IITC Mobile. Players sign into
  Ingress/Google normally inside the WebView; the app never touches that
  session.
- **IITC plugin management** — enable/disable IITC plugins (Player Activity
  Tracker, Draw Tools, Bookmarks) from an in-app list, synced per-user via
  Firestore.
- **Team chat** — real-time group chat over Firestore. **Not end-to-end
  encrypted yet** — treat it like a group text, not a secure channel.
- **Viewport sync (leader-follow)** — one teammate leads; everyone else's
  map view pans/zooms to follow along live.
- **App-scoped identity** — display names and chat identity are decoupled
  from each player's Google/Niantic account.
- **Invite-code gate** — new sign-ups need an invite code and admin approval
  before they can use chat or viewport sync.

See [`ingress-team-app-architecture.md`](ingress-team-app-architecture.md)
for the full design, including planned work (Signal Protocol E2E encryption
for chat, SOS/rally pings, shared pins, presence) that isn't built yet.

## Tech stack

- Kotlin + Jetpack Compose (Material 3), min SDK 26 / target SDK 36
- Firebase Auth (Google Sign-In) + Firestore
- Android `WebView` with injected IITC-CE core script and plugins
  (vendored under `app/src/main/assets/`)
- Gradle 8.9, AGP 8.7.0, Kotlin 2.3.20

## Getting started

1. Clone the repo and open it in Android Studio.
2. Add your own `app/google-services.json` (Firebase project config) —
   it's gitignored and not included in the repo. Ask Robert for access to
   the Firebase project, or point it at your own.
3. Build/run the `app` module on an emulator or device (min SDK 26).

From the command line:

```
./gradlew assembleDebug
```

Requires JDK 17–22 (Gradle 8.9's supported range). If `gradlew` can't find
a JDK, point `JAVA_HOME` at one explicitly.

## Project layout

- `app/src/main/java/com/roanokeresistance/lovelace/` — app source
  (`auth/`, `map/`, `chat/`, `plugins/`, `profile/`, `viewportsync/`)
- `app/src/main/assets/` — vendored IITC-CE core script and plugins
  (ISC-licensed; see
  [`THIRD_PARTY_NOTICES_IITC-CE_LICENSE.txt`](THIRD_PARTY_NOTICES_IITC-CE_LICENSE.txt))
- `ingress-team-app-architecture.md` — full architecture doc and roadmap
- `TEAM-GUIDE.md` — plain-language guide for non-technical teammates
- `HANDOFF.md` — running dev session notes/log

## Branches

- `main` — active development
- `debug-apk` — sideload distribution branch; carries a built
  `build-artifacts/lovelace-debug.apk` for teammates to install directly

## Distribution

This app is **not published to the Play Store on purpose**. It bundles
IITC and loads the real `intel.ingress.com`, which is exactly why the
reference project (IITC Mobile) has only ever shipped via F-Droid/direct
APK, never Play. Distribution to the team is via sideloaded APK
(`debug-apk` branch).

## Status

Early, actively-developed test build. Core shell (map + auth), chat, and
viewport sync all work; encryption, SOS/rally pings, and other planned
coordination features are not built yet.
