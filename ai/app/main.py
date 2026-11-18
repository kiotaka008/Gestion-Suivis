from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes import (
    analyze,
    chat,
    chat_stream,
    classify,
    conversations,
    extract,
    health,
    report,
    summarize,
)
from app.config import get_settings
from app.core.error_handlers import register_error_handlers
from app.core.rate_limit import rate_limit_middleware
from app.utils.logger import setup_logger

logger = setup_logger("main")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Cycle de vie : démarrage et arrêt."""
    settings = get_settings()
    logger.info(f"Démarrage du service IA en mode {settings.environment}")
    logger.info(f"Modèle LLM (Groq) : {settings.groq_model}")

    yield

    logger.info("Arrêt du service IA")


def create_app() -> FastAPI:
    """Factory de l'application FastAPI."""
    settings = get_settings()

    app = FastAPI(
        title="AI Service — Gestion Suivis",
        description=(
            "Service IA interne. "
            "Ce service est appelé UNIQUEMENT par le backend principal. "
            "Il ne doit JAMAIS être exposé publiquement."
        ),
        version="0.1.0",
        docs_url="/docs" if not settings.is_production else None,
        redoc_url="/redoc" if not settings.is_production else None,
        lifespan=lifespan,
    )

    # ─── CORS ─────────────────────────────────────────────
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # ─── Limitation de débit ──────────────────────────────
    # Une HTTPException levée dans un middleware n'est pas gérée par
    # FastAPI (elle donne un 500) : on la convertit nous-mêmes en réponse.
    @app.middleware("http")
    async def rate_limit_mw(request, call_next):
        try:
            await rate_limit_middleware(request)
        except HTTPException as exc:
            return JSONResponse(
                status_code=exc.status_code,
                content={"detail": exc.detail},
                headers=exc.headers,
            )
        return await call_next(request)

    # ─── Gestionnaires d'erreurs ──────────────────────────
    register_error_handlers(app)

    # ─── Routes ───────────────────────────────────────────
    app.include_router(health.router)
    app.include_router(chat.router)
    app.include_router(chat_stream.router)
    app.include_router(conversations.router)
    app.include_router(summarize.router)
    app.include_router(analyze.router)
    app.include_router(report.router)
    app.include_router(classify.router)
    app.include_router(extract.router)

    return app


app = create_app()