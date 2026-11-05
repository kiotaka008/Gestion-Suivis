# API backend Gestion-Suivis

Architecture : **React → Spring Boot → FastAPI → Gemini**. Le frontend n'appelle que
cette API ; Spring Boot est le seul à appeler le service IA, et n'appelle jamais Gemini.

## 1. Démarrage et configuration

Variables d'environnement (aucune n'est obligatoire pour démarrer, sauf la base) :

| Variable | Rôle | Défaut |
|---|---|---|
| `DB_URL` | URL JDBC PostgreSQL (schéma officiel `ProjectManager_SaaS_Database`) | `jdbc:postgresql://localhost:5432/projectmanager` |
| `DB_USERNAME` / `DB_PASSWORD` | identifiants PostgreSQL | `postgres` / vide |
| `JWT_SECRET` | secret HMAC des JWT (32 octets min.) | aléatoire à chaque démarrage (sessions perdues) |
| `JWT_EXPIRATION_MINUTES` | durée du jeton (5-1440) | 60 |
| `COOKIE_SECURE` | `true` en HTTPS (production) | `false` |
| `CORS_ALLOWED_ORIGINS` | origines supplémentaires, séparées par des virgules | — (5173 et 3000 toujours autorisés) |
| `AUTH_REGISTRATION_ENABLED` | ouvre `POST /api/auth/register` | `false` |
| `AI_SERVICE_URL` | adresse du service FastAPI | absent → IA « indisponible » |
| `AI_INTERNAL_API_KEY` | clé `X-Internal-API-Key` (16 caractères min.) | absent → IA « indisponible » |
| `AI_CONNECT_TIMEOUT_SECONDS` / `AI_READ_TIMEOUT_SECONDS` / `AI_HEALTH_TIMEOUT_SECONDS` | délais IA | 5 / 60 / 3 |
| `ECHEANCES_CRON` | planification du traitement des échéances | `0 0 7 * * *` |
| `JPA_SHOW_SQL` | affiche les requêtes SQL | `false` |

Ports de développement : React (Vite) 5173, Spring Boot 8080, FastAPI 8000, PostgreSQL 5432.
Recommandé côté frontend : proxy Vite `'/api' → 'http://localhost:8080'` et `VITE_API_URL=/api`.

Base de données : schéma officiel PostgreSQL 15+ (branche `develop`, dossier
`ProjectManager_SaaS_Database`), voir [SCHEMA_BASE.md](SCHEMA_BASE.md). Le backend ne crée ni
ne modifie aucune table (`ddl-auto=validate`).

Sur une base **vide** (aucune organisation), le backend crée au démarrage l'organisation
`gestion-suivis` et le compte `admin@gestion-suivis.com` / `Admin@2026` (rôle ADMIN, à changer
immédiatement). Avec le jeu de données de développement, utiliser ses comptes
(`rickael@company.com` / `password123`, etc.).

## 2. Conventions

- **Authentification** : JWT dans le cookie HttpOnly `access_token` (`SameSite=Lax`, `Path=/api`),
  posé par `POST /api/auth/login`. Pour les outils (Postman, curl) : en-tête `Authorization: Bearer <jeton>`.
- **CSRF** (requêtes POST/PUT/PATCH/DELETE authentifiées par cookie) : renvoyer la valeur du cookie
  `XSRF-TOKEN` dans l'en-tête `X-XSRF-TOKEN` (comportement par défaut d'Axios ; sans proxy Vite,
  ajouter `withXSRFToken: true`). Non exigé pour la connexion, la déconnexion, l'inscription et les requêtes Bearer.
- **JSON** : camelCase, noms identiques à `src/types/models.ts` ; identifiants UUID en texte ;
  dates-heures ISO-8601 UTC (`2025-01-10T09:00:00Z`) ; dates `YYYY-MM-DD`.
- **Multi-organisation** : chaque utilisateur appartient à une organisation ; toutes les données
  renvoyées sont limitées à son organisation (filtre Hibernate `@TenantId` + RLS PostgreSQL).
  Une ressource d'une autre organisation répond 404.
- **Énumérations** (valeurs JSON = valeurs des types ENUM PostgreSQL = frontend) :
  - rôles `admin`, `manager`, `member` (= EMPLOYE) ;
  - projets `active`, `on_hold`, `completed`, `archived` ;
  - activités `todo`, `in_progress`, `done`, `blocked` (le retard est calculé : `isOverdue`) ;
  - priorités `low`, `medium`, `high`, `urgent` ;
  - notifications `project`, `activity`, `comment`, `deadline`, `ai`, `user`.
- **Pagination** : `?page=1&perPage=20&sortBy=dueDate&sortOrder=asc` (page à partir de 1, `perPage` ≤ 100).
  Réponse : `{ "data": [...], "meta": { "page": 1, "perPage": 20, "total": 57, "totalPages": 3 } }`.
- **PATCH** : un champ absent ou `null` n'est pas modifié.
- **Erreurs** : `{ timestamp, status, code, message, path, errors? }`, `errors: [{ field, message }]`.

| HTTP | `code` | Cas |
|---|---|---|
| 400 | BAD_REQUEST | JSON mal formé, paramètre de mauvais type, tri ou période inconnus |
| 401 | UNAUTHORIZED | non connecté, session expirée, compte désactivé, identifiants faux |
| 403 | FORBIDDEN | rôle insuffisant, ressource d'un autre utilisateur, jeton CSRF absent |
| 404 | NOT_FOUND | ressource inexistante |
| 409 | CONFLICT | email déjà utilisé dans l'organisation, retrait du chef de projet, dernier administrateur… |
| 422 | VALIDATION_ERROR | champs invalides, valeur d'énumération refusée, incohérence (dates) |
| 429 | RATE_LIMITED | limite du service IA (en-tête `Retry-After`) |
| 502 / 503 | SERVER_ERROR | réponse IA inattendue / service IA indisponible |
| 500 | SERVER_ERROR | erreur interne (aucun détail renvoyé) |

Exemple 422 :
```json
{ "timestamp": "2026-11-01T10:15:30Z", "status": 422, "code": "VALIDATION_ERROR",
  "message": "Les données envoyées sont invalides.", "path": "/api/projects",
  "errors": [ { "field": "name", "message": "Le nom du projet est obligatoire." } ] }
```

## 3. Permissions

| Rôle | Périmètre |
|---|---|
| ADMIN | toutes les données de l'organisation ; gestion des utilisateurs, changement de chef, suppression des projets |
| MANAGER | consultation de tous les projets, activités et utilisateurs de l'organisation ; création de projets ; gestion des projets dont il est chef |
| EMPLOYE | ses activités (lecture ; mise à jour de `status`, `progress`), projets dont il est membre, chef ou affecté |

Un chef de projet gère son projet quel que soit son rôle. Toutes les vérifications sont faites
dans les services ; un identifiant d'une ressource non autorisée donne 403
(404 pour les notifications et conversations d'un autre utilisateur).

## 4. Endpoints

Légende frontend : **U** utilisé par React, **D** défini dans un service React, **N** nouveau.

### Authentification — `/api/auth`
| Méthode | URL | Corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| POST | `/login` | `{ email, password, organization? }` | 200 `{ user }` + cookie ; 401 ; 422 (`organization` requis si l'email existe dans plusieurs organisations) | public | U |
| POST | `/logout` | — | 204, cookie effacé | public | U |
| GET | `/me` | — | 200 `User` ; 401 | connecté | U |
| GET | `/csrf` | — | 204 + cookie `XSRF-TOKEN` | public | N |
| POST | `/change-password` | `{ currentPassword, newPassword }` | 204 ; 422 | connecté | N |
| POST | `/register` | `{ name, email, password, organization? }` | 201 `User` ; 403 si fermé ; 409 ; 422 (`organization` requis s'il y en a plusieurs) | public | N |

### Utilisateurs — `/api/users`
| Méthode | URL | Paramètres / corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| GET | `/` | `page, perPage, sortBy (name, email, role, isActive, createdAt), sortOrder, search, role, isActive` | `{ data: User[], meta }` | ADMIN, MANAGER | D |
| GET | `/me` | — | `User` | connecté | N |
| PATCH | `/me` | `{ name?, email?, avatarUrl? }` | `User` | connecté | N |
| GET | `/{id}` | — | `User` | ADMIN, MANAGER, soi-même | D |
| POST | `/` | `{ name, email, role, password, isActive? }` | 201 `User` ; 409 | ADMIN | N |
| PATCH | `/{id}` | `{ name?, email?, role?, avatarUrl? }` | `User` | ADMIN | D |
| POST | `/{id}/activate` | — | 204 | ADMIN | N |
| POST | `/{id}/deactivate` | — | 204 ; 409 (soi-même, dernier admin) | ADMIN | D |
| DELETE | `/{id}` | — | 204 ; 409 si chef de projet, affecté ou propriétaire de conversations | ADMIN | N |

`User` = type frontend + `organizationId`, `lastLoginAt`.

### Projets — `/api/projects`
| Méthode | URL | Paramètres / corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| GET | `/` | `page, perPage, sortBy (name, status, priority, startDate, dueDate, createdAt, updatedAt), sortOrder, search, status, priority, ownerId` | `{ data: Project[], meta }` | connecté (périmètre) | D |
| GET | `/{id}` | — | `Project` | périmètre | D |
| POST | `/` | `{ name, description?, status?, priority?, startDate?, dueDate?, ownerId?, memberIds? }` | 201 `Project` | ADMIN, MANAGER | D |
| PATCH | `/{id}` | mêmes champs, facultatifs (`ownerId` : ADMIN seulement) | `Project` | ADMIN, chef de projet | D |
| DELETE | `/{id}` | — | 204 | ADMIN | D |
| POST | `/{id}/members` | `{ userId }` | `Project` | gestionnaires du projet | N |
| DELETE | `/{id}/members/{userId}` | — | `Project` ; 409 pour le chef | gestionnaires du projet | N |
| GET | `/{id}/activities` | `page, perPage, sortBy, sortOrder, status, priority` | `{ data: Activity[], meta }` | périmètre | N |

`Project` = type frontend + `isOverdue`. Le chef de projet est membre (`project_members.role = owner`).
`progress` (colonne `projects.progress`) est recalculé à chaque modification d'activité
(moyenne des activités, une activité terminée = 100 %).

### Activités — `/api/activities`
| Méthode | URL | Paramètres / corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| GET | `/` | `page, perPage, sortBy (title, status, priority, progress, startDate, dueDate, createdAt, updatedAt), sortOrder, search, status, priority, projectId, assigneeId, overdue` | `{ data: Activity[], meta }` | périmètre | D |
| GET | `/{id}` | — | `Activity` | gestionnaires, personne affectée | D |
| POST | `/` | `{ title, projectId, description?, status?, priority?, assigneeId?, startDate?, dueDate?, progress? }` | 201 `Activity` ; 409 projet clos | gestionnaires du projet | D |
| PATCH | `/{id}` | mêmes champs | `Activity` | gestionnaires ; personne affectée : `status, progress` | D |
| DELETE | `/{id}` | — | 204 | gestionnaires du projet | D |

`Activity` = type frontend + `projectName`, `startDate`, `isOverdue`, `completedAt`.
Passer `status` à `done` met `progress` à 100. `completedAt` est approximatif (dernière
modification d'une activité terminée : le schéma n'a pas de date d'achèvement).

### Notifications — `/api/notifications` (uniquement les siennes)
| Méthode | URL | Paramètres | Réponse |
|---|---|---|---|
| GET | `/` | `page, perPage, type, status (all, read, unread)` | `{ data: Notification[], meta }` |
| GET | `/unread-count` | — | `{ count }` |
| PATCH | `/{id}/read` | — | 204 |
| POST | `/read-all` | — | 204 |
| DELETE | `/{id}` | — | 204 |

Créées automatiquement : affectation d'activité, fin d'activité (chef de projet), ajout à un
projet, et chaque jour à 7 h (organisation par organisation) les rappels d'échéance (≤ 2 jours)
et les alertes de retard des activités échues.

### Tableau de bord et bilans (calculés par le backend)
| Méthode | URL | Paramètres | Réponse | Rôles |
|---|---|---|---|---|
| GET | `/api/dashboard/stats` | — | totaux, `monthlyEvolution`, `statusDistribution`, `tasksPerWeek`, `upcomingDeadlines`, `recentActivities` (formats de `mocks/dashboardStats.ts`) | connecté (périmètre) |
| GET | `/api/reports/overview` | `period` ou `from` + `to` | `Bilan` | connecté (périmètre) |
| GET | `/api/reports/users/{id}` | idem | `Bilan` | ADMIN, MANAGER, soi-même |
| GET | `/api/reports/projects/{id}` | idem | `Bilan` | qui voit le projet |

`period` : `week`, `month`, `year` (cahier des charges) ou `7d`, `30d`, `3m`, `6m`, `12m` (sélecteur du frontend) ; 30d par défaut.
`Bilan` : `completionRate`, `overdueRate`, `averageCompletionDays` (approximatif), compteurs, `monthlyEvolution`, `statusDistribution`, `priorityDistribution`, `projectsSummary`.

### Suivi pour le module IA — `/api/suivi` (mêmes permissions)
| Méthode | URL | Paramètres | Réponse |
|---|---|---|---|
| GET | `/taches-non-accomplies` | `page, perPage, sortBy, sortOrder, projectId, assigneeId` | tâches non terminées échues (`reason: overdue`, `daysOverdue`) |
| GET | `/taches-sans-progression` | `page, perPage, days (7), projectId` | `reason: not_started` ou `no_progress` |
| GET | `/indicateurs-risque` | `projectId?` | liste triée : `riskLevel` (`low…critical`), `reasons`, `expectedProgress`, `overdueRate`… |

### Assistant IA — `/api/ai` (via FastAPI)
| Méthode | URL | Corps | Réponse | Rôles |
|---|---|---|---|---|
| POST | `/chat` | `{ conversationId?, content }` (≤ 8000 caractères) | `{ conversationId, message: { id, role, content, createdAt } }` | connecté |
| GET | `/conversations` | `page, perPage` | `{ data: [{ id, title, preview, messageCount, createdAt, updatedAt }], meta }` | les siennes |
| GET | `/conversations/{id}/messages` | `page, perPage` | `{ data: Message[], meta }` | propriétaire |
| DELETE | `/conversations/{id}` | — | 204 | propriétaire |
| POST | `/projects/{id}/risk-analysis` | — | `{ projectId, overallRisk, risks[], summary, indicators, model }` | qui voit le projet |
| POST | `/reports` | `{ reportType: weekly\|monthly\|project\|productivity, projectId? }` | `{ reportType, title, content, sections, model }` | connecté (périmètre) |
| GET | `/health` | — | `{ disponible, statut, version, environnement, latenceMs }` | ADMIN |

Le backend ajoute à la question un résumé des seules données visibles par l'utilisateur
(sans email ni nom de collègue), conserve l'historique (49 derniers messages renvoyés à
FastAPI), trace la consommation de jetons (`ai_messages.tokens_used`, `ai_usage_events`),
et ne refait jamais un appel en échec.

## 5. Exemples curl

```bash
# Connexion (enregistre le cookie)
curl -i -c cookies.txt -H "Content-Type: application/json" \
     -d '{"email":"admin@gestion-suivis.com","password":"Admin@2026"}' \
     http://localhost:8080/api/auth/login

# Lecture avec le cookie
curl -b cookies.txt "http://localhost:8080/api/projects?page=1&perPage=20&sortBy=dueDate&sortOrder=asc"

# Écriture avec le cookie : récupérer XSRF-TOKEN puis l'envoyer en en-tête
curl -c cookies.txt -b cookies.txt http://localhost:8080/api/auth/csrf
curl -b cookies.txt -H "X-XSRF-TOKEN: <valeur du cookie XSRF-TOKEN>" -H "Content-Type: application/json" \
     -d '{"name":"Migration cloud","priority":"high","dueDate":"2025-12-31"}' \
     http://localhost:8080/api/projects

# Variante Postman / scripts : jeton Bearer (pas de CSRF)
curl -H "Authorization: Bearer <jeton>" http://localhost:8080/api/suivi/taches-non-accomplies
```

## 6. Adaptations attendues côté frontend (non réalisées par le backend)

1. Proxy Vite `/api` → `http://localhost:8080` et `VITE_API_URL=/api` (sinon `withXSRFToken: true`).
2. Afficher le `message` renvoyé par l'API (notamment « Email ou mot de passe incorrect. » au lieu de « session expirée »).
3. Traiter le 400 (`BAD_REQUEST`) ; lire `errors[{ field, message }]` sur le 422 (déjà prévu).
4. Les statuts `archived` / `blocked` du frontend sont désormais acceptés (alignés sur la base).
5. Ajouter le mot de passe initial dans `CreateUserModal` ; `assigneeId`, `startDate` dans `CreateActivityModal`.
6. Brancher les pages sur l'API (au lieu de `src/mocks`) et activer `ProtectedRoute`.
7. Si un même email existe dans plusieurs organisations, envoyer `organization` (slug) à la connexion.
