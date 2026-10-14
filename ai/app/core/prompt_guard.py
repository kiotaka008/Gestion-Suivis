import re

from app.core.exceptions import PromptInjectionError, ValidationError
from app.utils.logger import get_logger

logger = get_logger("prompt_guard")


SUSPICIOUS_PATTERNS = [
    r"ignore\s+(all\s+)?(previous|prior|above)\s+(instructions|prompts|rules)",
    r"ignore\s+(les\s+)?instructions?\s+(precedentes?|ci-dessus)",
    r"tu\s+es\s+maintenant",
    r"you\s+are\s+now",
    r"new\s+role\s*:",
    r"nouveau\s+role\s*:",
    r"act\s+as\s+(a\s+)?(?!assistant)",
    r"agis\s+comme",
    r"repeat\s+(your\s+)?(system\s+)?prompt",
    r"repete\s+(ton\s+)?(system\s+)?prompt",
    r"what\s+(are|is)\s+your\s+(system\s+)?instructions",
    r"quelles?\s+sont\s+tes\s+instructions",
    r"print\s+(your\s+)?instructions",
    r"affiche\s+(tes\s+)?instructions",
    r"jailbreak",
    r"DAN\s+mode",
    r"developer\s+mode",
    r"mode\s+developpeur",
    r"sudo\s+mode",
    r"<\|im_start\|>",
    r"<\|im_end\|>",
    r"\[INST\]",
    r"\[/INST\]",
    r"<\|system\|>",
    r"<\|user\|>",
    r"<\|assistant\|>",
]

COMPILED_PATTERNS = [re.compile(p, re.IGNORECASE) for p in SUSPICIOUS_PATTERNS]

MAX_MESSAGE_LENGTH = 8000


def detect_prompt_injection(text: str) -> str | None:
    if not text:
        return None
    for pattern in COMPILED_PATTERNS:
        if pattern.search(text):
            return pattern.pattern
    return None


def check_messages_safety(messages: list[dict[str, str]]) -> None:
    for i, msg in enumerate(messages):
        if msg.get("role") != "user":
            continue

        content = msg.get("content", "")

        if len(content) > MAX_MESSAGE_LENGTH:
            logger.warning(f"Message utilisateur trop long : {len(content)} caracteres")
            raise ValidationError(
                f"Message trop long (max {MAX_MESSAGE_LENGTH} caracteres)."
            )

        detected = detect_prompt_injection(content)
        if detected:
            logger.warning(
                f"Prompt injection detectee (message {i}): pattern='{detected}'"
            )
            raise PromptInjectionError(
                "Votre message contient des instructions non autorisees. "
                "Veuillez reformuler votre demande."
            )


def sanitize_output(text: str) -> str:
    if not text:
        return text

    for pattern in [
        r"<\|im_start\|>",
        r"<\|im_end\|>",
        r"<\|system\|>",
        r"<\|user\|>",
        r"<\|assistant\|>",
    ]:
        text = re.sub(pattern, "", text)

    return text.strip()
