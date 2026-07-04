import os

os.environ.setdefault("DOG_TAG_DATABASE_URL", "sqlite://")

import pytest
from sqlmodel import Session, SQLModel, create_engine
from sqlmodel.pool import StaticPool

from app import db as db_module
from app.main import app


@pytest.fixture
def session():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    SQLModel.metadata.create_all(engine)
    db_module.engine = engine
    with Session(engine) as s:
        yield s


@pytest.fixture
def client(session):
    from fastapi.testclient import TestClient

    from app.db import get_session

    def override_get_session():
        yield session

    app.dependency_overrides[get_session] = override_get_session
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()
