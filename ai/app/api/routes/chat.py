from fastapi import APIRouter, Depends

from app.core.prompt_guard import check_messages_safety, sanitize_output
from app.core.security import verify_internal_api_key
from app.models.schemas import (
    AITaskType,
    ChatMessage,
    ChatRequest,
    ChatResponse,
    MessageRole,
)
from app.services.conversation_service import get_conversation_service
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
    """Endpoint de chat conversationnel avec persistance."""
    logger.info(
        f"Chat demandé : {len(request.messages)} message(s), "
        f"user={request.user_id or 'anonyme'}, "
        f"conversation={request.conversation_id or 'nouvelle'}"
    )

    messages_dict = [
        {"role": msg.role.value, "content": msg.content}
        for msg in request.messages
    ]
    check_messages_safety(messages_dict)

    conv_service = get_conversation_service()
    conversation = conv_service.get_or_create(
        conversation_id=request.conversation_id,
        user_id=request.user_id,
    )

    for msg in request.messages:
        if msg.role == MessageRole.USER:
            conv_service.add_message(
                conversation_id=conversation.id,
                role="user",
                content=msg.content,
            )

    llm = get_llm_service()

    # En mode démo (USE_MOCK=true), LLMService renvoie lui-même une réponse simulée,
    # identique pour toutes les routes (voir app/services/mock_llm.py).
    result = await llm.complete(
        system_prompt=SYSTEM_PROMPTS[AITaskType.CHAT],
        messages=messages_dict,
        temperature=request.temperature,
    )
    assistant_content = sanitize_output(result["content"])
    usage = result["usage"]
    model = result["model"]

    conv_service.add_message(
        conversation_id=conversation.id,
        role="assistant",
        content=assistant_content,
    )

    return ChatResponse(
        message=ChatMessage(
            role=MessageRole.ASSISTANT,
            content=assistant_content,
        ),
        conversation_id=conversation.id,
        usage=usage,
        model=model,
    )