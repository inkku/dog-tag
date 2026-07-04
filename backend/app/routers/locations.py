from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from app.alerts import evaluate_dog_fences
from app.db import get_session
from app.models import Dog, LocationSample, Tag

router = APIRouter(prefix="/locations", tags=["locations"])


@router.post("", response_model=LocationSample)
async def submit_location(sample: LocationSample, session: Session = Depends(get_session)):
    """Phone posts BLE-proximity-derived fixes here so the backend has a full
    location history per tag alongside FMDN/Tractive samples, and so fences get
    evaluated immediately using the freshest position available."""
    tag = session.get(Tag, sample.tag_id)
    if tag is None:
        raise HTTPException(status_code=404, detail="Tag not found")
    sample.id = None
    session.add(sample)
    session.commit()
    session.refresh(sample)

    if tag.dog_id is not None:
        dog = session.get(Dog, tag.dog_id)
        if dog is not None:
            await evaluate_dog_fences(session, dog, sample.lat, sample.lon)

    return sample


@router.get("/latest", response_model=dict[int, LocationSample])
def latest_per_tag(session: Session = Depends(get_session)):
    """Most recent location sample for every tag, keyed by tag id."""
    samples = session.exec(
        select(LocationSample).order_by(LocationSample.recorded_at.desc())
    ).all()
    latest: dict[int, LocationSample] = {}
    for sample in samples:
        if sample.tag_id not in latest:
            latest[sample.tag_id] = sample
    return latest


@router.get("/tag/{tag_id}", response_model=list[LocationSample])
def history_for_tag(tag_id: int, limit: int = 100, session: Session = Depends(get_session)):
    return session.exec(
        select(LocationSample)
        .where(LocationSample.tag_id == tag_id)
        .order_by(LocationSample.recorded_at.desc())
        .limit(limit)
    ).all()
