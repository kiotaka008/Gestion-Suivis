from typing import Any, AsyncIterator

from google import genai
from google.genai import types as genai_types
from google.genai import errors as genai_errors

from app.config import get_settings
from app.core.exceptions import (
    LLMError,
    LLMQuotaError,
    LLMTimeoutError,
    UnauthorizedError,
)
from app.models.schemas import TokenUsage
from app.utils.logger import get_logger

logger = get_logger("llm_service")


class LLMService:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._client: genai.Client | None = None

    @property
    def client(self) -> genai.Client:
        if self._client is None:
            self._client = genai.Client(api_key=self._settings.gemini_api_key)
        return self._client

    def _ensure_configured(self) -> None:
        pass

    async def complete(
        self,
        *,
        system_prompt: str,
        messages: list[dict[str, str]],
        temperature: float | None = None,
        max_tokens: int | None = None,
        response_format: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        settings = self._settings

        config_kwargs: dict[str, Any] = {
            "temperature": temperature if temperature is not None else settings.gemini_temperature,
            "max_output_tokens": max_tokens or settings.gemini_max_tokens,
        }

        if response_format and response_format.get("type") == "json_object":
            config_kwargs["response_mime_type"] = "application/json"

        config = genai_types.GenerateContentConfig(
            system_instruction=system_prompt,
            **config_kwargs,
        )

        contents = self._convert_messages(messages)

        try:
            logger.info(f"Appel Gemini : modèle={settings.gemini_model}, messages={len(messages)}")
            response = await self.client.aio.models.generate_content(
                model=settings.gemini_model,
                contents=contents,
                config=config,
            )
            content = response.text if hasattr(response, "text") else ""
            usage = TokenUsage()
            if hasattr(response, "usage_metadata") and response.usage_metadata:
                meta = response.usage_metadata
                usage = TokenUsage(
                    prompt_tokens=getattr(meta, "prompt_token_count", 0) or 0,
                    completion_tokens=getattr(meta, "candidates_token_count", 0) or 0,
                    total_tokens=getattr(meta, "total_token_count", 0) or 0,
                )
            return {"content": content or "", "usage": usage, "model": settings.gemini_model}

        except genai_errors.ClientError as e:
            status_code = getattr(e, "code", None) or 400
            msg = str(e)
            if status_code == 401 or "API_KEY_INVALID" in msg or "API key not valid" in msg:
                raise UnauthorizedError("Clé API Gemini invalide ou expirée.")
            if status_code == 429:
                raise LLMQuotaError("Quota Gemini dépassé. Veuillez réessayer.")
            raise LLMError(f"Requête invalide envoyée au LLM : {msg}")
        except genai_errors.ServerError:
            raise LLMError("Erreur du service LLM. Veuillez réessayer.")
        except Exception as e:
            logger.error(f"Erreur inattendue LLM : {type(e).__name__} — {e}")
            raise LLMError("Erreur inattendue du service LLM.")

    async def stream_complete(
        self,
        *,
        system_prompt: str,
        messages: list[dict[str, str]],
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> AsyncIterator[str]:
        """Appel streaming au LLM Gemini (yield les chunks)."""
        settings = self._settings
        
        config_kwargs: dict[str, Any] = {
            "temperature": temperature if temperature is not None else settings.gemini_temperature,
            "max_output_tokens": max_tokens or settings.gemini_max_tokens,
        }

        config = genai_types.GenerateContentConfig(
            system_instruction=system_prompt,
            **config_kwargs,
        )

        contents = self._convert_messages(messages)

        try:
            logger.info(f"Appel Gemini STREAM : modèle={settings.gemini_model}")
            response = await self.client.aio.models.generate_content_stream(
                model=settings.gemini_model,
                contents=contents,
                config=config,
            )
            
            async for chunk in response:
                if chunk.text:
                    yield chunk.text

        except Exception as e:
            logger.error(f"Erreur streaming LLM : {type(e).__name__} — {e}")
            raise LLMError("Erreur lors du streaming LLM.")

    @staticmethod
    def _convert_messages(messages: list[dict[str, str]]) -> list[dict[str, Any]]:
        contents: list[dict[str, Any]] = []
        for msg in messages:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            if role == "system":
                continue
            if role == "assistant":
                role = "model"
            contents.append({"role": role, "parts": [{"text": content}]})
        return contents


_llm_service: LLMService | None = None

def get_llm_service() -> LLMService:
    global _llm_service
    if _llm_service is None:
        _llm_service = LLMService()
    return _llm_service
