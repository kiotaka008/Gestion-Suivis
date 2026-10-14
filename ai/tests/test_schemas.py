import pytest
from pydantic import ValidationError

from app.models.schemas import (
    ChatMessage,
    ChatRequest,
    ClassifyRequest,
    ExtractRequest,
    MessageRole,
    SummarizeRequest,
)


class TestChatMessage:
    def test_valid_message(self) -> None:
        msg = ChatMessage(role=MessageRole.USER, content="Bonjour")
        assert msg.role == MessageRole.USER
        assert msg.content == "Bonjour"

    def test_content_is_stripped(self) -> None:
        msg = ChatMessage(role=MessageRole.USER, content="  Bonjour  ")
        assert msg.content == "Bonjour"

    def test_empty_content_raises(self) -> None:
        with pytest.raises(ValidationError):
            ChatMessage(role=MessageRole.USER, content="   ")


class TestChatRequest:
    def test_minimal_request(self) -> None:
        req = ChatRequest(
            messages=[ChatMessage(role=MessageRole.USER, content="Salut")]
        )
        assert len(req.messages) == 1
        assert req.stream is False
        assert req.temperature is None

    def test_temperature_out_of_range(self) -> None:
        with pytest.raises(ValidationError):
            ChatRequest(
                messages=[ChatMessage(role=MessageRole.USER, content="Salut")],
                temperature=3.0,
            )


class TestSummarizeRequest:
    def test_valid(self) -> None:
        req = SummarizeRequest(content="Un long texte à résumer...", max_words=100)
        assert req.max_words == 100
        assert req.language == "fr"

    def test_content_too_short(self) -> None:
        with pytest.raises(ValidationError):
            SummarizeRequest(content="Court")


class TestClassifyRequest:
    def test_valid(self) -> None:
        req = ClassifyRequest(
            text="Un texte à classer",
            categories=["bug", "feature"],
        )
        assert len(req.categories) == 2

    def test_requires_at_least_two_categories(self) -> None:
        with pytest.raises(ValidationError):
            ClassifyRequest(text="Texte", categories=["seule"])


class TestExtractRequest:
    def test_valid(self) -> None:
        req = ExtractRequest(
            text="Un texte suffisamment long pour passer.",
            fields=["nom", "email"],
        )
        assert req.fields == ["nom", "email"]