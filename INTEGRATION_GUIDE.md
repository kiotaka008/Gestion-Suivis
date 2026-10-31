\# Guide d'Intégration - Gestion Suivis



Ce document explique comment les 3 parties du projet (Frontend, Backend, Service IA) communiquent entre elles.



\## Architecture



Frontend (React) → Backend Principal → Service IA (FastAPI) → Google Gemini API



\## Règles d'or



1\. \*\*Le Frontend ne doit JAMAIS appeler le Service IA directement.\*\*

2\. \*\*Le Backend Principal est le seul à connaître la clé secrète du Service IA.\*\*

3\. \*\*Le Service IA est "stateless"\*\* (il ne stocke pas les conversations, c'est le Backend qui le fait).



\---



\## 1. Pour le Frontend (React)



Le Frontend appelle uniquement le Backend Principal.



\- Le Frontend envoie le message de l'utilisateur au Backend.

\- Le Backend renvoie la réponse de l'IA au Frontend.

\- Pour le streaming (effet "ChatGPT"), le Backend fera un proxy du flux SSE vers le Frontend.



\---



\## 2. Pour le Backend (Responsable Backend)



Le Backend fait le pont entre le Frontend et le Service IA.



\### Configuration requise

Le Backend doit avoir deux variables d'environnement :

\- `AI\_SERVICE\_URL` : URL du service IA (ex: `http://localhost:8000`)

\- `AI\_INTERNAL\_API\_KEY` : clé secrète partagée avec le service IA



\### Flux de traitement d'une requête de chat

1\. \*\*Authentification\*\* : Vérifier que l'utilisateur est connecté (JWT ou session).

2\. \*\*Récupération de l'historique\*\* : Aller chercher l'historique de la conversation dans SA base de données.

3\. \*\*Appel au Service IA\*\* : Envoyer une requête HTTP POST vers `{AI\_SERVICE\_URL}/api/ai/chat` avec :

&#x20;  - Le header `X-Internal-API-Key: {AI\_INTERNAL\_API\_KEY}`

&#x20;  - Le body JSON contenant l'historique complet de la conversation.

4\. \*\*Sauvegarde\*\* : Récupérer la réponse de l'IA et la sauvegarder dans SA base de données.

5\. \*\*Réponse au Frontend\*\* : Renvoyer la réponse de l'IA au Frontend.



\### Pour le streaming (SSE)

Le Backend peut soit retransmettre le flux `text/event-stream` tel quel au Frontend, soit le consommer et utiliser WebSocket.



\---



\## 3. Pour le Service IA (FastAPI)



Le Service IA reçoit des requêtes structurées et renvoie des réponses de Gemini.



\### Endpoints disponibles

| Endpoint | Méthode | Description |

|---|---|---|

| `/api/ai/chat` | POST | Chat conversationnel |

| `/api/ai/chat/stream` | POST | Chat avec streaming SSE |

| `/api/ai/summarize` | POST | Résumer un texte |

| `/api/ai/analyze-risks` | POST | Analyser les risques |

| `/api/ai/classify` | POST | Classifier un texte |

| `/api/ai/extract` | POST | Extraire des informations |

| `/api/ai/generate-report` | POST | Générer un rapport |

| `/api/ai/conversations` | GET | Lister les conversations |



\### Authentification

Toutes les requêtes vers `/api/ai/\*` doivent inclure le header :

`X-Internal-API-Key: <votre-cle-secrete>`



\### Documentation complète

\- Swagger : `http://localhost:8000/docs`

\- README : `ai/README.md`



\---



\## Checklist d'intégration



\- \[ ] Le Backend a bien `AI\_SERVICE\_URL` et `AI\_INTERNAL\_API\_KEY` dans son `.env`.

\- \[ ] Le Backend transmet le header `X-Internal-API-Key` au Service IA.

\- \[ ] Le Backend sauvegarde les messages dans SA base de données.

\- \[ ] Le Frontend n'appelle jamais le Service IA directement.

