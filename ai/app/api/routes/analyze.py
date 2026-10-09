import json

from fastapi import APIRouter, Depends

from app.core.exceptions import LLMError
from app.core.security import verify_internal_api_key
from app.models.schemas import (
    AITaskType,
    AnalyzeRisksRequest,
    AnalyzeRisksResponse,
    RiskItem,
)
from app.services.llm_service import get_llm_service
from app.services.prompt_service import SYSTEM_PROMPTS, build_analyze_risks_prompt
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai",
    tags=["AI"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.analyze")


@router.post(
    "/analyze-risks",
    response_model=AnalyzeRisksResponse,
    summary="Analyser les risques d'un projet",
    description="Identifie les risques d'un projet et propose des recommandations.",
)
async def analyze_risks(request: AnalyzeRisksRequest) -> AnalyzeRisksResponse:
    """Endpoint d'analyse de risques."""
    logger.info(f"Analyse demandée pour le projet : {request.project_name}")

    llm = get_llm_service()

    result = await llm.complete(
        system_prompt=SYSTEM_PROMPTS[AITaskType.ANALYZE_RISKS],
        messages=[
            {"role": "user", "content": build_analyze_risks_prompt(request)}
        ],
        response_format={"type": "json_object"},
    )

    try:
        parsed = json.loads(result["content"])
    except json.JSONDecodeError:
        logger.error("Réponse LLM non JSON pour analyze_risks")
        raise LLMError("Réponse invalide du LLM.")

    risks = [
        RiskItem(
            level=r.get("level", "medium"),
            title=r.get("title", ""),
            description=r.get("description", ""),
            recommendation=r.get("recommendation"),
        )
        for r in parsed.get("risks", [])
    ]

    return AnalyzeRisksResponse(
        overall_risk=parsed.get("overall_risk", "medium"),
        risks=risks,
        summary=parsed.get("summary", ""),
        usage=result["usage"],
        model=result["model"],
    )