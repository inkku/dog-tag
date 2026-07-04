# Dog Tag Android app

Kotlin + Jetpack Compose app: BLE scanning of the 4 tags, a live map, virtual
fence editor, and local (phone-side) fence breach detection so the fast path
doesn't depend on the backend or network.

**Not compiled in this repo's dev sandbox** (no Android SDK there) — open this
directory in Android Studio to build. Expect to bump a dependency version or
two on first sync; the versions pinned in `app/build.gradle.kts` were current
as of mid-2026 but Android Studio will flag anything it wants newer.

## Screens

- **Map** — polls the backend's `/locations/latest` + `/dogs` + `/tags` and
  plots the newest known position per dog (osmdroid, no Google Maps API key
  needed).
- **Dogs** — create dogs, create FMDN/Tractive tags (enter their id from the
  backend/Tractive app), assign any unassigned tag to a dog.
- **Fences** — create a circular geofence (name, which dog(s) it applies to,
  center, radius, warn margin) and attach rules ("notify owner on exit", "ring
  tag when approaching the edge").
- **Calibrate** — BLE-scans nearby devices, lets you pick one, and walks you
  through recording RSSI at a few known distances to fit that specific tag's
  path-loss parameters (see below). Saves the result as a new BLE `Tag`.
- **Settings** — backend base URL (must be reachable from your phone - a LAN
  IP or hostname, not `10.0.2.2` which only works from the emulator).

## How BLE virtual fencing actually works here

RSSI gives you a distance estimate to the tag, but **no bearing** - a BLE scan
alone can't tell you which direction the dog is, only roughly how far. So
`fence/BleFenceEvaluator.kt` treats this honestly with interval arithmetic
instead of pretending to compute the dog's GPS position:

- It knows the phone's own distance to the fence's center (regular GPS
  haversine) and the tag's BLE distance from the phone.
- By the triangle inequality, the dog's true distance from the fence center is
  somewhere between `|phoneToCenter - tagDistance|` and
  `phoneToCenter + tagDistance`.
- If that whole range is safely inside the radius → `INSIDE`. If it's outside →
  `OUTSIDE`. If it straddles the boundary → `UNCERTAIN` (no alert fires; we'd
  rather stay quiet than cry wolf).

This is exactly determinate when the phone sits at the fence's center — e.g.
mount an old phone at the middle of the yard, or set the fence's center to
your current position before you walk away. That's the recommended setup:
"assess the area's bluetooth range" (via the Calibrate screen) at that
anchor point, then draw the fence radius to match a distance you've actually
measured, not a guess.

## Known gaps / follow-ups

- **BLE address rotation.** FMDN-network tags (OTAG included) rotate their
  advertised BLE address periodically to prevent stalking, the same way Apple's
  Find My accessories do. `ble/TagScanner.kt` currently pairs tags by a fixed
  MAC address, which will silently stop matching after a rotation. The
  Calibrate/pairing screen re-scans and lets you re-pair by proximity, but
  there's no automatic re-identification yet. Doing that properly needs either
  (a) periodic proximity-based re-pairing prompts, or (b) connecting via GATT
  and reading a stable identifier characteristic once a candidate is found -
  which requires OTAG's GATT layout, undocumented publicly; sniff it with nRF
  Connect against the real hardware if you want to go there.
- **Direct BLE "ring."** There's no known public GATT characteristic for
  triggering OTAG's sound locator directly. The app only rings via the
  backend's FMDN play-sound call (`POST /tags/{id}/ring`), which routes through
  Google's network and has real latency (their docs mention ~2-3 min in
  crowded areas; presumably much faster with the owner's own phone right next
  to it, but that's untested here).
- **Tractive fusion.** The backend records Tractive's GPS fixes as
  `LocationSample`s and the map shows the most recent sample per dog regardless
  of source, so a dog wearing both an OTAG and a Tractive collar will show
  whichever reported most recently - it doesn't yet do anything smarter like
  preferring the tighter-accuracy source when both are fresh.

## Permissions

Requested on launch: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, fine location
(needed for BLE scanning pre-Android 12 and for the fence evaluator's own GPS
fix), and notifications. The foreground service (`FenceEvaluatorService`) only
starts once all of those are granted.
