import json
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from app.models.schemas import TokenUsage


def make_mock_response(content: dict) -> dict:
    return {
        "content": json.dumps(content),
        "usage": TokenUsage(prompt_tokens=10, completion_tokens=20, total_tokens=30),
        "model": "gemini-3.8-flash",
    }


def test_chat_endpoint(
    client: TestClient,
    auth_headers: dict,
) -> None:
    mock_resp = {
        "content": "Bonjour, je suis un assistant.",
        "usage": TokenUsage(prompt_tokens=10, completion_tokens=20, total_tokens=30),
        "model": "gemini-3.8-flash",
    }
    with patch(
        "app.services.llm_service.LLMService.complete",
        new_callable=AsyncMock,
        return_value=mock_resp,
    ):
        response = client.post(
            "/api/ai/chat",
            json={"messages": [{"role": "user", "content": "Bonjour"}]},
            headers=auth_headers,
        )
    assert response.status_code == 200
    data = response.json()
    assert data["message"]["role"] == "assistant"
    assert "Bonjour" in data["message"]["content"]


def test_summarize_endpoint(
    client: TestClient,
    auth_headers: dict,
) -> None:
    mock_resp = make_mock_response({
        "summary": "Resume du texte",
        "key_points": ["point 1", "point 2"],
    })
    with patch(
        "app.services.llm_service.LLMService.complete",
        new_callable=AsyncMock,
        return_value=mock_resp,
    ):
        response = client.post(
            "/api/ai/summarize",
            json={"content": "Un long texte a resumer", "max_words": 100},
            headers=auth_headers,
        )
    assert response.status_code == 200
    data = response.json()
    assert data["summary"] == "Resume du texte"
    assert len(data["key_points"]) == 2


def test_analyze_risks_endpoint(
    client: TestClient,
    auth_headers: dict,
) -> None:
    mock_resp = make_mock_response({
        "overall_risk": "high",
        "risks": [
            {
                "level": "high",
                "title": "Risque 1",
                "description": "Description",
                "recommendation": "Action",
            }
        ],
        "summary": "Risque eleve",
    })
    with patch(
        "app.services.llm_service.LLMService.complete",
        new_callable=AsyncMock,
        return_value=mock_resp,
    ):
        response = client.post(
            "/api/ai/analyze-risks",
            json={
                "project_name": "Test",
                "activities": [],
            },
            headers=auth_headers,
        )
    assert response.status_code == 200
    data = response.json()
    assert data["overall_risk"] == "high"
    assert len(data["risks"]) == 1


def test_classify_endpoint(
    client: TestClient,
    auth_headers: dict,
) -> None:
    mock_resp = make_mock_response({
        "category": "performance",
        "confidence": 0.95,
        "reasoning": "Raison",
    })
    with patch(
        "app.services.llm_service.LLMService.complete",
        new_callable=AsyncMock,
        return_value=mock_resp,
    ):
        response = client.post(
            "/api/ai/classify",
            json={"text": "Le site est lent", "categories": ["bug", "performance"]},
            headers=auth_headers,
        )
    assert response.status_code == 200
    data = response.json()
    assert data["category"] == "performance"
    assert data["confidence"] == 0.95


def test_extract_endpoint(
    client: TestClient,
    auth_headers: dict,
) -> None:
    mock_resp = make_mock_response({
        "extracted": {"nom": "Marie", "email": "marie@test.com"},
    })
    with patch(
        "app.services.llm_service.LLMService.complete",
        new_callable=AsyncMock,
        return_value=mock_resp,
    ):
        response = client.post(
            "/api/ai/extract",
            json={
                "text": "Je m'appelle Marie, contact: marie@test.com",
                "fields": ["nom", "email"],
            },
            headers=auth_headers,
        )
    assert response.status_code == 200
    data = response.json()
    assert data["extracted"]["nom"] == "Marie"


def test_generate_report_endpoint(
    client: TestClient,
    auth_headers: dict,
) -> None:
    mock_resp = make_mock_response({
        "title": "Rapport hebdo",
        "content": "Contenu du rapport",
        "sections": {"Section 1": "Contenu 1"},
    })
    with patch(
        "app.services.llm_service.LLMService.complete",
        new_callable=AsyncMock,
        return_value=mock_resp,
    ):
        response = client.post(
            "/api/ai/generate-report",
            json={
                "report_type": "weekly",
                "data": {"key": "value"},
            },
            headers=auth_headers,
        )
    assert response.status_code == 200
    data = response.json()
    assert data["title"] == "Rapport hebdo"
