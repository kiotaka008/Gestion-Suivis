from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import (
    analyze,
    chat,
    classify,
    extract,
    health,
    report,
    summarize,
)
from app.config import get_settings
from app.core.error_handlers import register_error_handlers
from app.utils.logger import setup_logger

logger = setup_logger("main")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """
    Gestion du cycle de vie de l'application.
    - startup : initialisation
    - shutdown : nettoyage
    """
    settings = get_settings()
    logger.info(f"Démarrage du service IA en mode {settings.environment}")
    logger.info(f"Modèle LLM par défaut : {settings.gemini_model}")

    yield

    logger.info("Arrêt du service IA")


def create_app() -> FastAPI:
    """
    Factory de l'application FastAPI.
    """
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

    # ─── Gestionnaires d'erreurs ──────────────────────────
    register_error_handlers(app)

    # ─── Routes ───────────────────────────────────────────
    app.include_router(health.router)
    app.include_router(chat.router)
    app.include_router(summarize.router)
    app.include_router(analyze.router)
    app.include_router(report.router)
    app.include_router(classify.router)
    app.include_router(extract.router)

    return app


app = create_app()