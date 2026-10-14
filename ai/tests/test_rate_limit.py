from fastapi.testclient import TestClient


def test_rate_limit_allows_normal_requests(client: TestClient) -> None:
    """Les premieres requetes passent."""
    response = client.get("/health")
    assert response.status_code == 200


def test_health_is_not_rate_limited(client: TestClient) -> None:
    """/health n'est pas soumis au rate limit."""
    for _ in range(50):
        response = client.get("/health")
        assert response.status_code == 200


def test_rate_limiter_directly() -> None:
    """Test direct du RateLimiter (sans passer par le middleware)."""
    from app.core.rate_limit import RateLimiter

    class FakeRequest:
        class client:
            host = "127.0.0.1"
        headers = {}
        url = type("URL", (), {"path": "/test"})()

    limiter = RateLimiter(max_requests_per_minute=3)

    # 3 premieres requetes OK
    for i in range(3):
        allowed, remaining = limiter.is_allowed(FakeRequest())
        assert allowed is True, f"Requete {i+1} devait etre autorisee"

    # 4e requete bloquee
    allowed, remaining = limiter.is_allowed(FakeRequest())
    assert allowed is False, "Requete 4 devait etre bloquee"

    # 5e requete bloquee
    allowed, remaining = limiter.is_allowed(FakeRequest())
    assert allowed is False, "Requete 5 devait etre bloquee"


def test_rate_limiter_reset() -> None:
    """Le reset vide les compteurs."""
    from app.core.rate_limit import RateLimiter

    class FakeRequest:
        class client:
            host = "127.0.0.1"
        headers = {}
        url = type("URL", (), {"path": "/test"})()

    limiter = RateLimiter(max_requests_per_minute=2)
    limiter.is_allowed(FakeRequest())
    limiter.is_allowed(FakeRequest())
    limiter.reset()

    allowed, _ = limiter.is_allowed(FakeRequest())
    assert allowed is True
