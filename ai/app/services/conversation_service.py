import uuid
from datetime import datetime, timezone, timedelta
from threading import Lock

from app.models.conversation import Conversation, ConversationSummary
from app.utils.logger import get_logger

logger = get_logger("conversation_service")

CONVERSATION_TTL_HOURS = 24
MAX_CONVERSATIONS = 1000


class ConversationService:
    def __init__(self) -> None:
        self._conversations: dict[str, Conversation] = {}
        self._lock = Lock()

    def _cleanup_expired(self) -> None:
        now = datetime.now(timezone.utc)
        ttl = timedelta(hours=CONVERSATION_TTL_HOURS)
        expired = [cid for cid, conv in self._conversations.items() if now - conv.updated_at > ttl]
        for cid in expired:
            del self._conversations[cid]
        if expired:
            logger.info(f"Nettoyage : {len(expired)} conversation(s) expiree(s)")

    def create(self, user_id: str | None = None, conversation_id: str | None = None) -> Conversation:
        with self._lock:
            self._cleanup_expired()
            if len(self._conversations) >= MAX_CONVERSATIONS:
                oldest_id = min(self._conversations, key=lambda cid: self._conversations[cid].updated_at)
                del self._conversations[oldest_id]
                logger.warning(f"Limite atteinte, suppression de {oldest_id}")
            conv_id = conversation_id or str(uuid.uuid4())
            conversation = Conversation(id=conv_id, user_id=user_id)
            self._conversations[conv_id] = conversation
            logger.info(f"Conversation creee : {conv_id}")
            return conversation

    def get(self, conversation_id: str) -> Conversation | None:
        with self._lock:
            conv = self._conversations.get(conversation_id)
            if conv:
                conv.updated_at = datetime.now(timezone.utc)
            return conv

    def get_or_create(self, conversation_id: str | None, user_id: str | None = None) -> Conversation:
        if conversation_id:
            existing = self.get(conversation_id)
            if existing:
                return existing
            return self.create(user_id=user_id, conversation_id=conversation_id)
        return self.create(user_id=user_id)

    def add_message(self, conversation_id: str, role: str, content: str) -> Conversation | None:
        with self._lock:
            conv = self._conversations.get(conversation_id)
            if conv:
                conv.add_message(role, content)
            return conv

    def list_by_user(self, user_id: str | None, limit: int = 50) -> list[ConversationSummary]:
        with self._lock:
            self._cleanup_expired()
            results = [self._to_summary(conv) for conv in self._conversations.values() if conv.user_id == user_id]
            results.sort(key=lambda x: x.updated_at, reverse=True)
            return results[:limit]

    def delete(self, conversation_id: str) -> bool:
        with self._lock:
            if conversation_id in self._conversations:
                del self._conversations[conversation_id]
                return True
            return False

    def reset(self) -> None:
        with self._lock:
            self._conversations.clear()

    @staticmethod
    def _to_summary(conv: Conversation) -> ConversationSummary:
        preview = ""
        for msg in conv.messages:
            if msg.role == "user":
                preview = msg.content[:100]
                break
        return ConversationSummary(
            id=conv.id,
            user_id=conv.user_id,
            message_count=conv.message_count,
            created_at=conv.created_at,
            updated_at=conv.updated_at,
            preview=preview,
        )


_conversation_service: ConversationService | None = None


def get_conversation_service() -> ConversationService:
    global _conversation_service
    if _conversation_service is None:
        _conversation_service = ConversationService()
    return _conversation_service
