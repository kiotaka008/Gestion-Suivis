from datetime import datetime, timezone

from fastapi import APIRouter

from app.config import get_settings

router = APIRouter(tags=["health"])


@router.get(
    "/health",
    summary="Vérification de l'état du service",
    description="Retourne l'état de santé du service IA.",
)
async def health_check() -> dict:
    """
    Endpoint de santé utilisé par le backend principal
    pour vérifier que le service IA est opérationnel.
    """
    settings = get_settings()

    return {
        "status": "ok",
        "service": "ai-service",
        "version": "0.1.0",
        "environment": settings.environment,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }