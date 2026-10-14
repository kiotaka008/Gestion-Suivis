from fastapi.testclient import TestClient


def test_health_returns_200(client: TestClient) -> None:
    """Vérifie que /health répond 200."""
    response = client.get("/health")
    assert response.status_code == 200


def test_health_returns_ok_status(client: TestClient) -> None:
    """Vérifie la structure de la réponse."""
    response = client.get("/health")
    data = response.json()

    assert data["status"] == "ok"
    assert data["service"] == "ai-service"
    assert data["version"] == "0.1.0"
    assert "environment" in data
    assert "timestamp" in data


def test_health_does_not_require_auth(client: TestClient) -> None:
    """/health doit être accessible sans clé API."""
    response = client.get("/health")
    assert response.status_code == 200