from app.models.schemas import (
    AITaskType,
    AnalyzeRisksRequest,
    ClassifyRequest,
    ExtractRequest,
    GenerateReportRequest,
    SummarizeRequest,
)
from app.services.prompt_service import (
    SYSTEM_PROMPTS,
    build_analyze_risks_prompt,
    build_classify_prompt,
    build_extract_prompt,
    build_generate_report_prompt,
    build_summarize_prompt,
)


class TestSystemPrompts:
    def test_all_task_types_have_prompts(self) -> None:
        for task in AITaskType:
            assert task in SYSTEM_PROMPTS, f"Prompt manquant pour {task}"

    def test_prompts_are_not_empty(self) -> None:
        for task, prompt in SYSTEM_PROMPTS.items():
            assert len(prompt) > 20, f"Prompt trop court pour {task}"


class TestSummarizePrompt:
    def test_contains_content(self) -> None:
        req = SummarizeRequest(content="Mon contenu a resumer", max_words=100)
        prompt = build_summarize_prompt(req)
        assert "Mon contenu a resumer" in prompt
        assert "100" in prompt

    def test_contains_context_if_provided(self) -> None:
        req = SummarizeRequest(
            content="Ceci est un contenu suffisamment long",
            context="Projet Refonte",
            max_words=100,
        )
        prompt = build_summarize_prompt(req)
        assert "Projet Refonte" in prompt


class TestAnalyzeRisksPrompt:
    def test_contains_project_name(self) -> None:
        req = AnalyzeRisksRequest(
            project_name="Migration cloud",
            activities=[],
        )
        prompt = build_analyze_risks_prompt(req)
        assert "Migration cloud" in prompt

    def test_contains_activities(self) -> None:
        req = AnalyzeRisksRequest(
            project_name="Test",
            activities=[
                {"title": "Tache A", "status": "blocked", "due_date": "2025-10-15"}
            ],
        )
        prompt = build_analyze_risks_prompt(req)
        assert "Tache A" in prompt
        assert "blocked" in prompt


class TestClassifyPrompt:
    def test_contains_categories(self) -> None:
        req = ClassifyRequest(
            text="Mon texte",
            categories=["bug", "feature"],
        )
        prompt = build_classify_prompt(req)
        assert "bug" in prompt
        assert "feature" in prompt
        assert "Mon texte" in prompt


class TestExtractPrompt:
    def test_contains_fields(self) -> None:
        req = ExtractRequest(
            text="Texte source",
            fields=["nom", "email"],
        )
        prompt = build_extract_prompt(req)
        assert "nom" in prompt
        assert "email" in prompt
        assert "Texte source" in prompt


class TestGenerateReportPrompt:
    def test_contains_report_type(self) -> None:
        req = GenerateReportRequest(
            report_type="weekly",
            data={"key": "value"},
        )
        prompt = build_generate_report_prompt(req)
        assert "weekly" in prompt
        assert "key" in prompt
