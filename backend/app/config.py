import os
from functools import lru_cache
from typing import Optional


class Settings:
    database_url: str = os.environ.get("DOG_TAG_DATABASE_URL", "sqlite:///./dogtag.db")

    fmdn_secrets_path: str = os.environ.get("FMDN_SECRETS_PATH", "./Auth/secrets.json")
    fmdn_enabled: bool = os.environ.get("FMDN_ENABLED", "false").lower() == "true"

    tractive_email: Optional[str] = os.environ.get("TRACTIVE_EMAIL")
    tractive_password: Optional[str] = os.environ.get("TRACTIVE_PASSWORD")
    tractive_enabled: bool = bool(tractive_email and tractive_password)

    notify_webhook_url: Optional[str] = os.environ.get("NOTIFY_WEBHOOK_URL")

    poll_interval_seconds: int = int(os.environ.get("POLL_INTERVAL_SECONDS", "60"))


@lru_cache
def get_settings() -> Settings:
    return Settings()
