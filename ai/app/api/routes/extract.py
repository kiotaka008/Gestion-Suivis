import json

from fastapi import APIRouter, Depends

from app.core.exceptions import LLMError
from app.core.security import verify_internal_api_key
from app.models.schemas import (
    AITaskType,
    ExtractRequest,
    ExtractResponse,
)
from app.services.llm_service import get_llm_service
from app.services.prompt_service import SYSTEM_PROMPTS, build_extract_prompt
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai",
    tags=["AI"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.extract")


@router.post(
    "/extract",
    response_model=ExtractResponse,
    summary="Extraire des informations",
    description="Extrait des champs spécifiques d'un texte.",
)
async def extract(request: ExtractRequest) -> ExtractResponse:
    """Endpoint d'extraction."""
    logger.info(f"Extraction demandée : {len(request.fields)} champ(s)")

    llm = get_llm_service()

    result = await llm.complete(
        system_prompt=SYSTEM_PROMPTS[AITaskType.EXTRACT],
        messages=[
            {"role": "user", "content": build_extract_prompt(request)}
        ],
        response_format={"type": "json_object"},
    )

    try:
        parsed = json.loads(result["content"])
    except json.JSONDecodeError:
        logger.error("Réponse LLM non JSON pour extract")
        raise LLMError("Réponse invalide du LLM.")

    return ExtractResponse(
        extracted=parsed.get("extracted", {}),
        usage=result["usage"],
        model=result["model"],
    )