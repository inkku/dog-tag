"""Background loop: pull FMDN + Tractive positions and evaluate fences.

BLE-proximity fences are evaluated immediately, phone-side and via the
/locations POST endpoint. This poller covers the slower "network location"
tier (FMDN, Tractive) for when a dog is out of direct BLE range.
"""
import asyncio
import logging

from sqlmodel import Session, select

from app import db
from app.alerts import evaluate_dog_fences
from app.config import get_settings
from app.integrations.fmdn import FmdnUnavailable, get_fmdn_client
from app.integrations.tractive import TractiveUnavailable, get_tractive_client
from app.models import Dog, LocationSample, LocationSource, Tag, TagType

log = logging.getLogger("dogtag.poller")


async def _poll_fmdn(session: Session) -> None:
    settings = get_settings()
    if not settings.fmdn_enabled:
        return
    client = get_fmdn_client()
    tags = session.exec(select(Tag).where(Tag.type == TagType.FMDN)).all()
    for tag in tags:
        if not tag.fmdn_device_id:
            continue
        try:
            location = client.get_location(tag.fmdn_device_id)
        except FmdnUnavailable as exc:
            log.warning("FMDN unavailable: %s", exc)
            return
        if location is None:
            continue
        session.add(
            LocationSample(
                tag_id=tag.id,
                lat=location.lat,
                lon=location.lon,
                accuracy_m=location.accuracy_m,
                source=LocationSource.FMDN,
            )
        )
    session.commit()


async def _poll_tractive(session: Session) -> None:
    settings = get_settings()
    if not settings.tractive_enabled:
        return
    client = get_tractive_client()
    tags = session.exec(select(Tag).where(Tag.type == TagType.TRACTIVE)).all()
    for tag in tags:
        if not tag.tractive_tracker_id:
            continue
        try:
            location = await client.get_location(tag.tractive_tracker_id)
        except TractiveUnavailable as exc:
            log.warning("Tractive unavailable: %s", exc)
            return
        if location is None:
            continue
        session.add(
            LocationSample(
                tag_id=tag.id,
                lat=location.lat,
                lon=location.lon,
                accuracy_m=location.accuracy_m,
                source=LocationSource.TRACTIVE,
            )
        )
    session.commit()


async def _evaluate_all_dogs(session: Session) -> None:
    from app.alerts import latest_location_for_dog

    dogs = session.exec(select(Dog)).all()
    for dog in dogs:
        latest = latest_location_for_dog(session, dog.id)
        if latest is not None:
            await evaluate_dog_fences(session, dog, latest.lat, latest.lon)


async def poll_once() -> None:
    with Session(db.engine) as session:
        await _poll_fmdn(session)
        await _poll_tractive(session)
        await _evaluate_all_dogs(session)


async def run_forever() -> None:
    settings = get_settings()
    while True:
        try:
            await poll_once()
        except Exception:  # noqa: BLE001 - keep the poller alive across errors
            log.exception("Poll cycle failed")
        await asyncio.sleep(settings.poll_interval_seconds)
