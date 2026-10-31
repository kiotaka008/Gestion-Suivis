from unittest.mock import patch
import pytest
from fastapi.testclient import TestClient


async def fake_stream(*args, **kwargs):
    chunks = ["Bonjour, ", "ceci ", "est ", "un ", "test ", "de ", "streaming."]
    for chunk in chunks:
        yield chunk


def test_chat_stream_endpoint(
    client: TestClient,
    auth_headers: dict,
) -> None:
    """Teste que le streaming SSE renvoie bien des chunks."""
    from app.services.llm_service import LLMService

    with patch.object(LLMService, "stream_complete", side_effect=fake_stream):
        response = client.post(
            "/api/ai/chat/stream",
            json={"messages": [{"role": "user", "content": "Test"}]},
            headers=auth_headers,
        )

    assert response.status_code == 200
    assert "text/event-stream" in response.headers["content-type"]

    # Verifier que le flux contient les chunks
    content = response.text
    assert "data: " in content
    assert '"chunk":' in content
    assert "data: [DONE]" in content


def test_chat_stream_requires_auth(client: TestClient) -> None:
    """Le streaming doit exiger la cle API."""
    response = client.post(
        "/api/ai/chat/stream",
        json={"messages": [{"role": "user", "content": "Test"}]},
    )
    assert response.status_code == 401
