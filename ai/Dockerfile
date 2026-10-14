# ============================================================
# Dockerfile - AI Service (Gestion Suivis)
# ============================================================
# Image optimisee pour le service IA FastAPI + Gemini
# Multi-stage build pour reduire la taille finale
# ============================================================

# ---------- Stage 1 : Builder ----------
FROM python:3.12-slim AS builder

WORKDIR /build

# Dependances systeme pour compiler certains packages Python
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copier requirements et installer dans un venv isole
COPY requirements.txt .
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt


# ---------- Stage 2 : Runtime ----------
FROM python:3.12-slim AS runtime

# Utilisateur non-root pour la securite
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copier le venv depuis le builder
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Copier le code source
COPY --chown=appuser:appuser app/ ./app/
COPY --chown=appuser:appuser .env.example ./.env.example

# Variables d'environnement par defaut
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PORT=8000 \
    HOST=0.0.0.0

# Passer a l'utilisateur non-root
USER appuser

# Exposer le port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://localhost:8000/health', timeout=5).raise_for_status()"

# Lancer le serveur
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
