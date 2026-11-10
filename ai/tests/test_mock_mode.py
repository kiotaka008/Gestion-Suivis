"""Mode démo (USE_MOCK=true) : toutes les tâches répondent sans appel à Groq."""

import json
from collections.abc import Iterator
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from app.models.schemas import AITaskType
from app.services.llm_service import LLMService, get_llm_service
from app.services.mock_llm import MODELE_SIMULE, contenu_simule, tache_depuis_prompt
from app.services.prompt_service import SYSTEM_PROMPTS


def _service_demo() -> LLMService:
    service = LLMService()
    service._settings = service._settings.model_copy(update={"use_mock": True})
    return service


@pytest.fixture
def mode_demo() -> Iterator[None]:
    """Active le mode démo sur le service partagé par les routes."""
    service = get_llm_service()
    demo = service._settings.model_copy(update={"use_mock": True})
    with patch.object(service, "_settings", demo):
        yield


def test_task_found_from_system_prompt() -> None:
    for tache, prompt in SYSTEM_PROMPTS.items():
        assert tache_depuis_prompt(prompt) is tache
    assert tache_depuis_prompt("prompt inconnu") is AITaskType.CHAT


def test_structured_tasks_return_valid_json() -> None:
    for tache in AITaskType:
        contenu = contenu_simule(tache)
        if tache is AITaskType.CHAT:
            assert contenu.strip()
        else:
            assert isinstance(json.loads(contenu), dict)


@pytest.mark.asyncio
async def test_complete_in_mock_mode_never_calls_groq() -> None:
    service = _service_demo()
    with patch.object(service.client.chat.completions, "create", new_callable=AsyncMock) as create:
        result = await service.complete(
            system_prompt=SYSTEM_PROMPTS[AITaskType.ANALYZE_RISKS],
            messages=[{"role": "user", "content": "Projet"}],
            response_format={"type": "json_object"},
        )
    create.assert_not_awaited()
    assert result["model"] == MODELE_SIMULE
    assert json.loads(result["content"])["overall_risk"] == "medium"


@pytest.mark.asyncio
async def test_stream_in_mock_mode_yields_words() -> None:
    service = _service_demo()
    morceaux = [m async for m in service.stream_complete(system_prompt="x", messages=[])]
    assert "".join(morceaux) == contenu_simule(AITaskType.CHAT)
    assert len(morceaux) > 1


def test_analyze_risks_route_in_mock_mode(client: TestClient, auth_headers: dict, mode_demo: None) -> None:
    """Requête identique à celle envoyée par le backend Spring Boot."""
    response = client.post(
        "/api/ai/analyze-risks",
        headers=auth_headers,
        json={
            "project_name": "Website redesign",
            "project_description": "Refonte du site",
            "activities": [{"title": "Maquettes", "status": "in_progress", "progress": 40}],
            "language": "fr",
        },
    )
    assert response.status_code == 200
    corps = response.json()
    assert corps["overall_risk"] == "medium"
    assert len(corps["risks"]) == 2
    assert corps["model"] == MODELE_SIMULE


def test_generate_report_route_in_mock_mode(client: TestClient, auth_headers: dict, mode_demo: None) -> None:
    response = client.post(
        "/api/ai/generate-report",
        headers=auth_headers,
        json={"report_type": "weekly", "data": {"projects": 3}, "language": "fr"},
    )
    assert response.status_code == 200
    corps = response.json()
    assert corps["title"] == "Rapport de démonstration"
    assert corps["sections"]


def test_other_routes_in_mock_mode(client: TestClient, auth_headers: dict, mode_demo: None) -> None:
    resume = client.post(
        "/api/ai/summarize",
        headers=auth_headers,
        json={"content": "Un texte assez long pour être résumé par le service."},
    )
    assert resume.status_code == 200
    classement = client.post(
        "/api/ai/classify",
        headers=auth_headers,
        json={"text": "Bug sur la page de connexion", "categories": ["bug", "feature"]},
    )
    assert classement.status_code == 200
    assert classement.json()["category"] == "bug"
