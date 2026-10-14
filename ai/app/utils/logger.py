import logging
import sys
from typing import Any

from app.config import get_settings


def setup_logger(name: str = "ai_service") -> logging.Logger:
    """
    Configure un logger structuré.
    - En dev : format lisible
    - En prod : format compact
    """
    settings = get_settings()
    logger = logging.getLogger(name)

    if logger.handlers:
        return logger

    logger.setLevel(settings.log_level)

    handler = logging.StreamHandler(sys.stdout)

    if settings.is_production:
        fmt = "%(asctime)s | %(levelname)s | %(name)s | %(message)s"
    else:
        fmt = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"

    handler.setFormatter(logging.Formatter(fmt, datefmt="%Y-%m-%d %H:%M:%S"))
    logger.addHandler(handler)
    logger.propagate = False

    return logger


def get_logger(name: str = "ai_service") -> logging.Logger:
    """Récupère un logger déjà configuré."""
    return logging.getLogger(name)


class SensitiveFilter(logging.Filter):
    """
    Filtre de sécurité : masque les données sensibles dans les logs.
    Ne JAMAIS logger les clés API, tokens, mots de passe.
    """

    SENSITIVE_KEYS = ("api_key", "apikey", "token", "password", "secret", "authorization")

    def filter(self, record: logging.LogRecord) -> bool:
        if isinstance(record.args, dict):
            record.args = {
                k: ("***REDACTED***" if any(s in k.lower() for s in self.SENSITIVE_KEYS) else v)
                for k, v in record.args.items()
            }
        return True