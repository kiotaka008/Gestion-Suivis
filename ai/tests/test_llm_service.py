from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.core.exceptions import LLMError, UnauthorizedError
from app.services.llm_service import LLMService, get_llm_service


class TestLLMServiceSingleton:
    def test_get_llm_service_returns_singleton(self) -> None:
        s1 = get_llm_service()
        s2 = get_llm_service()
        assert s1 is s2


class TestLLMServiceConvertMessages:
    def test_converts_user_message(self) -> None:
        service = LLMService()
        messages = [{"role": "user", "content": "Bonjour"}]
        result = service._convert_messages(messages)
        assert result == [{"role": "user", "parts": [{"text": "Bonjour"}]}]

    def test_converts_assistant_to_model(self) -> None:
        service = LLMService()
        messages = [{"role": "assistant", "content": "Salut"}]
        result = service._convert_messages(messages)
        assert result == [{"role": "model", "parts": [{"text": "Salut"}]}]

    def test_skips_system_messages(self) -> None:
        service = LLMService()
        messages = [
            {"role": "system", "content": "Tu es un assistant"},
            {"role": "user", "content": "Bonjour"},
        ]
        result = service._convert_messages(messages)
        assert len(result) == 1
        assert result[0]["role"] == "user"


class TestLLMServiceComplete:
    @pytest.mark.asyncio
    async def test_complete_success(self) -> None:
        service = LLMService()

        mock_response = MagicMock()
        mock_response.text = "Reponse simulee"
        mock_response.usage_metadata.prompt_token_count = 10
        mock_response.usage_metadata.candidates_token_count = 20
        mock_response.usage_metadata.total_token_count = 30

        with patch.object(
            service.client.aio.models,
            "generate_content",
            new_callable=AsyncMock,
            return_value=mock_response,
        ):
            result = await service.complete(
                system_prompt="Test",
                messages=[{"role": "user", "content": "Bonjour"}],
            )

        assert result["content"] == "Reponse simulee"
        assert result["usage"].total_tokens == 30
        assert result["model"] == "gemini-3.8-flash"
