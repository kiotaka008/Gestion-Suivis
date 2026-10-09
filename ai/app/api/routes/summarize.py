import json

from fastapi import APIRouter, Depends

from app.core.exceptions import LLMError
from app.core.security import verify_internal_api_key
from app.models.schemas import (
    AITaskType,
    SummarizeRequest,
    SummarizeResponse,
)
from app.services.llm_service import get_llm_service
from app.services.prompt_service import SYSTEM_PROMPTS, build_summarize_prompt
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai",
    tags=["AI"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.summarize")


@router.post(
    "/summarize",
    response_model=SummarizeResponse,
    summary="Résumer un contenu",
    description="Résume un texte long en points clés.",
)
async def summarize(request: SummarizeRequest) -> SummarizeResponse:
    """Endpoint de résumé."""
    logger.info(f"Résumé demandé : {len(request.content)} caractères")

    llm = get_llm_service()

    result = await llm.complete(
        system_prompt=SYSTEM_PROMPTS[AITaskType.SUMMARIZE],
        messages=[
            {"role": "user", "content": build_summarize_prompt(request)}
        ],
        response_format={"type": "json_object"},
    )

    try:
        parsed = json.loads(result["content"])
    except json.JSONDecodeError:
        logger.error("Réponse LLM non JSON pour summarize")
        raise LLMError("Réponse invalide du LLM.")

    return SummarizeResponse(
        summary=parsed.get("summary", ""),
        key_points=parsed.get("key_points", []),
        usage=result["usage"],
        model=result["model"],
    )