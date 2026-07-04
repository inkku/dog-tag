from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from app.db import get_session
from app.models import Dog

router = APIRouter(prefix="/dogs", tags=["dogs"])


@router.get("", response_model=list[Dog])
def list_dogs(session: Session = Depends(get_session)):
    return session.exec(select(Dog)).all()


@router.post("", response_model=Dog)
def create_dog(dog: Dog, session: Session = Depends(get_session)):
    dog.id = None
    session.add(dog)
    session.commit()
    session.refresh(dog)
    return dog


@router.get("/{dog_id}", response_model=Dog)
def get_dog(dog_id: int, session: Session = Depends(get_session)):
    dog = session.get(Dog, dog_id)
    if dog is None:
        raise HTTPException(status_code=404, detail="Dog not found")
    return dog


@router.delete("/{dog_id}", status_code=204)
def delete_dog(dog_id: int, session: Session = Depends(get_session)):
    dog = session.get(Dog, dog_id)
    if dog is None:
        raise HTTPException(status_code=404, detail="Dog not found")
    session.delete(dog)
    session.commit()
