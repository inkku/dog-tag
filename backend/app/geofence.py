"""Circle-based virtual fence math."""
from dataclasses import dataclass
from math import atan2, cos, radians, sin, sqrt

from app.models import Fence

EARTH_RADIUS_M = 6_371_000.0


def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    phi1, phi2 = radians(lat1), radians(lat2)
    d_phi = radians(lat2 - lat1)
    d_lambda = radians(lon2 - lon1)
    a = sin(d_phi / 2) ** 2 + cos(phi1) * cos(phi2) * sin(d_lambda / 2) ** 2
    return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))


@dataclass
class FenceEvaluation:
    distance_from_center_m: float
    distance_from_edge_m: float  # positive = inside fence, negative = outside
    inside: bool
    approaching_edge: bool  # inside, but within warn_margin_m of the boundary


def evaluate_fence(lat: float, lon: float, fence: Fence) -> FenceEvaluation:
    distance_from_center = haversine_distance_m(lat, lon, fence.center_lat, fence.center_lon)
    distance_from_edge = fence.radius_m - distance_from_center
    inside = distance_from_edge >= 0
    approaching_edge = inside and distance_from_edge <= fence.warn_margin_m
    return FenceEvaluation(
        distance_from_center_m=distance_from_center,
        distance_from_edge_m=distance_from_edge,
        inside=inside,
        approaching_edge=approaching_edge,
    )
