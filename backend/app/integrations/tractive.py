"""Adapter around the unofficial `aiotractive` client for Tractive GPS trackers.

Tractive has no official public API. `aiotractive` (pip install aiotractive) is the
same unofficial/reverse-engineered client used by Home Assistant's Tractive
integration. Configure TRACTIVE_EMAIL / TRACTIVE_PASSWORD to enable it.
"""
from dataclasses import dataclass
from typing import Optional

from app.config import get_settings


@dataclass
class TractiveLocation:
    lat: float
    lon: float
    accuracy_m: Optional[float]


class TractiveUnavailable(RuntimeError):
    pass


class TractiveClient:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._api = None

    async def _ensure_client(self):
        if not self._settings.tractive_enabled:
            raise TractiveUnavailable("TRACTIVE_EMAIL / TRACTIVE_PASSWORD are not set")
        if self._api is not None:
            return self._api
        try:
            import aiotractive  # type: ignore
        except ImportError as exc:  # pragma: no cover - depends on optional dep
            raise TractiveUnavailable(
                "aiotractive is not installed. `pip install aiotractive`."
            ) from exc
        self._api = aiotractive.Tractive(
            self._settings.tractive_email, self._settings.tractive_password
        )
        await self._api.authenticate()
        return self._api

    async def get_location(self, tracker_id: str) -> Optional[TractiveLocation]:
        api = await self._ensure_client()
        tracker = api.tracker(tracker_id)
        pos = await tracker.pos_report()
        if not pos or "latlong" not in pos:
            return None
        lat, lon = pos["latlong"]
        return TractiveLocation(lat=lat, lon=lon, accuracy_m=pos.get("pos_uncertainty"))

    async def close(self) -> None:
        if self._api is not None:
            await self._api.close()


_client: Optional[TractiveClient] = None


def get_tractive_client() -> TractiveClient:
    global _client
    if _client is None:
        _client = TractiveClient()
    return _client
