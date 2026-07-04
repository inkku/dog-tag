"""Fence evaluation + alert dispatch, shared by the poller and location-submit endpoint."""
from datetime import timedelta, timezone
from typing import Optional

from sqlmodel import Session, select

from app.geofence import evaluate_fence
from app.integrations.fmdn import FmdnUnavailable, get_fmdn_client
from app.integrations.notifier import notify_owner
from app.models import (
    AlertAction,
    AlertEvent,
    AlertRule,
    AlertTrigger,
    Dog,
    Fence,
    LocationSample,
    Tag,
    utcnow,
)

# Don't re-fire the same rule more often than this, so a dog sitting right on a
# fence boundary doesn't spam notifications every poll cycle.
ALERT_COOLDOWN = timedelta(minutes=5)


def latest_location_for_dog(session: Session, dog_id: int) -> Optional[LocationSample]:
    tag_ids = session.exec(select(Tag.id).where(Tag.dog_id == dog_id)).all()
    if not tag_ids:
        return None
    return session.exec(
        select(LocationSample)
        .where(LocationSample.tag_id.in_(tag_ids))
        .order_by(LocationSample.recorded_at.desc())
    ).first()


def _recently_fired(session: Session, rule: AlertRule, dog_id: int) -> bool:
    last = session.exec(
        select(AlertEvent)
        .where(AlertEvent.dog_id == dog_id, AlertEvent.fence_id == rule.fence_id)
        .where(AlertEvent.trigger == rule.trigger)
        .order_by(AlertEvent.created_at.desc())
    ).first()
    if last is None:
        return False
    last_created = last.created_at
    if last_created.tzinfo is None:
        # SQLite round-trips datetimes as naive; utcnow() stored them in UTC,
        # so re-attach UTC before comparing with the (aware) current time.
        last_created = last_created.replace(tzinfo=timezone.utc)
    return utcnow() - last_created < ALERT_COOLDOWN


async def _dispatch(session: Session, dog: Dog, fence: Fence, rule: AlertRule) -> None:
    if _recently_fired(session, rule, dog.id):
        return

    if rule.action == AlertAction.NOTIFY_OWNER:
        verb = "left" if rule.trigger == AlertTrigger.EXIT else "is approaching the edge of"
        message = f"{dog.name} {verb} fence '{fence.name}'"
        await notify_owner(title="Dog Tag alert", message=message)
    elif rule.action == AlertAction.RING_TAG:
        tag = session.exec(
            select(Tag).where(Tag.dog_id == dog.id, Tag.fmdn_device_id.is_not(None))
        ).first()
        message = f"Ringing {dog.name}'s tag near fence '{fence.name}'"
        if tag is not None:
            try:
                get_fmdn_client().play_sound(tag.fmdn_device_id)
            except FmdnUnavailable:
                pass
    else:
        message = f"Unhandled alert action {rule.action}"

    session.add(
        AlertEvent(
            dog_id=dog.id,
            fence_id=fence.id,
            trigger=rule.trigger,
            action=rule.action,
            message=message,
        )
    )
    session.commit()


async def evaluate_dog_fences(session: Session, dog: Dog, lat: float, lon: float) -> None:
    fences = session.exec(
        select(Fence).where((Fence.dog_id == dog.id) | (Fence.dog_id.is_(None)))
    ).all()
    for fence in fences:
        evaluation = evaluate_fence(lat, lon, fence)
        rules = session.exec(
            select(AlertRule).where(AlertRule.fence_id == fence.id, AlertRule.enabled == True)  # noqa: E712
        ).all()
        for rule in rules:
            if rule.trigger == AlertTrigger.EXIT and not evaluation.inside:
                await _dispatch(session, dog, fence, rule)
            elif rule.trigger == AlertTrigger.APPROACH and evaluation.approaching_edge:
                await _dispatch(session, dog, fence, rule)
