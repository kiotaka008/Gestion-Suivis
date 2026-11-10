from typing import Any, AsyncIterator, cast

from groq import AsyncGroq, omit
from groq import APIError as GroqAPIError
from groq.types.chat import ChatCompletionMessageParam
from groq.types.chat.completion_create_params import ResponseFormat

from app.config import get_settings
from app.core.exceptions import (
    LLMError,
    LLMQuotaError,
    LLMTimeoutError,
    UnauthorizedError,
)
from app.models.schemas import AITaskType, TokenUsage
from app.services.mock_llm import MODELE_SIMULE, USAGE_SIMULE, contenu_simule, tache_depuis_prompt
from app.utils.logger import get_logger

logger = get_logger("llm_service")


class LLMService:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._client: AsyncGroq | None = None

    @property
    def client(self) -> AsyncGroq:
        if self._client is None:
            self._client = AsyncGroq(api_key=self._settings.groq_api_key)
        return self._client

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

        # Mode démo : réponse simulée au format de la tâche, sans appel à Groq.
        if settings.use_mock:
            tache = tache_depuis_prompt(system_prompt)
            logger.info(f"MODE MOCK — réponse simulée pour la tâche {tache.value}")
            return {"content": contenu_simule(tache), "usage": USAGE_SIMULE, "model": MODELE_SIMULE}

        groq_messages = self._messages_groq(system_prompt, messages)

        try:
            logger.info(f"Appel Groq : modèle={settings.groq_model}, messages={len(messages)}")
            response = await self.client.chat.completions.create(
                model=settings.groq_model,
                messages=groq_messages,
                temperature=temperature if temperature is not None else 0.7,
                max_tokens=max_tokens or 2048,
                # Sans format demandé, le paramètre n'est pas envoyé (et non « null »).
                response_format=cast(ResponseFormat, response_format) if response_format is not None else omit,
            )

            content = response.choices[0].message.content or ""
            usage = TokenUsage()
            if response.usage:
                usage = TokenUsage(
                    prompt_tokens=response.usage.prompt_tokens or 0,
                    completion_tokens=response.usage.completion_tokens or 0,
                    total_tokens=response.usage.total_tokens or 0,
                )

            return {"content": content, "usage": usage, "model": settings.groq_model}

        except GroqAPIError as e:
            status = getattr(e, "status_code", None) or 500
            if status == 401:
                raise UnauthorizedError("Clé API Groq invalide ou expirée.")
            if status == 429:
                raise LLMQuotaError("Quota Groq dépassé. Veuillez réessayer.")
            raise LLMError(f"Erreur Groq : {str(e)}")
        except Exception as e:
            logger.error(f"Erreur inattendue Groq : {type(e).__name__} — {e}")
            raise LLMError("Erreur inattendue du service LLM.")

    async def stream_complete(
        self,
        *,
        system_prompt: str,
        messages: list[dict[str, str]],
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> AsyncIterator[str]:
        settings = self._settings

        # Mode démo : la réponse simulée du chat est envoyée mot par mot.
        if settings.use_mock:
            logger.info("MODE MOCK — flux simulé")
            mots = contenu_simule(AITaskType.CHAT).split(" ")
            for i, mot in enumerate(mots):
                yield mot if i == 0 else " " + mot
            return

        groq_messages = self._messages_groq(system_prompt, messages)

        try:
            logger.info(f"Appel Groq STREAM : modèle={settings.groq_model}")
            stream = await self.client.chat.completions.create(
                model=settings.groq_model,
                messages=groq_messages,
                temperature=temperature if temperature is not None else 0.7,
                max_tokens=max_tokens or 2048,
                stream=True,
            )

            async for chunk in stream:
                if chunk.choices and chunk.choices[0].delta.content:
                    yield chunk.choices[0].delta.content

        except Exception as e:
            logger.error(f"Erreur streaming Groq : {type(e).__name__} — {e}")
            raise LLMError("Erreur lors du streaming LLM.")

    @staticmethod
    def _messages_groq(
        system_prompt: str, messages: list[dict[str, str]]
    ) -> list[ChatCompletionMessageParam]:
        """Prompt système en tête, puis l'historique (rôles user/assistant) dans l'ordre."""
        return cast(
            list[ChatCompletionMessageParam],
            [{"role": "system", "content": system_prompt}, *messages],
        )


_llm_service: LLMService | None = None

def get_llm_service() -> LLMService:
    global _llm_service
    if _llm_service is None:
        _llm_service = LLMService()
    return _llm_service