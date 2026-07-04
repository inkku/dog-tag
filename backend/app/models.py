from datetime import datetime, timezone
from enum import Enum
from typing import Optional

from sqlmodel import Field, SQLModel


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class TagType(str, Enum):
    BLE = "ble"
    FMDN = "fmdn"
    TRACTIVE = "tractive"


class LocationSource(str, Enum):
    BLE_PROXIMITY = "ble_proximity"
    FMDN = "fmdn"
    TRACTIVE = "tractive"


class AlertAction(str, Enum):
    NOTIFY_OWNER = "notify_owner"
    RING_TAG = "ring_tag"


class AlertTrigger(str, Enum):
    EXIT = "exit"          # fence fully breached
    APPROACH = "approach"  # within warn_margin_m of the fence edge, still inside


class Dog(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    name: str
    color: str = "#4285F4"
    created_at: datetime = Field(default_factory=utcnow)


class Tag(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    dog_id: Optional[int] = Field(default=None, foreign_key="dog.id")
    name: str
    type: TagType

    # BLE identification + calibration (for RSSI -> distance estimation)
    mac_address: Optional[str] = None
    rssi_at_1m: Optional[float] = None       # measured RSSI at 1 meter, from calibration
    path_loss_exponent: Optional[float] = None  # environment-dependent, from calibration

    # FMDN identification (device id known to GoogleFindMyTools/Find Hub)
    fmdn_device_id: Optional[str] = None

    # Tractive identification (tracker id from the Tractive API)
    tractive_tracker_id: Optional[str] = None

    created_at: datetime = Field(default_factory=utcnow)


class Fence(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    dog_id: Optional[int] = Field(default=None, foreign_key="dog.id")  # None = applies to all dogs
    name: str
    center_lat: float
    center_lon: float
    radius_m: float
    warn_margin_m: float = 10.0
    created_at: datetime = Field(default_factory=utcnow)


class AlertRule(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    fence_id: int = Field(foreign_key="fence.id")
    trigger: AlertTrigger
    action: AlertAction
    enabled: bool = True


class LocationSample(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    tag_id: int = Field(foreign_key="tag.id")
    lat: float
    lon: float
    accuracy_m: Optional[float] = None
    source: LocationSource
    recorded_at: datetime = Field(default_factory=utcnow)


class AlertEvent(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    dog_id: int = Field(foreign_key="dog.id")
    fence_id: int = Field(foreign_key="fence.id")
    trigger: AlertTrigger
    action: AlertAction
    message: str
    created_at: datetime = Field(default_factory=utcnow)
