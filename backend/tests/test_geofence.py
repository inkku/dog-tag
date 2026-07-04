from app.geofence import evaluate_fence, haversine_distance_m
from app.models import Fence


def test_haversine_zero_distance():
    assert haversine_distance_m(52.0, 4.0, 52.0, 4.0) == 0.0


def test_haversine_known_distance():
    # ~1 degree of latitude is ~111.19 km
    d = haversine_distance_m(0.0, 0.0, 1.0, 0.0)
    assert 110_500 < d < 111_500


def test_evaluate_fence_inside():
    fence = Fence(name="yard", center_lat=52.0, center_lon=4.0, radius_m=50, warn_margin_m=10)
    result = evaluate_fence(52.0, 4.0, fence)
    assert result.inside
    assert not result.approaching_edge
    assert result.distance_from_edge_m == 50


def test_evaluate_fence_approaching_edge():
    fence = Fence(name="yard", center_lat=52.0, center_lon=4.0, radius_m=50, warn_margin_m=10)
    # ~45m north of center, still inside but within the 10m warn margin
    result = evaluate_fence(52.0004, 4.0, fence)
    assert result.inside
    assert result.approaching_edge


def test_evaluate_fence_outside():
    fence = Fence(name="yard", center_lat=52.0, center_lon=4.0, radius_m=50, warn_margin_m=10)
    result = evaluate_fence(52.01, 4.0, fence)
    assert not result.inside
    assert not result.approaching_edge
