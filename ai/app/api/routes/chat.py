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

    # ─── Mode Mock ───
    if llm._settings.use_mock:
        logger.info("MODE MOCK ACTIVÉ — Réponse simulée pour /api/ai/chat")
        assistant_content = (
            "Voici les principaux risques d'un projet de refonte de site web :\n\n"
            "1. Dépassement de budget : Les refontes dépassent souvent le budget initial de 20 à 30%.\n"
            "2. Retard de livraison : La coordination entre design et développement peut prendre plus de temps que prévu.\n"
            "3. Perte de trafic : Une mauvaise redirection d'URL peut impacter le référencement (SEO).\n"
            "4. Résistance au changement : Les utilisateurs peuvent être perturbés par la nouvelle interface.\n"
            "5. Problèmes de compatibilité : Le nouveau site doit fonctionner sur tous les navigateurs et appareils."
        )
        from app.models.schemas import TokenUsage
        usage = TokenUsage(prompt_tokens=50, completion_tokens=100, total_tokens=150)
        model = "mock-gemini-3.8-flash"
    else:
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