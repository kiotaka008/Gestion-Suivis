import json

from fastapi import APIRouter, Depends

from app.core.exceptions import LLMError
from app.core.security import verify_internal_api_key
from app.models.schemas import (
    AITaskType,
    ClassifyRequest,
    ClassifyResponse,
)
from app.services.llm_service import get_llm_service
from app.services.prompt_service import SYSTEM_PROMPTS, build_classify_prompt
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai",
    tags=["AI"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.classify")


@router.post(
    "/classify",
    response_model=ClassifyResponse,
    summary="Classifier un texte",
    description="Classe un texte dans l'une des catégories fournies.",
)
async def classify(request: ClassifyRequest) -> ClassifyResponse:
    """Endpoint de classification."""
    logger.info(f"Classification demandée : {len(request.categories)} catégories")

    llm = get_llm_service()

    result = await llm.complete(
        system_prompt=SYSTEM_PROMPTS[AITaskType.CLASSIFY],
        messages=[
            {"role": "user", "content": build_classify_prompt(request)}
        ],
        response_format={"type": "json_object"},
    )

    try:
        parsed = json.loads(result["content"])
    except json.JSONDecodeError:
        logger.error("Réponse LLM non JSON pour classify")
        raise LLMError("Réponse invalide du LLM.")

    return ClassifyResponse(
        category=parsed.get("category", request.categories[0]),
        confidence=float(parsed.get("confidence", 0.0)),
        reasoning=parsed.get("reasoning"),
        usage=result["usage"],
        model=result["model"],
    )