# Dog Tag backend

Self-hosted service that talks to Google Find Hub (FMDN) and Tractive on your
behalf, stores dogs/tags/fences, and evaluates geofences for the "network
location" tier (BLE-proximity fencing is evaluated on the phone instead — see
`../android/README.md`).

## Run it

Requires Python 3.9+ (check with `python3 --version` — macOS's built-in
`/usr/bin/python3` is sometimes older than this; install a newer one via
[python.org](https://www.python.org/downloads/) or `brew install python@3.12`
if so, and use that interpreter to create the venv below).

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # then edit
export $(cat .env | xargs)
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Open `http://<host>:8000/docs` for interactive API docs.

## Enabling FMDN (OTAG tag location + ring, via Google's Find Hub network)

1. On a machine with Chrome installed, follow the setup in
   [GoogleFindMyTools](https://github.com/leonboe1/GoogleFindMyTools): install it,
   run its login flow with the Google account your OTAG tags are paired to. This
   produces `Auth/secrets.json`.
2. `pip install git+https://github.com/leonboe1/GoogleFindMyTools.git` into this
   backend's venv, and copy `Auth/secrets.json` to wherever `FMDN_SECRETS_PATH`
   points.
3. Set `FMDN_ENABLED=true`.
4. `app/integrations/fmdn.py` has a single seam (`_ensure_backend`) that imports
   the library and wraps its device-list / get-location / play-sound calls.
   GoogleFindMyTools is an actively evolving reverse-engineered project, so
   double-check its current module/function names against what's imported there
   before relying on it — that adapter is intentionally the one place you should
   expect to edit.
5. Once a `Tag` row has `type=fmdn` and `fmdn_device_id` set to the id
   GoogleFindMyTools reports for that tag, the poller will start recording its
   location and `POST /tags/{id}/ring` will work.

## Enabling Tractive

Set `TRACTIVE_EMAIL` / `TRACTIVE_PASSWORD`. Create a `Tag` with `type=tractive`
and `tractive_tracker_id` set to the tracker's id (visible in the Tractive app's
URL/API, or by listing `api.trackers()` via `aiotractive` once authenticated).

## Notifications

`NOTIFY_WEBHOOK_URL` is POSTed to with a `Title` header and a plain-text body on
every alert. Works out of the box with [ntfy.sh](https://ntfy.sh) — pick a
private topic name and set `NOTIFY_WEBHOOK_URL=https://ntfy.sh/your-topic-name`,
then subscribe to that topic in the ntfy Android/iOS app. Any other webhook
receiver (Slack incoming webhook, Home Assistant, etc.) works too if you adjust
the payload shape in `app/integrations/notifier.py`.

## Data model

- **Dog** — a tracked animal.
- **Tag** — one physical tracker (`ble`, `fmdn`, or `tractive`), optionally
  assigned to a dog. A dog can have more than one tag (e.g. the dog that wears
  both an OTAG and the Tractive collar has two `Tag` rows).
- **Fence** — a circular geofence (center + radius + warn margin), either tied
  to one dog or `dog_id=null` to apply to every dog.
- **AlertRule** — attached to a fence: on `exit` or `approach`, either
  `notify_owner` or `ring_tag`.
- **LocationSample** — one position fix, tagged with its source
  (`ble_proximity`, `fmdn`, `tractive`) so history and fusion logic can tell
  them apart.
- **AlertEvent** — a fired alert, used for the 5-minute cooldown that stops a
  dog sitting on a fence boundary from spamming notifications.

## Tests

```bash
pip install -r requirements-dev.txt
pytest -q
```
