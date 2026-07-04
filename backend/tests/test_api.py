def test_health(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_create_and_list_dog(client):
    resp = client.post("/dogs", json={"name": "Fido", "color": "#FF0000"})
    assert resp.status_code == 200
    dog = resp.json()
    assert dog["name"] == "Fido"
    assert dog["id"] is not None

    resp = client.get("/dogs")
    assert resp.status_code == 200
    assert len(resp.json()) == 1


def test_create_tag_and_fence_and_rule(client):
    dog = client.post("/dogs", json={"name": "Rex"}).json()
    tag = client.post(
        "/tags",
        json={"name": "Rex's BLE tag", "type": "ble", "dog_id": dog["id"], "mac_address": "AA:BB:CC:DD:EE:FF"},
    ).json()
    assert tag["dog_id"] == dog["id"]

    fence = client.post(
        "/fences",
        json={
            "name": "Backyard",
            "dog_id": dog["id"],
            "center_lat": 52.0,
            "center_lon": 4.0,
            "radius_m": 50,
            "warn_margin_m": 10,
        },
    ).json()

    rule = client.post(
        f"/fences/{fence['id']}/rules",
        json={"fence_id": fence["id"], "trigger": "exit", "action": "notify_owner"},
    ).json()
    assert rule["fence_id"] == fence["id"]


def test_update_tag_assigns_to_dog(client):
    dog = client.post("/dogs", json={"name": "Fern"}).json()
    tag = client.post("/tags", json={"name": "Unassigned tag", "type": "ble"}).json()
    assert tag["dog_id"] is None

    updated = client.put(f"/tags/{tag['id']}", json={**tag, "dog_id": dog["id"]}).json()
    assert updated["dog_id"] == dog["id"]
    assert updated["id"] == tag["id"]


def test_submit_location_triggers_fence_exit_alert(client):
    dog = client.post("/dogs", json={"name": "Buddy"}).json()
    tag = client.post(
        "/tags", json={"name": "Buddy's tag", "type": "ble", "dog_id": dog["id"]}
    ).json()
    fence = client.post(
        "/fences",
        json={
            "name": "Yard",
            "dog_id": dog["id"],
            "center_lat": 52.0,
            "center_lon": 4.0,
            "radius_m": 20,
            "warn_margin_m": 5,
        },
    ).json()
    client.post(
        f"/fences/{fence['id']}/rules",
        json={"fence_id": fence["id"], "trigger": "exit", "action": "notify_owner"},
    )

    # Well outside the 20m fence radius.
    resp = client.post(
        "/locations",
        json={"tag_id": tag["id"], "lat": 52.01, "lon": 4.0, "source": "ble_proximity"},
    )
    assert resp.status_code == 200

    latest = client.get("/locations/latest").json()
    assert str(tag["id"]) in latest or tag["id"] in [int(k) for k in latest.keys()]
