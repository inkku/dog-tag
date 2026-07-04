# Dog Tag

An app for tracking multiple dogs wearing BLE tracker tags (OTAG, Google Find Hub /
Find My Device network compatible), with optional Tractive GPS collar fusion, live
map view, and virtual-fence alerts.

## Why two components

Two of the requested integrations don't expose an official third-party API:

- **Google Find Hub / Find My Device network (FMDN)**: OTAG tags register into this
  network. Google only publishes an SDK for *accessory manufacturers* to broadcast
  into the network — there is no public API for a separate app to query an already
  paired device. [`GoogleFindMyTools`](https://github.com/leonboe1/GoogleFindMyTools)
  is a reverse-engineered client that authenticates with *your own* Google account
  (the account the tags are already paired to) and can list devices, decrypt their
  crowd-sourced location, and issue a play-sound command.
- **Tractive** has no official public API. Integration relies on an unofficial
  client such as [`aiotractive`](https://github.com/Tractive/aiotractive) (or
  similar reverse-engineered projects) authenticating with your Tractive account.

Both require account credentials and, for FMDN, a one-time browser-based Google
login that produces a reusable auth token file. That doesn't fit inside an Android
app's sandbox, and it also gives you one place to run/rotate long-lived
credentials instead of storing them on a phone. So:

- **`backend/`** — a small self-hosted Python service (run it on a home server,
  Raspberry Pi, NAS, or your own PC). Holds the Google/Tractive credentials, polls
  FMDN + Tractive positions, stores dogs/tags/fences, evaluates geofences for the
  "network location" tier, and dispatches owner notifications.
- **`android/`** — the phone app. Does its own direct BLE scanning of the 4 tags
  for near-field RSSI-based proximity (this is the only way to get resolution
  tight enough for virtual fencing — FMDN/GPS positions are crowd-sourced and can
  be off by tens of meters), draws the map, lets you draw fences, and evaluates
  BLE-proximity fence breaches locally and immediately (no dependency on the
  backend or network for the fast path). It talks to the backend for FMDN/Tractive
  positions when a dog is out of direct BLE range, and to request a tag "ring"
  over the FMDN network as a fallback when direct BLE ring isn't possible.

## Status / limitations

This is a first working scaffold, not a finished product:

- The backend runs and is tested in this environment (Python 3.11). See
  `backend/README.md` for setup, including the one-time FMDN login.
- The Android app is written but **not compiled here** — this sandbox has no
  Android SDK. Open `android/` in Android Studio to build; expect to fix minor
  SDK/dependency-version issues on first sync.
- Direct-BLE "ring the tag" (lowest latency, works without the FMDN network) needs
  OTAG's own GATT protocol, which isn't publicly documented. The app currently
  only supports ringing via the backend's FMDN play-sound call. If you can sniff
  OTAG's companion app's BLE traffic (see `android/app/src/main/java/com/dogtag/ble`),
  a direct GATT write can be added later.
- RSSI-based BLE ranging is noisy and needs on-site calibration (walls, terrain,
  tag orientation all matter) — see the in-app calibration screen described in
  `android/README.md`.

## Layout

```
backend/    FastAPI service: dogs, tags, fences, FMDN/Tractive polling, alerts
android/    Kotlin/Compose app: BLE scan, map, fence editor, notifications
```

See `backend/README.md` and `android/README.md` for component-specific docs.
