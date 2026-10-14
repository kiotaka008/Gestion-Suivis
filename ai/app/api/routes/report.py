import json

from fastapi import APIRouter, Depends

from app.core.exceptions import LLMError
from app.core.security import verify_internal_api_key
from app.models.schemas import (
    AITaskType,
    GenerateReportRequest,
    GenerateReportResponse,
)
from app.services.llm_service import get_llm_service
from app.services.prompt_service import SYSTEM_PROMPTS, build_generate_report_prompt
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai",
    tags=["AI"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.report")


@router.post(
    "/generate-report",
    response_model=GenerateReportResponse,
    summary="Générer un rapport structuré",
    description="Génère un rapport basé sur les données fournies.",
)
async def generate_report(request: GenerateReportRequest) -> GenerateReportResponse:
    """Endpoint de génération de rapport."""
    logger.info(f"Rapport demandé : type={request.report_type}")

    llm = get_llm_service()

    result = await llm.complete(
        system_prompt=SYSTEM_PROMPTS[AITaskType.GENERATE_REPORT],
        messages=[
            {"role": "user", "content": build_generate_report_prompt(request)}
        ],
        response_format={"type": "json_object"},
    )

    try:
        parsed = json.loads(result["content"])
    except json.JSONDecodeError:
        logger.error("Réponse LLM non JSON pour generate_report")
        raise LLMError("Réponse invalide du LLM.")

    return GenerateReportResponse(
        title=parsed.get("title", "Rapport"),
        content=parsed.get("content", ""),
        sections=parsed.get("sections", {}),
        usage=result["usage"],
        model=result["model"],
    )