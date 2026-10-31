from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.core.security import verify_internal_api_key
from app.models.conversation import Conversation, ConversationListResponse
from app.services.conversation_service import get_conversation_service
from app.utils.logger import get_logger

router = APIRouter(
    prefix="/api/ai/conversations",
    tags=["Conversations"],
    dependencies=[Depends(verify_internal_api_key)],
)

logger = get_logger("routes.conversations")


@router.get("", response_model=ConversationListResponse)
async def list_conversations(
    user_id: str | None = Query(default=None),
    limit: int = Query(default=50, ge=1, le=200),
) -> ConversationListResponse:
    service = get_conversation_service()
    conversations = service.list_by_user(user_id, limit=limit)
    return ConversationListResponse(conversations=conversations, total=len(conversations))


@router.get("/{conversation_id}", response_model=Conversation)
async def get_conversation(conversation_id: str) -> Conversation:
    service = get_conversation_service()
    conversation = service.get(conversation_id)
    if not conversation:
        raise HTTPException(status_code=404, detail="Conversation introuvable.")
    return conversation


@router.delete("/{conversation_id}", status_code=204)
async def delete_conversation(conversation_id: str) -> None:
    service = get_conversation_service()
    if not service.delete(conversation_id):
        raise HTTPException(status_code=404, detail="Conversation introuvable.")
