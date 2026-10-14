from fastapi import APIRouter, Depends

from app.core.security import verify_internal_api_key
from app.models.schemas import (
    ChatMessage,
    ChatRequest,
    ChatResponse,
    MessageRole,
)
from app.services.llm_service import get_llm_service
from app.services.prompt_service import SYSTEM_PROMPTS
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai",
    tags=["AI"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.chat")


@router.post(
    "/chat",
    response_model=ChatResponse,
    summary="Chat conversationnel avec l'IA",
    description="Envoie une conversation et reçoit une réponse de l'assistant IA.",
)
async def chat(request: ChatRequest) -> ChatResponse:
    """Endpoint de chat conversationnel."""
    logger.info(
        f"Chat demandé : {len(request.messages)} message(s), "
        f"user={request.user_id or 'anonyme'}"
    )

    llm = get_llm_service()

    # Construire les messages pour le LLM
    messages = [
        {"role": msg.role.value, "content": msg.content}
        for msg in request.messages
    ]

    result = await llm.complete(
        system_prompt=SYSTEM_PROMPTS[__import__(
            "app.models.schemas", fromlist=["AITaskType"]
        ).AITaskType.CHAT],
        messages=messages,
        temperature=request.temperature,
    )

    return ChatResponse(
        message=ChatMessage(
            role=MessageRole.ASSISTANT,
            content=result["content"],
        ),
        conversation_id=request.conversation_id,
        usage=result["usage"],
        model=result["model"],
    )