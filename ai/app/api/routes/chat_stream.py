import json
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.core.prompt_guard import check_messages_safety, sanitize_output
from app.core.security import verify_internal_api_key
from app.models.schemas import AITaskType, ChatRequest
from app.services.llm_service import get_llm_service
from app.services.prompt_service import SYSTEM_PROMPTS
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai",
    tags=["AI"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.chat_stream")


@router.post(
    "/chat/stream",
    summary="Chat conversationnel avec streaming (SSE)",
    description="Renvoie la réponse mot par mot au format Server-Sent Events.",
)
async def chat_stream(request: ChatRequest):
    logger.info(f"Chat STREAM demandé : {len(request.messages)} message(s)")

    messages_dict = [
        {"role": msg.role.value, "content": msg.content}
        for msg in request.messages
    ]
    check_messages_safety(messages_dict)

    llm = get_llm_service()

    async def event_generator():
        try:
            async for text_chunk in llm.stream_complete(
                system_prompt=SYSTEM_PROMPTS[AITaskType.CHAT],
                messages=messages_dict,
                temperature=request.temperature,
            ):
                clean_chunk = sanitize_output(text_chunk)
                if clean_chunk:
                    data = json.dumps({"chunk": clean_chunk}, ensure_ascii=False)
                    yield f"data: {data}\n\n"
            
            yield "data: [DONE]\n\n"

        except Exception as e:
            logger.error(f"Erreur dans le stream : {e}")
            error_data = json.dumps({"error": "Erreur lors de la génération"})
            yield f"data: {error_data}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
