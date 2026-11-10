"""Réponses simulées du LLM pour le mode démo (USE_MOCK=true).

Le mode démo permet de faire tourner toute l'application sans clé Groq : chaque tâche
reçoit une réponse au format attendu par sa route (texte pour le chat, JSON pour les autres).
"""

import json

from app.models.schemas import AITaskType, TokenUsage
from app.services.prompt_service import SYSTEM_PROMPTS

MODELE_SIMULE = "mock-llm"
USAGE_SIMULE = TokenUsage(prompt_tokens=50, completion_tokens=100, total_tokens=150)

_REPONSES: dict[AITaskType, object] = {
    AITaskType.CHAT: (
        "Réponse de démonstration (mode USE_MOCK) : le service IA fonctionne, "
        "mais aucune clé Groq n'est configurée. Ajoutez GROQ_API_KEY pour obtenir "
        "de vraies réponses."
    ),
    AITaskType.SUMMARIZE: {
        "summary": "Résumé de démonstration (mode USE_MOCK).",
        "key_points": ["Point clé de démonstration 1", "Point clé de démonstration 2"],
    },
    AITaskType.ANALYZE_RISKS: {
        "overall_risk": "medium",
        "risks": [
            {
                "level": "medium",
                "title": "Retard possible sur les échéances",
                "description": "Analyse de démonstration (mode USE_MOCK) : certaines "
                "activités approchent de leur date limite.",
                "recommendation": "Prioriser les activités en retard et revoir le planning.",
            },
            {
                "level": "low",
                "title": "Charge de l'équipe",
                "description": "Analyse de démonstration : répartition des tâches à surveiller.",
                "recommendation": "Vérifier la charge de chaque membre chaque semaine.",
            },
        ],
        "summary": "Analyse de risques de démonstration (mode USE_MOCK).",
    },
    AITaskType.GENERATE_REPORT: {
        "title": "Rapport de démonstration",
        "content": "Rapport généré en mode démo (USE_MOCK) : aucune clé Groq n'est configurée.",
        "sections": {
            "Avancement": "Section de démonstration.",
            "Points d'attention": "Section de démonstration.",
        },
    },
    AITaskType.CLASSIFY: {
        "confidence": 0.5,
        "reasoning": "Classification de démonstration (mode USE_MOCK).",
    },
    AITaskType.EXTRACT: {"extracted": {}},
}


def tache_depuis_prompt(system_prompt: str) -> AITaskType:
    """Retrouve la tâche à partir de son prompt système (chat par défaut)."""
    for tache, prompt in SYSTEM_PROMPTS.items():
        if prompt == system_prompt:
            return tache
    return AITaskType.CHAT


def contenu_simule(tache: AITaskType) -> str:
    """Contenu simulé : texte pour le chat, JSON pour les tâches structurées."""
    reponse = _REPONSES.get(tache, _REPONSES[AITaskType.CHAT])
    if isinstance(reponse, str):
        return reponse
    return json.dumps(reponse, ensure_ascii=False)
