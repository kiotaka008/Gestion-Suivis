from typing import Any

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
    """
    Service LLM abstrait (Google Gemini via le SDK google-genai).

    Encapsule l'appel au fournisseur LLM.
    Permet de changer de fournisseur sans modifier les routes.
    """

    def __init__(self) -> None:
        self._settings = get_settings()
        self._client: genai.Client | None = None

    @property
    def client(self) -> genai.Client:
        """Lazy-loading du client Gemini."""
        if self._client is None:
            self._client = genai.Client(api_key=self._settings.gemini_api_key)
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
        """
        Appel générique au LLM Gemini.

        Args:
            system_prompt : prompt système
            messages : liste de {"role": "...", "content": "..."}
            temperature : créativité (0-2)
            max_tokens : limite de tokens en sortie
            response_format : {"type": "json_object"} pour forcer JSON

        Returns:
            {"content": str, "usage": TokenUsage, "model": str}
        """
        settings = self._settings

        # Configuration de la génération
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

        # Convertir les messages au format Gemini
        contents = self._convert_messages(messages)

        try:
            logger.info(
                f"Appel Gemini : modèle={settings.gemini_model}, "
                f"messages={len(messages)}"
            )

            response = await self.client.aio.models.generate_content(
                model=settings.gemini_model,
                contents=contents,
                config=config,
            )

            content = response.text if hasattr(response, "text") else ""

            # Extraction des tokens
            usage = TokenUsage()
            if hasattr(response, "usage_metadata") and response.usage_metadata:
                meta = response.usage_metadata
                usage = TokenUsage(
                    prompt_tokens=getattr(meta, "prompt_token_count", 0) or 0,
                    completion_tokens=getattr(meta, "candidates_token_count", 0) or 0,
                    total_tokens=getattr(meta, "total_token_count", 0) or 0,
                )

            logger.info(f"Réponse Gemini reçue : {usage.total_tokens} tokens")

            return {
                "content": content or "",
                "usage": usage,
                "model": settings.gemini_model,
            }

        except genai_errors.ClientError as e:
            # 4xx : erreur côté client (clé invalide, requête invalide)
            status_code = getattr(e, "code", None) or 400
            msg = str(e)

            if status_code == 401 or "API_KEY_INVALID" in msg or "API key not valid" in msg:
                logger.error(f"Clé Gemini invalide : {msg}")
                raise UnauthorizedError("Clé API Gemini invalide ou expirée.")

            if status_code == 429:
                logger.warning(f"Quota Gemini dépassé : {msg}")
                raise LLMQuotaError(
                    "Quota Gemini dépassé. Veuillez réessayer dans une minute."
                )

            logger.error(f"Erreur client Gemini ({status_code}) : {msg}")
            raise LLMError(f"Requête invalide envoyée au LLM : {msg}")

        except genai_errors.ServerError as e:
            logger.error(f"Erreur serveur Gemini : {e}")
            raise LLMError("Erreur du service LLM. Veuillez réessayer.")

        except genai_errors.APIError as e:
            logger.error(f"Erreur API Gemini : {e}")
            raise LLMError("Erreur lors de l'appel au service LLM.")

        except TimeoutError:
            logger.warning("Timeout Gemini")
            raise LLMTimeoutError("Le service IA n'a pas répondu à temps.")

        except Exception as e:
            logger.error(f"Erreur inattendue LLM : {type(e).__name__} — {e}")
            raise LLMError("Erreur inattendue du service LLM.")

    @staticmethod
    def _convert_messages(messages: list[dict[str, str]]) -> list[dict[str, Any]]:
        """
        Convertit les messages au format attendu par Gemini.
        - "system" → ignoré (passé via system_instruction)
        - "assistant" → "model"
        - "user" → "user"
        """
        contents: list[dict[str, Any]] = []

        for msg in messages:
            role = msg.get("role", "user")
            content = msg.get("content", "")

            if role == "system":
                continue

            if role == "assistant":
                role = "model"

            contents.append({
                "role": role,
                "parts": [{"text": content}],
            })

        return contents


# Singleton
_llm_service: LLMService | None = None


def get_llm_service() -> LLMService:
    """Retourne l'instance unique du service LLM."""
    global _llm_service
    if _llm_service is None:
        _llm_service = LLMService()
    return _llm_service