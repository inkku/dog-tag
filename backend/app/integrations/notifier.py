"""Owner notifications via a generic webhook (works out of the box with ntfy.sh)."""
import httpx

from app.config import get_settings


async def notify_owner(title: str, message: str) -> None:
    settings = get_settings()
    if not settings.notify_webhook_url:
        return
    async with httpx.AsyncClient(timeout=10.0) as client:
        await client.post(
            settings.notify_webhook_url,
            headers={"Title": title},
            content=message.encode("utf-8"),
        )
