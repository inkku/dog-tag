import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.db import init_db
from app.poller import run_forever
from app.routers import dogs, fences, locations, tags


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    poll_task = asyncio.create_task(run_forever())
    try:
        yield
    finally:
        poll_task.cancel()


app = FastAPI(title="Dog Tag backend", lifespan=lifespan)

app.include_router(dogs.router)
app.include_router(tags.router)
app.include_router(fences.router)
app.include_router(locations.router)


@app.get("/health")
def health():
    return {"status": "ok"}
