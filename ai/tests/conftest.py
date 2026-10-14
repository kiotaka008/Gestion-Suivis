import os
from collections.abc import Iterator
from unittest.mock import AsyncMock, patch

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

os.environ.setdefault("INTERNAL_API_KEY", "test-internal-key-1234567890")
os.environ.setdefault("GEMINI_API_KEY", "test-gemini-key-1234567890")
os.environ.setdefault("GEMINI_MODEL", "gemini-3.8-flash")
os.environ.setdefault("ENVIRONMENT", "development")
os.environ.setdefault("DEBUG", "true")
os.environ.setdefault("LOG_LEVEL", "WARNING")


@pytest.fixture(scope="session")
def internal_api_key() -> str:
    return "test-internal-key-1234567890"


@pytest.fixture
def auth_headers(internal_api_key: str) -> dict[str, str]:
    return {"X-Internal-API-Key": internal_api_key}


@pytest.fixture
def app() -> FastAPI:
    from app.main import create_app
    return create_app()


@pytest.fixture
def client(app: FastAPI) -> Iterator[TestClient]:
    with TestClient(app) as c:
        yield c


@pytest.fixture
def mock_llm_response():
    from app.models.schemas import TokenUsage
    return {
        "content": '{"result": "mocked response"}',
        "usage": TokenUsage(prompt_tokens=10, completion_tokens=20, total_tokens=30),
        "model": "gemini-3.8-flash",
    }


@pytest.fixture
def mock_llm_service(mock_llm_response):
    with patch("app.services.llm_service.LLMService.complete", new_callable=AsyncMock) as mock:
        mock.return_value = mock_llm_response
        yield mock
