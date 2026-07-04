from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from app.db import get_session
from app.models import AlertRule, Fence

router = APIRouter(prefix="/fences", tags=["fences"])


@router.get("", response_model=list[Fence])
def list_fences(session: Session = Depends(get_session)):
    return session.exec(select(Fence)).all()


@router.post("", response_model=Fence)
def create_fence(fence: Fence, session: Session = Depends(get_session)):
    fence.id = None
    session.add(fence)
    session.commit()
    session.refresh(fence)
    return fence


@router.delete("/{fence_id}", status_code=204)
def delete_fence(fence_id: int, session: Session = Depends(get_session)):
    fence = session.get(Fence, fence_id)
    if fence is None:
        raise HTTPException(status_code=404, detail="Fence not found")
    session.delete(fence)
    session.commit()


@router.post("/{fence_id}/rules", response_model=AlertRule)
def add_rule(fence_id: int, rule: AlertRule, session: Session = Depends(get_session)):
    fence = session.get(Fence, fence_id)
    if fence is None:
        raise HTTPException(status_code=404, detail="Fence not found")
    rule.id = None
    rule.fence_id = fence_id
    session.add(rule)
    session.commit()
    session.refresh(rule)
    return rule


@router.get("/{fence_id}/rules", response_model=list[AlertRule])
def list_rules(fence_id: int, session: Session = Depends(get_session)):
    return session.exec(select(AlertRule).where(AlertRule.fence_id == fence_id)).all()
