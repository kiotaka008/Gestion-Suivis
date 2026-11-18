from typing import Any, AsyncIterator, cast

from groq import APIConnectionError, APIStatusError, APITimeoutError, AsyncGroq, omit
from groq import APIError as GroqAPIError
from groq.types.chat import ChatCompletionMessageParam
from groq.types.chat.completion_create_params import ResponseFormat

from app.config import get_settings
from app.core.exceptions import LLMError, LLMQuotaError, LLMTimeoutError
from app.models.schemas import AITaskType, TokenUsage
from app.services.mock_llm import MODELE_SIMULE, USAGE_SIMULE, contenu_simule, tache_depuis_prompt
from app.utils.logger import get_logger

logger = get_logger("llm_service")

TIMEOUT_SECONDES = 30.0
MAX_TENTATIVES = 2


class LLMService:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._client: AsyncGroq | None = None

    @property
    def client(self) -> AsyncGroq:
        if self._client is None:
            if not self._settings.groq_api_key:
                logger.error("GROQ_API_KEY absente : service LLM non configuré.")
                raise LLMError("Le service IA est momentanément indisponible.")
            self._client = AsyncGroq(
                api_key=self._settings.groq_api_key,
                timeout=TIMEOUT_SECONDES,
                max_retries=MAX_TENTATIVES,
            )
        return self._client

    @staticmethod
    def _traduire_erreur(e: Exception) -> Exception:
        """
        Convertit une erreur Groq en erreur du service.
        Le détail technique va dans les logs, jamais vers le client.
        """
        if isinstance(e, APITimeoutError):
            logger.error("Groq : délai dépassé.")
            return LLMTimeoutError("Le service IA a mis trop de temps à répondre.")
        if isinstance(e, APIConnectionError):
            logger.error(f"Groq : connexion impossible — {e}")
            return LLMError("Le service IA est momentanément indisponible.")
        if isinstance(e, APIStatusError):
            status = e.status_code
            if status == 429:
                logger.warning("Groq : quota ou limite de débit atteint.")
                return LLMQuotaError("Le service IA est très sollicité. Veuillez réessayer.")
            if status in (401, 403):
                logger.error("Groq : clé API refusée (invalide, révoquée ou sans accès).")
            elif status == 404:
                logger.error("Groq : modèle introuvable (vérifier GROQ_MODEL).")
            else:
                logger.error(f"Groq : erreur HTTP {status} — {e}")
            return LLMError("Le service IA est momentanément indisponible.")
        if isinstance(e, GroqAPIError):
            logger.error(f"Groq : erreur API — {e}")
            return LLMError("Le service IA est momentanément indisponible.")
        logger.error(f"Erreur inattendue LLM : {type(e).__name__} — {e}")
        return LLMError("Erreur inattendue du service IA.")

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
                response_format=(
                    cast(ResponseFormat, response_format) if response_format is not None else omit
                ),
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

        except LLMError:
            raise
        except Exception as e:
            raise self._traduire_erreur(e) from e

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

        except LLMError:
            raise
        except Exception as e:
            raise self._traduire_erreur(e) from e

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