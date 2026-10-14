from functools import lru_cache
from typing import Literal

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    Configuration centrale du service IA.
    Toutes les valeurs sont lues depuis les variables d'environnement (.env).
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ─── Environnement ────────────────────────────────────
    environment: Literal["development", "staging", "production"] = "development"
    debug: bool = False
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"

    # ─── Serveur ──────────────────────────────────────────
    host: str = "0.0.0.0"
    port: int = 8000

    # ─── Sécurité interne ─────────────────────────────────
    internal_api_key: str = Field(
        ...,
        min_length=16,
        description="Clé API partagée entre le backend principal et ce service IA",
    )

        # ─── Google Gemini ────────────────────────────────────
    gemini_api_key: str = Field(..., min_length=10)
    gemini_model: str = "gemini-1.5-flash"
    gemini_max_tokens: int = Field(default=2048, ge=1, le=16384)
    gemini_temperature: float = Field(default=0.7, ge=0.0, le=2.0)

    # ─── CORS ─────────────────────────────────────────────
    allowed_origins: str = "http://localhost:3000"

    # ─── Rate limit ───────────────────────────────────────
    rate_limit_per_minute: int = Field(default=30, ge=1, le=1000)

    @field_validator("allowed_origins")
    @classmethod
    def strip_origins(cls, v: str) -> str:
        return v.strip()

    @property
    def allowed_origins_list(self) -> list[str]:
        """Transforme la string CSV en liste."""
        return [o.strip() for o in self.allowed_origins.split(",") if o.strip()]

    @property
    def is_production(self) -> bool:
        return self.environment == "production"


@lru_cache
def get_settings() -> Settings:
    """
    Retourne une instance unique (singleton) de Settings.
    Utilise lru_cache pour ne lire le .env qu'une seule fois.
    """
    return Settings()