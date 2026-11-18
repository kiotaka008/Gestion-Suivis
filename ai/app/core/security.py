import secrets

from fastapi import Header, HTTPException, status

from app.config import get_settings
from app.utils.logger import get_logger

logger = get_logger("security")


async def verify_internal_api_key(
    x_internal_api_key: str | None = Header(
        default=None,
        alias="X-Internal-API-Key",
        description="Clé API interne partagée avec le backend principal",
    ),
) -> None:
    """
    Vérifie la clé API interne envoyée par le backend principal.
    Ce service IA ne doit JAMAIS être appelé directement par le frontend.
    """
    settings = get_settings()

    if not x_internal_api_key:
        logger.warning("Requête sans clé API interne")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Clé API interne manquante.",
        )

    # Comparaison à temps constant (encode : compare_digest refuse les str non ASCII).
    if not secrets.compare_digest(
        x_internal_api_key.encode("utf-8"),
        settings.internal_api_key.encode("utf-8"),
    ):
        logger.warning("Requête avec clé API interne invalide")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Clé API interne invalide.",
        )