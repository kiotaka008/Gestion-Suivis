from datetime import datetime
from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field, field_validator


# ────────────────────────────────────────────────────────
# ENUMS
# ────────────────────────────────────────────────────────

class MessageRole(str, Enum):
    """Rôle d'un message dans une conversation."""
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"


class AITaskType(str, Enum):
    """Types de tâches IA supportées."""
    CHAT = "chat"
    SUMMARIZE = "summarize"
    ANALYZE_RISKS = "analyze_risks"
    GENERATE_REPORT = "generate_report"
    CLASSIFY = "classify"
    EXTRACT = "extract"


# ────────────────────────────────────────────────────────
# SCHÉMAS DE BASE
# ────────────────────────────────────────────────────────

class ChatMessage(BaseModel):
    """Un message dans une conversation."""
    role: MessageRole
    content: str = Field(..., min_length=1, max_length=32_000)

    @field_validator("content")
    @classmethod
    def strip_content(cls, v: str) -> str:
        stripped = v.strip()
        if not stripped:
            raise ValueError("Le contenu du message ne peut pas être vide.")
        return stripped


# ────────────────────────────────────────────────────────
# REQUÊTES
# ────────────────────────────────────────────────────────

class ChatRequest(BaseModel):
    """Requête de chat conversationnel."""
    messages: list[ChatMessage] = Field(
        ...,
        min_length=1,
        max_length=50,
        description="Historique de la conversation",
    )
    user_id: str | None = Field(
        default=None,
        max_length=64,
        description="Identifiant utilisateur (pour traçabilité)",
    )
    conversation_id: str | None = Field(
        default=None,
        max_length=64,
        description="Identifiant de conversation (pour suivi)",
    )
    stream: bool = Field(
        default=False,
        description="Activer le streaming de la réponse",
    )
    temperature: float | None = Field(
        default=None,
        ge=0.0,
        le=2.0,
        description="Créativité du modèle (override)",
    )


class SummarizeRequest(BaseModel):
    """Requête pour résumer un contenu."""
    content: str = Field(..., min_length=10, max_length=50_000)
    context: str | None = Field(
        default=None,
        max_length=2000,
        description="Contexte additionnel (ex: nom du projet)",
    )
    max_words: int = Field(default=150, ge=20, le=1000)
    language: Literal["fr", "en", "mg"] = "fr"


class AnalyzeRisksRequest(BaseModel):
    """Requête d'analyse de risques d'un projet."""
    project_name: str = Field(..., min_length=1, max_length=200)
    project_description: str | None = Field(default=None, max_length=5000)
    activities: list[dict] = Field(
        default_factory=list,
        description="Liste des activités du projet (titre, statut, échéance...)",
    )
    language: Literal["fr", "en", "mg"] = "fr"


class GenerateReportRequest(BaseModel):
    """Requête de génération de rapport structuré."""
    report_type: Literal["weekly", "monthly", "project", "productivity"] = "weekly"
    data: dict = Field(
        ...,
        description="Données brutes à inclure dans le rapport",
    )
    language: Literal["fr", "en", "mg"] = "fr"


class ClassifyRequest(BaseModel):
    """Requête de classification de texte."""
    text: str = Field(..., min_length=1, max_length=5000)
    categories: list[str] = Field(
        ...,
        min_length=2,
        max_length=20,
        description="Catégories possibles",
    )

    @field_validator("categories")
    @classmethod
    def validate_categories(cls, v: list[str]) -> list[str]:
        cleaned = [c.strip() for c in v if c.strip()]
        if len(cleaned) < 2:
            raise ValueError("Au moins 2 catégories valides sont requises.")
        return cleaned


class ExtractRequest(BaseModel):
    """Requête d'extraction d'informations structurées."""
    text: str = Field(..., min_length=10, max_length=20_000)
    fields: list[str] = Field(
        ...,
        min_length=1,
        max_length=20,
        description="Champs à extraire (ex: nom, date, montant)",
    )


# ────────────────────────────────────────────────────────
# RÉPONSES
# ────────────────────────────────────────────────────────

class TokenUsage(BaseModel):
    """Consommation de tokens."""
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class ChatResponse(BaseModel):
    """Réponse de chat."""
    message: ChatMessage
    conversation_id: str | None = None
    usage: TokenUsage | None = None
    model: str
    created_at: datetime = Field(default_factory=datetime.utcnow)


class SummarizeResponse(BaseModel):
    """Réponse de résumé."""
    summary: str
    key_points: list[str] = Field(default_factory=list)
    usage: TokenUsage | None = None
    model: str
    created_at: datetime = Field(default_factory=datetime.utcnow)


class RiskItem(BaseModel):
    """Un risque identifié."""
    level: Literal["low", "medium", "high", "critical"]
    title: str
    description: str
    recommendation: str | None = None


class AnalyzeRisksResponse(BaseModel):
    """Réponse d'analyse de risques."""
    overall_risk: Literal["low", "medium", "high", "critical"]
    risks: list[RiskItem]
    summary: str
    usage: TokenUsage | None = None
    model: str
    created_at: datetime = Field(default_factory=datetime.utcnow)


class GenerateReportResponse(BaseModel):
    """Réponse de génération de rapport."""
    title: str
    content: str
    sections: dict[str, str] = Field(default_factory=dict)
    usage: TokenUsage | None = None
    model: str
    created_at: datetime = Field(default_factory=datetime.utcnow)


class ClassifyResponse(BaseModel):
    """Réponse de classification."""
    category: str
    confidence: float = Field(ge=0.0, le=1.0)
    reasoning: str | None = None
    usage: TokenUsage | None = None
    model: str


class ExtractResponse(BaseModel):
    """Réponse d'extraction."""
    extracted: dict[str, str | None]
    usage: TokenUsage | None = None
    model: str


# ────────────────────────────────────────────────────────
# ERREURS
# ────────────────────────────────────────────────────────

class ErrorDetail(BaseModel):
    """Détail d'une erreur."""
    code: str
    message: str
    details: dict | None = None


class ErrorResponse(BaseModel):
    """Format standard d'erreur."""
    error: ErrorDetail