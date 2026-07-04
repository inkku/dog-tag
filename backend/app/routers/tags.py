from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from app.db import get_session
from app.integrations.fmdn import FmdnUnavailable, get_fmdn_client
from app.models import Tag

router = APIRouter(prefix="/tags", tags=["tags"])


@router.get("", response_model=list[Tag])
def list_tags(session: Session = Depends(get_session)):
    return session.exec(select(Tag)).all()


@router.post("", response_model=Tag)
def create_tag(tag: Tag, session: Session = Depends(get_session)):
    tag.id = None
    session.add(tag)
    session.commit()
    session.refresh(tag)
    return tag


@router.get("/{tag_id}", response_model=Tag)
def get_tag(tag_id: int, session: Session = Depends(get_session)):
    tag = session.get(Tag, tag_id)
    if tag is None:
        raise HTTPException(status_code=404, detail="Tag not found")
    return tag


@router.put("/{tag_id}", response_model=Tag)
def update_tag(tag_id: int, update: Tag, session: Session = Depends(get_session)):
    tag = session.get(Tag, tag_id)
    if tag is None:
        raise HTTPException(status_code=404, detail="Tag not found")
    data = update.model_dump(exclude={"id", "created_at"})
    for key, value in data.items():
        setattr(tag, key, value)
    session.add(tag)
    session.commit()
    session.refresh(tag)
    return tag


@router.delete("/{tag_id}", status_code=204)
def delete_tag(tag_id: int, session: Session = Depends(get_session)):
    tag = session.get(Tag, tag_id)
    if tag is None:
        raise HTTPException(status_code=404, detail="Tag not found")
    session.delete(tag)
    session.commit()


@router.post("/{tag_id}/ring")
def ring_tag(tag_id: int, session: Session = Depends(get_session)):
    """Ask the FMDN network to play sound on this tag (fallback when the phone
    can't reach it directly over BLE)."""
    tag = session.get(Tag, tag_id)
    if tag is None:
        raise HTTPException(status_code=404, detail="Tag not found")
    if tag.type != "fmdn" or not tag.fmdn_device_id:
        raise HTTPException(status_code=400, detail="Tag has no fmdn_device_id to ring")
    try:
        get_fmdn_client().play_sound(tag.fmdn_device_id)
    except FmdnUnavailable as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return {"status": "ringing"}
