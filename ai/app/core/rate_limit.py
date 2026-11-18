"""
Rate limiting simple en mémoire.

Note production : pour un déploiement multi-instances (plusieurs workers
ou conteneurs), utiliser Redis ou un service externe : chaque processus
a ici ses propres compteurs.
"""

import re
import secrets
import time
from collections import defaultdict, deque
from threading import Lock

from fastapi import HTTPException, Request, status

from app.config import get_settings
from app.utils.logger import get_logger

logger = get_logger("rate_limit")

# Identifiant utilisateur/organisation transmis par le backend (valeur sûre uniquement).
_ID_VALIDE = re.compile(r"^[A-Za-z0-9._:-]{1,64}$")
_ROUTES_EXEMPTEES = ("/health", "/docs", "/redoc", "/openapi.json")


class RateLimiter:
    """
    Fenêtre glissante de 60 secondes, avec un compteur par :
    - utilisateur (en-tête X-User-Id ou X-Organization-Id) si la clé interne est valide ;
    - backend (clé interne valide, sans identifiant) ;
    - adresse IP (clé absente ou invalide).
    """

    def __init__(self, max_requests_per_minute: int = 30, max_buckets: int = 10_000) -> None:
        self.max_requests = max_requests_per_minute
        self.window_seconds = 60
        self.max_buckets = max_buckets
        self._requests: dict[str, deque[float]] = defaultdict(deque)
        self._lock = Lock()

    def _get_client_key(self, request: Request) -> str:
        api_key = request.headers.get("X-Internal-API-Key")
        attendue = get_settings().internal_api_key

        if api_key and secrets.compare_digest(
            api_key.encode("utf-8"), attendue.encode("utf-8")
        ):
            sujet = request.headers.get("X-User-Id") or request.headers.get("X-Organization-Id")
            if sujet and _ID_VALIDE.match(sujet):
                return f"user:{sujet}"
            return "backend"

        client = request.client
        return f"ip:{client.host if client else 'unknown'}"

    def is_allowed(self, request: Request) -> tuple[bool, int]:
        key = self._get_client_key(request)
        now = time.time()
        cutoff = now - self.window_seconds

        with self._lock:
            # Évite une croissance sans limite de la mémoire.
            if len(self._requests) > self.max_buckets:
                for k in [k for k, b in self._requests.items() if not b or b[-1] < cutoff]:
                    del self._requests[k]

            bucket = self._requests[key]
            while bucket and bucket[0] < cutoff:
                bucket.popleft()

            if len(bucket) >= self.max_requests:
                return False, 0

            bucket.append(now)
            return True, self.max_requests - len(bucket)

    def reset(self) -> None:
        """Vide tous les compteurs (utile pour les tests)."""
        with self._lock:
            self._requests.clear()


_rate_limiter: RateLimiter | None = None


def get_rate_limiter() -> RateLimiter:
    global _rate_limiter
    if _rate_limiter is None:
        settings = get_settings()
        _rate_limiter = RateLimiter(max_requests_per_minute=settings.rate_limit_per_minute)
    return _rate_limiter


async def rate_limit_middleware(request: Request) -> None:
    """Lève une HTTPException 429 si la limite est dépassée (convertie en réponse dans main.py)."""
    if request.url.path in _ROUTES_EXEMPTEES:
        return

    limiter = get_rate_limiter()
    allowed, _remaining = limiter.is_allowed(request)

    if not allowed:
        logger.warning(f"Rate limit depasse pour {limiter._get_client_key(request)}")
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Trop de requetes. Veuillez patienter une minute.",
            headers={
                "Retry-After": "60",
                "X-RateLimit-Limit": str(limiter.max_requests),
                "X-RateLimit-Remaining": "0",
            },
        )