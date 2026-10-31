from datetime import datetime, timezone
from pydantic import BaseModel, Field


class ConversationMessage(BaseModel):
    role: str
    content: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class Conversation(BaseModel):
    id: str
    user_id: str | None = None
    messages: list[ConversationMessage] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    message_count: int = 0

    def add_message(self, role: str, content: str) -> None:
        self.messages.append(ConversationMessage(role=role, content=content))
        self.updated_at = datetime.now(timezone.utc)
        self.message_count = len(self.messages)


class ConversationSummary(BaseModel):
    id: str
    user_id: str | None = None
    message_count: int
    created_at: datetime
    updated_at: datetime
    preview: str = ""


class ConversationListResponse(BaseModel):
    conversations: list[ConversationSummary]
    total: int
