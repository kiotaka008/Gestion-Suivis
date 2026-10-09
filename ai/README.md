\# AI Service - Gestion Suivis



Service IA interne pour la plateforme Gestion Suivis.



Ce micro-service expose des endpoints REST permettant d'utiliser les capacités d'un LLM (Google Gemini) pour :



\- Chat conversationnel

\- Résumé de contenu

\- Analyse de risques de projet

\- Génération de rapports structurés

\- Classification de texte

\- Extraction d'informations



Ce service est appelé UNIQUEMENT par le backend principal.

Il ne doit JAMAIS être exposé publiquement.



\---



\## Architecture



Frontend (React)

&#x20;   |

Backend principal (Node / Laravel / autre)

&#x20;   | (HTTP interne + clé API partagée)

Service IA (FastAPI) - VOUS ÊTES ICI

&#x20;   |

Google Gemini API



\---



\## Stack technique



| Composant | Technologie |

|---|---|

| Langage | Python 3.12 |

| Framework | FastAPI |

| Serveur | Uvicorn |

| Validation | Pydantic v2 |

| LLM | Google Gemini (google-genai) |

| Tests | pytest + pytest-asyncio |



\---



\## Prérequis



\- Python 3.12 (obligatoire)

\- pip et venv

\- Une clé API Google Gemini (gratuite sur https://aistudio.google.com/app/apikey)



\---



\## Installation



\### 1. Cloner le dépôt



git clone https://github.com/kiotaka008/Gestion-Suivis.git

cd Gestion-Suivis/ai



\### 2. Créer et activer l'environnement virtuel



Windows (PowerShell) :

&#x20;   py -3.12 -m venv venv

&#x20;   venv\\Scripts\\activate



Linux / macOS :

&#x20;   python3.12 -m venv venv

&#x20;   source venv/bin/activate



\### 3. Installer les dépendances



&#x20;   pip install --upgrade pip

&#x20;   pip install -r requirements.txt



\### 4. Configurer l'environnement



Copier le fichier d'exemple :

&#x20;   copy .env.example .env



Puis éditer .env et renseigner :

\- INTERNAL\_API\_KEY : clé partagée avec le backend principal

\- GEMINI\_API\_KEY : votre clé API Google Gemini



Ne JAMAIS commiter le fichier .env.



\---



\## Lancer le service



&#x20;   uvicorn app.main:app --reload --port 8000



Le service est disponible sur :

\- API : http://localhost:8000

\- Swagger UI : http://localhost:8000/docs

\- ReDoc : http://localhost:8000/redoc

\- Health : http://localhost:8000/health



\---



\## Endpoints



Tous les endpoints IA requièrent le header X-Internal-API-Key.



| Méthode | Endpoint | Description |

|---|---|---|

| GET | /health | Vérification de l'état du service |

| POST | /api/ai/chat | Chat conversationnel |

| POST | /api/ai/summarize | Résumer un contenu |

| POST | /api/ai/analyze-risks | Analyser les risques d'un projet |

| POST | /api/ai/generate-report | Générer un rapport structuré |

| POST | /api/ai/classify | Classifier un texte |

| POST | /api/ai/extract | Extraire des informations |



\---



\## Exemple d'utilisation



\### Chat



curl -X POST http://localhost:8000/api/ai/chat \\

&#x20; -H "Content-Type: application/json" \\

&#x20; -H "X-Internal-API-Key: votre-cle-interne" \\

&#x20; -d "{\\"messages\\": \[{\\"role\\": \\"user\\", \\"content\\": \\"Bonjour\\"}]}"



Réponse :

{

&#x20; "message": {

&#x20;   "role": "assistant",

&#x20;   "content": "Bonjour ! Je suis un assistant IA..."

&#x20; },

&#x20; "usage": {

&#x20;   "prompt\_tokens": 103,

&#x20;   "completion\_tokens": 40,

&#x20;   "total\_tokens": 517

&#x20; },

&#x20; "model": "gemini-3.8-flash",

&#x20; "created\_at": "2026-10-09T11:31:24"

}



\---



\## Tests



&#x20;   pytest



Avec coverage :

&#x20;   pytest --cov=app --cov-report=html



\---



\## Structure du projet



ai/

&#x20; app/

&#x20;   api/routes/     - Endpoints (health, chat, summarize, etc.)

&#x20;   core/           - Erreurs, sécurité

&#x20;   models/         - Schémas Pydantic

&#x20;   services/       - Logique métier (LLM, prompts)

&#x20;   utils/          - Logger, helpers

&#x20;   config.py       - Configuration

&#x20;   main.py         - Point d'entrée FastAPI

&#x20; tests/            - Tests unitaires

&#x20; .env.example      - Variables d'environnement (exemple)

&#x20; .gitignore

&#x20; README.md         - Ce fichier

&#x20; requirements.txt  - Dépendances Python



\---



\## Sécurité



\- Authentification : clé API interne (header X-Internal-API-Key)

\- CORS : restreint aux origines autorisées

\- Filtrage des logs : les secrets ne sont jamais loggés

\- Prompt injection : les données utilisateur sont traitées comme des données

\- Aucune clé API n'est exposée au frontend



\---



\## Auteur



Projet de stage - 2ème année

Responsable IA : \[Votre nom]

Contributeurs : 3 personnes



\---



\## Licence



Projet privé - Tous droits réservés.

