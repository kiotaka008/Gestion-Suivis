from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.core.exceptions import AIServiceError
from app.utils.logger import get_logger

logger = get_logger("error_handlers")


def register_error_handlers(app: FastAPI) -> None:
    """
    Enregistre les gestionnaires d'erreurs globaux.
    Ne JAMAIS exposer les détails techniques au client.
    """

    @app.exception_handler(AIServiceError)
    async def handle_ai_service_error(request: Request, exc: AIServiceError) -> JSONResponse:
        logger.warning(
            f"AIServiceError sur {request.url.path} : {exc.error_code} — {exc.message}"
        )
        return JSONResponse(
            status_code=exc.status_code,
            content={
                "error": {
                    "code": exc.error_code,
                    "message": exc.message,
                    "details": exc.details,
                }
            },
        )

    @app.exception_handler(Exception)
    async def handle_unexpected_error(request: Request, exc: Exception) -> JSONResponse:
        logger.error(
            f"Erreur inattendue sur {request.url.path} : {type(exc).__name__}",
            exc_info=True,
        )
        return JSONResponse(
            status_code=500,
            content={
                "error": {
                    "code": "INTERNAL_SERVER_ERROR",
                    "message": "Une erreur interne est survenue.",
                }
            },
        )