import pytest
from fastapi.testclient import TestClient


@pytest.fixture(autouse=True)
def reset_conversations():
    """Reset le service avant chaque test."""
    from app.services.conversation_service import get_conversation_service
    service = get_conversation_service()
    service.reset()
    yield
    service.reset()


def test_list_empty_conversations(
    client: TestClient,
    auth_headers: dict,
) -> None:
    response = client.get(
        "/api/ai/conversations",
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["conversations"] == []
    assert data["total"] == 0


def test_list_requires_auth(client: TestClient) -> None:
    response = client.get("/api/ai/conversations")
    assert response.status_code == 401


def test_get_nonexistent_conversation(
    client: TestClient,
    auth_headers: dict,
) -> None:
    response = client.get(
        "/api/ai/conversations/unknown-id",
        headers=auth_headers,
    )
    assert response.status_code == 404


def test_delete_nonexistent_conversation(
    client: TestClient,
    auth_headers: dict,
) -> None:
    response = client.delete(
        "/api/ai/conversations/unknown-id",
        headers=auth_headers,
    )
    assert response.status_code == 404


def test_create_and_list_conversation(
    client: TestClient,
    auth_headers: dict,
) -> None:
    from app.services.conversation_service import get_conversation_service
    service = get_conversation_service()

    conv = service.create(user_id="test-user")
    service.add_message(conv.id, "user", "Bonjour")
    service.add_message(conv.id, "assistant", "Salut !")

    response = client.get(
        "/api/ai/conversations?user_id=test-user",
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 1
    assert data["conversations"][0]["id"] == conv.id
    assert data["conversations"][0]["message_count"] == 2
    assert data["conversations"][0]["preview"] == "Bonjour"


def test_get_full_conversation(
    client: TestClient,
    auth_headers: dict,
) -> None:
    from app.services.conversation_service import get_conversation_service
    service = get_conversation_service()

    conv = service.create(user_id="user-x")
    service.add_message(conv.id, "user", "Question 1")
    service.add_message(conv.id, "assistant", "Reponse 1")

    response = client.get(
        f"/api/ai/conversations/{conv.id}",
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["id"] == conv.id
    assert len(data["messages"]) == 2
    assert data["messages"][0]["role"] == "user"
    assert data["messages"][0]["content"] == "Question 1"
    assert data["messages"][1]["role"] == "assistant"


def test_delete_conversation(
    client: TestClient,
    auth_headers: dict,
) -> None:
    from app.services.conversation_service import get_conversation_service
    service = get_conversation_service()

    conv = service.create(user_id="to-delete")
    service.add_message(conv.id, "user", "Test")

    response = client.delete(
        f"/api/ai/conversations/{conv.id}",
        headers=auth_headers,
    )
    assert response.status_code == 204

    response = client.get(
        f"/api/ai/conversations/{conv.id}",
        headers=auth_headers,
    )
    assert response.status_code == 404


def test_conversation_service_directly() -> None:
    from app.services.conversation_service import ConversationService

    service = ConversationService()

    conv = service.create(user_id="direct-test")
    assert conv.id is not None
    assert conv.message_count == 0

    service.add_message(conv.id, "user", "Hello")
    service.add_message(conv.id, "assistant", "Hi")

    retrieved = service.get(conv.id)
    assert retrieved is not None
    assert retrieved.message_count == 2

    summaries = service.list_by_user("direct-test")
    assert len(summaries) == 1

    assert service.delete(conv.id) is True
    assert service.get(conv.id) is None
