# Ingress Resistance Team App — Architecture Doc

## 1. Purpose

An Android app for a local Ingress Resistance cell that:
- Displays the live IITC-enhanced intel map inside the app
- Provides real-time end-to-end encrypted team chat
- Lets one teammate "lead" the map view, with everyone else's viewport following along live

This is a companion tool. It never touches Ingress gameplay actions, automation, or the game client itself — it only displays the same intel map a browser would show, plus a chat layer bolted on the side. That separation is what keeps it clear of anything resembling gameplay automation.

## 2. High-Level Components

```
┌─────────────────────────────────────────────┐
│                Android App                   │
│                                                │
│  ┌──────────────┐   ┌──────────────────────┐ │
│  │  Map Module   │   │   Chat Module         │ │
│  │  (WebView +   │   │  (E2E encrypted,      │ │
│  │   IITC inject)│   │   Signal protocol)    │ │
│  └──────┬───────┘   └──────────┬───────────┘ │
│         │                      │              │
│  ┌──────▼──────────────────────▼───────────┐ │
│  │        Viewport Sync Module              │ │
│  │  (leader broadcasts lat/lng/zoom)        │ │
│  └──────┬───────────────────────────────────┘ │
│         │                                      │
│  ┌──────▼───────────────────────────────────┐ │
│  │        Auth Module (Google Sign-In)       │ │
│  └────────────────────────────────────────────┘ │
└──────────────────┬─────────────────────────────┘
                    │
        ┌───────────▼────────────┐
        │   Backend (thin relay)  │
        │  - Firebase Auth        │
        │  - Firestore/RTDB       │
        │    (encrypted blobs +   │
        │     viewport coords)    │
        │  - Signal key server    │
        └──────────────────────────┘
```

## 3. Map Module

- **Approach**: WebView loading `intel.ingress.com`, with the IITC userscript injected on page load — same pattern as the existing open-source IITC Mobile project. Worth forking/referencing that codebase rather than rebuilding the injection layer from scratch.
- The player still logs into Ingress/Google normally inside that WebView, exactly as they would in a desktop browser. The app doesn't intercept or store those credentials.
- IITC plugins (portal highlighting, etc.) run as normal; no changes needed there.

## 4. Auth Module

- **Google Sign-In via Firebase Auth**, kept entirely separate from the WebView's own Ingress/Google session.
- On first login, the app creates an app-scoped identity (a UID + display name the person picks), stored in your backend. This identity is what chat and viewport-sync use — it has no link to the person's Niantic account, agent name, or level.
- Since this is Resistance-only and small (one cell), a simple invite-code or admin-approval gate before an account can join the shared chat is worth including so a stray Google sign-in from an outsider can't join.

- **QR code invites**: the invite-code gate (§4) can double as a QR code — generate a code that encodes a short-lived invite token, display it as a QR in-app, and new members scan it with their phone camera to auto-fill the invite code and jump straight to Google Sign-In. Faster than typing a code over voice or text at a meetup, and the token can expire after one use or a few hours to limit exposure if the QR gets shared wider than intended.

## 5. Chat Module (End-to-End Encryption)

- **Protocol**: Signal Protocol (via `libsignal`), the same double-ratchet scheme Signal/WhatsApp use — well-audited, and there are maintained Android bindings.
- **Group chat model**: Signal's protocol is natively 1:1; for a shared team channel, use **sender keys** (the same mechanism Signal Groups use) — each member generates a sender key, distributes it E2E to other members, and messages are encrypted once per sender and fanned out. The backend only ever stores/relays ciphertext.
- **Key management**: keys generated and stored on-device (Android Keystore); the backend stores only public prekeys, never private keys.
- **Emoji**: just Unicode text over the same encrypted channel — no special handling needed.

### 5a. Key Rotation on Member Removal

Sender keys don't expire on their own — a removed member can still decrypt any message encrypted with a sender key they already received, until every other member re-keys. This needs to be an explicit flow, not an afterthought:

- Removing a member (admin action) immediately drops them from the group membership list in the backend, so they can no longer authenticate to fetch new ciphertext or receive future key distributions.
- The removal also triggers a **rotate-now** event pushed to all remaining members: each generates a fresh sender key and redistributes it E2E to the current member list only. Until rotation completes, treat the channel as still readable by the removed member.
- Same trigger should fire if a member reports a lost/stolen phone — treat it as a removal, not just a courtesy notice, since the on-device key material (Android Keystore) is what's actually at risk.
- Rotation should be near-instant for a one-cell-sized group (a handful of members), so this doesn't need to be optimized for scale — just needs to actually happen automatically rather than relying on someone remembering to do it.

### 5b. Push Notification Delivery

Chat messages and the SOS/rally ping (§9) both need to reach a backgrounded or locked phone, which in practice means FCM (Firebase Cloud Messaging) — but FCM payloads pass through Google's infrastructure, which is in direct tension with end-to-end encryption.

- **Pushes carry no plaintext.** Use FCM only as a silent/data-message wake-up trigger (no visible notification body/title in the payload itself) — it tells the app "new ciphertext is waiting," nothing more.
- On receipt, the app pulls the actual encrypted blob from Firestore, decrypts it locally, and *then* constructs the visible Android notification (message preview, SOS banner, etc.) entirely on-device.
- This adds a small amount of latency (push → fetch → decrypt → display) but keeps message content, sender identity, and SOS location data out of Google's push infrastructure entirely — only "someone sent something" metadata is visible to FCM, which is unavoidable with any push provider.
- The SOS/rally ping (§9) should set a high-priority FCM flag so it wakes the device promptly even under Android's background restrictions/Doze mode; routine chat can use normal priority to conserve battery.

## 6. Viewport Sync (Leader-Follow)

- One user designates themselves (or is designated) **leader** for a session.
- The app publishes the leader's `{lat, lng, zoom}` on a lightweight channel (Firestore listener or a small WebSocket relay) at a throttled rate (e.g. every 300–500ms, or on-change).
- Followers' WebViews receive updates and pan/zoom the IITC map to match, via JS injected into the WebView (`window.map.setView(...)` equivalent).
- This channel is metadata only (coordinates), not gameplay data — it doesn't need E2E encryption at the same strength as chat, but should still run over TLS and be scoped to authenticated group members only.
- Leader status should be easy to hand off (tap "become leader") since whoever's actively scouting will change during an op.

## 7. Backend

Given it's one cell to start, a minimal backend keeps cost and maintenance low:
- **Firebase** (Auth + Firestore or Realtime Database) covers identity, encrypted chat blob storage/relay, viewport pub/sub, and group membership — all with generous free tiers at this scale.
- No custom server needed initially; if the team grows or you want the key server independent of Google's infrastructure later, that's a clean second-phase migration.

## 8. Risk Notes

- **Niantic ToS**: IITC itself has operated for years in an informal tolerance zone, not an official Niantic endorsement. This app doesn't add gameplay automation risk beyond what IITC already carries, but no one can guarantee Niantic's enforcement posture won't change — that's worth stating plainly to your team, not just to you.
- **App-store policy**: Google Play has rules about apps that interact with third-party game clients; worth a quick read of Play's developer policy on "impersonation" and "deceptive behavior" sections before listing this publicly (a private/internal distribution via APK sideload or Play internal testing track sidesteps this entirely for a small cell).

## 9. Small-Team Tactical Features

Being outnumbered means the edge has to come from coordination speed and information density, not headcount. These are additions worth considering on top of the core build:

- **SOS / rally ping**: one-tap broadcast that pushes a high-priority notification to the whole team with the sender's current map location pinned — for "I need backup now" or "portal under attack" moments, cutting through normal chat noise.
- **Persistent shared pins**: named markers (not just leader-follow viewport) for things like "farm route," "safe hack spot," "key trade point," or "watch this portal" — visible to everyone, editable by anyone, so institutional knowledge doesn't live in one person's head.
- **Presence / "who's active"**: a simple online/nearby indicator so people know who's currently out playing and roughly where, without needing to ask in chat — useful for spontaneous team-ups.
- **Op checklists**: a lightweight shared checklist feature for planned ops (anomalies, multi-portal fields) — check off steps in real time so a small team doesn't duplicate work or miss a step under time pressure.
- **Priority notification tiers**: mute routine chat by default but let SOS/rally pings always break through — important for a small team where everyone can't watch the app constantly.
- **Key/resource tracking**: a shared (non-gameplay) inventory note of who's holding keys to which important portals, so a small roster doesn't lose track of scarce resources.
- **Low-footprint / battery-friendly mode**: since a small team can't afford members burning out or missing pings due to dead phones, a low-power background mode for the viewport-sync and notification listener matters more here than it would for a huge, redundant team.
- **After-action notes**: quick post-op text log (who ran it, what happened, what to change next time) — cheap to build, valuable for a small team that can't afford to relearn the same lessons.

None of these touch gameplay automation — they're coordination and information-sharing tools, same category as the chat and viewport sync already in the plan.

## 10. Build Phases

1. **Core shell**: WebView + IITC injection + Google Sign-In (app identity only)
2. **Chat**: Signal Protocol integration, sender-key group chat, basic UI
3. **Viewport sync**: leader/follower coordinate broadcast + WebView JS bridge
4. **Polish**: invite-code gating, leader handoff UX, notifications
