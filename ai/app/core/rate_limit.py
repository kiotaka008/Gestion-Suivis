import time
from collections import defaultdict, deque
from threading import Lock

from fastapi import Request, HTTPException, status

from app.config import get_settings
from app.utils.logger import get_logger

logger = get_logger("rate_limit")


class RateLimiter:
    def __init__(self, max_requests_per_minute: int = 30) -> None:
        self.max_requests = max_requests_per_minute
        self.window_seconds = 60
        self._requests: dict[str, deque[float]] = defaultdict(deque)
        self._lock = Lock()

    def _get_client_key(self, request: Request) -> str:
        api_key = request.headers.get("X-Internal-API-Key")
        if api_key:
            return f"key:{hash(api_key) & 0xFFFFFFFF:x}"
        client = request.client
        return f"ip:{client.host if client else 'unknown'}"

    def is_allowed(self, request: Request) -> tuple[bool, int]:
        key = self._get_client_key(request)
        now = time.time()
        cutoff = now - self.window_seconds

        with self._lock:
            bucket = self._requests[key]
            while bucket and bucket[0] < cutoff:
                bucket.popleft()

            if len(bucket) >= self.max_requests:
                return False, 0

            bucket.append(now)
            return True, self.max_requests - len(bucket)

    def reset(self) -> None:
        with self._lock:
            self._requests.clear()


_rate_limiter: RateLimiter | None = None


def get_rate_limiter() -> RateLimiter:
    global _rate_limiter
    if _rate_limiter is None:
        settings = get_settings()
        _rate_limiter = RateLimiter(
            max_requests_per_minute=settings.rate_limit_per_minute
        )
    return _rate_limiter


async def rate_limit_middleware(request: Request) -> None:
    if request.url.path in ("/health", "/docs", "/redoc", "/openapi.json"):
        return

    limiter = get_rate_limiter()
    allowed, remaining = limiter.is_allowed(request)

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
