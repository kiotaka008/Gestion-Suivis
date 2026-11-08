from collections.abc import Mapping
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest
from groq import APIStatusError, omit

from app.core.exceptions import LLMError, LLMQuotaError, UnauthorizedError
from app.services.llm_service import LLMService, get_llm_service


def _reponse_groq(contenu: str = "Reponse simulee") -> MagicMock:
    """Réponse au format chat.completions de Groq (compatible OpenAI)."""
    reponse = MagicMock()
    reponse.choices = [MagicMock()]
    reponse.choices[0].message.content = contenu
    reponse.usage.prompt_tokens = 10
    reponse.usage.completion_tokens = 20
    reponse.usage.total_tokens = 30
    return reponse


def _arguments(create: AsyncMock) -> Mapping[str, Any]:
    """Arguments nommés du dernier appel à chat.completions.create."""
    assert create.await_args is not None, "chat.completions.create n'a pas été appelé"
    return create.await_args.kwargs


def _erreur_groq(statut: int) -> APIStatusError:
    requete = httpx.Request("POST", "https://api.groq.com/openai/v1/chat/completions")
    return APIStatusError("erreur", response=httpx.Response(statut, request=requete), body=None)


class TestLLMServiceSingleton:
    def test_get_llm_service_returns_singleton(self) -> None:
        s1 = get_llm_service()
        s2 = get_llm_service()
        assert s1 is s2


class TestLLMServiceComplete:
    @pytest.mark.asyncio
    async def test_complete_success(self) -> None:
        service = LLMService()
        with patch.object(
            service.client.chat.completions, "create", new_callable=AsyncMock, return_value=_reponse_groq()
        ) as create:
            result = await service.complete(
                system_prompt="Test",
                messages=[{"role": "user", "content": "Bonjour"}],
            )

        assert result["content"] == "Reponse simulee"
        assert result["usage"].prompt_tokens == 10
        assert result["usage"].completion_tokens == 20
        assert result["usage"].total_tokens == 30
        assert result["model"] == "llama-3.3-70b-versatile"
        envoye = _arguments(create)
        assert envoye["model"] == "llama-3.3-70b-versatile"
        assert envoye["response_format"] is omit

    @pytest.mark.asyncio
    async def test_system_prompt_first_then_history_in_order(self) -> None:
        service = LLMService()
        historique = [
            {"role": "user", "content": "Bonjour"},
            {"role": "assistant", "content": "Salut"},
            {"role": "user", "content": "Et mes projets ?"},
        ]
        with patch.object(
            service.client.chat.completions, "create", new_callable=AsyncMock, return_value=_reponse_groq()
        ) as create:
            await service.complete(system_prompt="Tu es un assistant", messages=historique)

        assert _arguments(create)["messages"] == [
            {"role": "system", "content": "Tu es un assistant"},
            *historique,
        ]

    @pytest.mark.asyncio
    async def test_json_response_format_and_parameters_forwarded(self) -> None:
        service = LLMService()
        with patch.object(
            service.client.chat.completions, "create", new_callable=AsyncMock, return_value=_reponse_groq("{}")
        ) as create:
            await service.complete(
                system_prompt="Test",
                messages=[{"role": "user", "content": "Analyse"}],
                temperature=0.2,
                max_tokens=500,
                response_format={"type": "json_object"},
            )

        envoye = _arguments(create)
        assert envoye["response_format"] == {"type": "json_object"}
        assert envoye["temperature"] == 0.2
        assert envoye["max_tokens"] == 500

    @pytest.mark.asyncio
    async def test_empty_content_becomes_empty_string(self) -> None:
        service = LLMService()
        reponse = _reponse_groq()
        reponse.choices[0].message.content = None
        with patch.object(service.client.chat.completions, "create", new_callable=AsyncMock, return_value=reponse):
            result = await service.complete(system_prompt="Test", messages=[])
        assert result["content"] == ""

    @pytest.mark.asyncio
    async def test_invalid_key_raises_unauthorized(self) -> None:
        service = LLMService()
        with patch.object(
            service.client.chat.completions, "create", new_callable=AsyncMock, side_effect=_erreur_groq(401)
        ):
            with pytest.raises(UnauthorizedError):
                await service.complete(system_prompt="Test", messages=[])

    @pytest.mark.asyncio
    async def test_rate_limit_raises_quota_error(self) -> None:
        service = LLMService()
        with patch.object(
            service.client.chat.completions, "create", new_callable=AsyncMock, side_effect=_erreur_groq(429)
        ):
            with pytest.raises(LLMQuotaError):
                await service.complete(system_prompt="Test", messages=[])

    @pytest.mark.asyncio
    async def test_other_api_error_raises_llm_error(self) -> None:
        service = LLMService()
        with patch.object(
            service.client.chat.completions, "create", new_callable=AsyncMock, side_effect=_erreur_groq(500)
        ):
            with pytest.raises(LLMError):
                await service.complete(system_prompt="Test", messages=[])

    @pytest.mark.asyncio
    async def test_unexpected_error_raises_llm_error(self) -> None:
        service = LLMService()
        with patch.object(
            service.client.chat.completions, "create", new_callable=AsyncMock, side_effect=RuntimeError("boom")
        ):
            with pytest.raises(LLMError):
                await service.complete(system_prompt="Test", messages=[])


class TestLLMServiceStream:
    @pytest.mark.asyncio
    async def test_stream_yields_non_empty_deltas(self) -> None:
        service = LLMService()

        def morceau(texte: str | None) -> MagicMock:
            m = MagicMock()
            m.choices = [MagicMock()]
            m.choices[0].delta.content = texte
            return m

        async def flux():
            for texte in ["Bon", None, "jour"]:
                yield morceau(texte)

        with patch.object(service.client.chat.completions, "create", new_callable=AsyncMock, return_value=flux()):
            morceaux = [m async for m in service.stream_complete(system_prompt="Test", messages=[])]

        assert morceaux == ["Bon", "jour"]
