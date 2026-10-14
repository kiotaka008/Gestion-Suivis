from fastapi.testclient import TestClient


def test_endpoint_without_api_key_returns_401(client: TestClient) -> None:
    """Sans clé API → 401 Unauthorized."""
    response = client.post(
        "/api/ai/chat",
        json={"messages": [{"role": "user", "content": "Bonjour"}]},
    )
    assert response.status_code == 401
    assert "Clé API interne manquante" in response.json()["detail"]


def test_endpoint_with_wrong_api_key_returns_401(client: TestClient) -> None:
    """Avec mauvaise clé → 401 Unauthorized."""
    response = client.post(
        "/api/ai/chat",
        json={"messages": [{"role": "user", "content": "Bonjour"}]},
        headers={"X-Internal-API-Key": "wrong-key-12345"},
    )
    assert response.status_code == 401
    assert "Clé API interne invalide" in response.json()["detail"]


def test_endpoint_with_correct_api_key_passes_security(
    client: TestClient,
    auth_headers: dict[str, str],
    mock_llm_service,
) -> None:
    """Avec bonne clé → passe la sécurité."""
    response = client.post(
        "/api/ai/chat",
        json={"messages": [{"role": "user", "content": "Bonjour"}]},
        headers=auth_headers,
    )
    assert response.status_code != 401