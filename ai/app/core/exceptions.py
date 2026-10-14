from typing import Any


class AIServiceError(Exception):
    """Exception de base pour le service IA."""

    status_code: int = 500
    error_code: str = "AI_SERVICE_ERROR"

    def __init__(
        self,
        message: str,
        *,
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.details = details or {}


class ValidationError(AIServiceError):
    """Erreur de validation des entrées."""

    status_code = 422
    error_code = "VALIDATION_ERROR"


class UnauthorizedError(AIServiceError):
    """Clé API interne invalide ou manquante."""

    status_code = 401
    error_code = "UNAUTHORIZED"


class ForbiddenError(AIServiceError):
    """Accès refusé."""

    status_code = 403
    error_code = "FORBIDDEN"


class NotFoundError(AIServiceError):
    """Ressource introuvable."""

    status_code = 404
    error_code = "NOT_FOUND"


class RateLimitError(AIServiceError):
    """Trop de requêtes."""

    status_code = 429
    error_code = "RATE_LIMITED"


class LLMError(AIServiceError):
    """Erreur lors de l'appel au LLM."""

    status_code = 502
    error_code = "LLM_ERROR"


class LLMTimeoutError(LLMError):
    """Le LLM n'a pas répondu dans le délai imparti."""

    error_code = "LLM_TIMEOUT"


class LLMQuotaError(LLMError):
    """Quota OpenAI dépassé."""

    status_code = 402
    error_code = "LLM_QUOTA_EXCEEDED"


class PromptInjectionError(AIServiceError):
    """Tentative de prompt injection détectée."""

    status_code = 400
    error_code = "PROMPT_INJECTION_DETECTED"