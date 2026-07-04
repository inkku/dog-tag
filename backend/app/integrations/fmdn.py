"""Adapter around GoogleFindMyTools (https://github.com/leonboe1/GoogleFindMyTools).

That project is an unofficial, reverse-engineered client for Google's Find Hub /
Find My Device network (FMDN). It is not on PyPI; install it per its own README
(one-time browser login to produce Auth/secrets.json), then point
FMDN_SECRETS_PATH at that file.

The exact module/function names in GoogleFindMyTools change as it's actively
reverse-engineered — treat the two `_call_*` methods below as the integration
seam to wire up against whatever version you've vendored, rather than a frozen
API. Everything else in this app only depends on the small `FmdnDevice` /
`FmdnLocation` shapes returned here.
"""
from dataclasses import dataclass

from app.config import get_settings


@dataclass
class FmdnDevice:
    device_id: str
    name: str


@dataclass
class FmdnLocation:
    lat: float
    lon: float
    accuracy_m: float | None


class FmdnUnavailable(RuntimeError):
    """Raised when FMDN isn't configured or the underlying library isn't installed."""


class FmdnClient:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._backend = None

    def _ensure_backend(self):
        if not self._settings.fmdn_enabled:
            raise FmdnUnavailable("FMDN_ENABLED is not set to true")
        if self._backend is not None:
            return self._backend
        try:
            # Vendored/installed copy of GoogleFindMyTools. Adjust this import to
            # match whatever entry point it exposes in the version you install.
            from GoogleFindMyTools import spot_api  # type: ignore
        except ImportError as exc:  # pragma: no cover - depends on optional dep
            raise FmdnUnavailable(
                "GoogleFindMyTools is not installed. See backend/README.md."
            ) from exc
        self._backend = spot_api.SpotApi(secrets_path=self._settings.fmdn_secrets_path)
        return self._backend

    def list_devices(self) -> list[FmdnDevice]:
        backend = self._ensure_backend()
        return [FmdnDevice(device_id=d["id"], name=d["name"]) for d in backend.list_devices()]

    def get_location(self, device_id: str) -> FmdnLocation | None:
        backend = self._ensure_backend()
        raw = backend.get_location(device_id)
        if raw is None:
            return None
        return FmdnLocation(lat=raw["lat"], lon=raw["lon"], accuracy_m=raw.get("accuracy"))

    def play_sound(self, device_id: str) -> None:
        backend = self._ensure_backend()
        backend.play_sound(device_id)


_client: FmdnClient | None = None


def get_fmdn_client() -> FmdnClient:
    global _client
    if _client is None:
        _client = FmdnClient()
    return _client
