# API backend Gestion-Suivis

Architecture : **React → Spring Boot → FastAPI → Gemini**. Le frontend n'appelle que
cette API ; Spring Boot est le seul à appeler le service IA, et n'appelle jamais Gemini.

## 1. Démarrage et configuration

Variables d'environnement (aucune n'est obligatoire pour démarrer, sauf la base) :

| Variable | Rôle | Défaut |
|---|---|---|
| `DB_URL` | URL JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/gestion_suivis` |
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

Compte administrateur créé au premier démarrage s'il n'existe pas :
`admin@gestion-suivis.com` / `Admin@2026` (rôle ADMIN, à changer immédiatement).

## 2. Conventions

- **Authentification** : JWT dans le cookie HttpOnly `access_token` (`SameSite=Lax`, `Path=/api`),
  posé par `POST /api/auth/login`. Pour les outils (Postman, curl) : en-tête `Authorization: Bearer <jeton>`.
- **CSRF** (requêtes POST/PUT/PATCH/DELETE authentifiées par cookie) : renvoyer la valeur du cookie
  `XSRF-TOKEN` dans l'en-tête `X-XSRF-TOKEN` (comportement par défaut d'Axios ; sans proxy Vite,
  ajouter `withXSRFToken: true`). Non exigé pour la connexion, l'inscription et les requêtes Bearer.
- **JSON** : camelCase, noms identiques à `src/types/models.ts` ; identifiants UUID en texte ;
  dates-heures ISO-8601 UTC (`2025-01-10T09:00:00Z`) ; dates `YYYY-MM-DD`.
- **Énumérations** (valeurs JSON) :
  - rôles `admin`, `manager`, `member` (= EMPLOYE) ;
  - projets `active`, `on_hold`, `completed`, `cancelled` (= ANNULE, valeur nouvelle) — `archived` refusé ;
  - activités `todo`, `in_progress`, `done`, `overdue` (= EN_RETARD, valeur nouvelle) — `blocked` refusé ;
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
| 409 | CONFLICT | email déjà utilisé, département non vide, dernier administrateur… |
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
| ADMIN | toutes les données ; gestion des utilisateurs, départements, suppression des projets |
| MANAGER | projets et activités de son département (création, modification, affectation), utilisateurs de son département |
| EMPLOYE | ses activités (lecture ; mise à jour de `status`, `progress`, `actualHours`), projets dont il est membre ou chef |

Un chef de projet gère son projet quel que soit son rôle. Toutes les vérifications sont faites
dans les services ; un identifiant d'une ressource non autorisée donne 403
(404 pour les notifications et conversations d'un autre utilisateur).

## 4. Endpoints

Légende frontend : **U** utilisé par React, **D** défini dans un service React, **N** nouveau.

### Authentification — `/api/auth`
| Méthode | URL | Corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| POST | `/login` | `{ email, password }` | 200 `{ user }` + cookie ; 401 ; 422 | public | U |
| POST | `/logout` | — | 204, cookie effacé | public | U |
| GET | `/me` | — | 200 `User` ; 401 | connecté | U |
| GET | `/csrf` | — | 204 + cookie `XSRF-TOKEN` | public | N |
| POST | `/change-password` | `{ currentPassword, newPassword }` | 204 ; 422 | connecté | N |
| POST | `/register` | `{ name, email, password }` | 201 `User` ; 403 si fermé ; 409 | public | N |

### Utilisateurs — `/api/users`
| Méthode | URL | Paramètres / corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| GET | `/` | `page, perPage, sortBy (name, email, role, isActive, createdAt), sortOrder, search, role, isActive, departmentId` | `{ data: User[], meta }` | ADMIN, MANAGER (son département) | D |
| GET | `/me` | — | `User` | connecté | N |
| PATCH | `/me` | `{ name?, email?, bio?, avatarUrl? }` | `User` | connecté | N |
| GET | `/{id}` | — | `User` | ADMIN, MANAGER (département), soi-même | D |
| POST | `/` | `{ name, email, role, password, departmentId?, isActive? }` | 201 `User` ; 409 | ADMIN | N |
| PATCH | `/{id}` | `{ name?, email?, role?, departmentId?, avatarUrl?, bio? }` | `User` | ADMIN | D |
| POST | `/{id}/activate` | — | 204 | ADMIN | N |
| POST | `/{id}/deactivate` | — | 204 ; 409 (soi-même, dernier admin) | ADMIN | D |
| DELETE | `/{id}` | — | 204 ; 409 si lié à des données | ADMIN | N |

### Départements — `/api/departments`
| Méthode | URL | Corps | Réponse | Rôles |
|---|---|---|---|---|
| GET | `/` | `page, perPage, sortBy (name, createdAt), search` | `{ data: Department[], meta }` | connecté |
| GET | `/{id}` | — | `Department` | connecté |
| POST | `/` | `{ name, description?, managerId? }` | 201 ; 409 | ADMIN |
| PATCH | `/{id}` | `{ name?, description? }` | `Department` | ADMIN |
| DELETE | `/{id}` | — | 204 ; 409 si non vide | ADMIN |
| PUT | `/{id}/manager` | `{ userId }` (rôle manager) | `Department` ; 422 | ADMIN |
| DELETE | `/{id}/manager` | — | `Department` | ADMIN |
| GET | `/{id}/members` | `page, perPage` | `{ data: User[], meta }` | ADMIN, MANAGER du département |
| POST | `/{id}/members` | `{ userId }` | 204 | ADMIN |
| DELETE | `/{id}/members/{userId}` | — | 204 | ADMIN |

`Department` : `{ id, name, description, managerId, managerName, memberCount, createdAt, updatedAt }`.

### Projets — `/api/projects`
| Méthode | URL | Paramètres / corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| GET | `/` | `page, perPage, sortBy (name, status, priority, startDate, dueDate, createdAt, updatedAt), sortOrder, search, status, priority, departmentId, ownerId` | `{ data: Project[], meta }` | connecté (périmètre) | D |
| GET | `/{id}` | — | `Project` | périmètre | D |
| POST | `/` | `{ name, description?, status?, priority?, startDate?, dueDate?, ownerId?, departmentId?, memberIds? }` | 201 `Project` | ADMIN, MANAGER | D |
| PATCH | `/{id}` | mêmes champs, facultatifs | `Project` | ADMIN, MANAGER du département, chef de projet | D |
| DELETE | `/{id}` | — | 204 | ADMIN | D |
| POST | `/{id}/members` | `{ userId }` | `Project` | gestionnaires du projet | N |
| DELETE | `/{id}/members/{userId}` | — | `Project` | gestionnaires du projet | N |
| GET | `/{id}/activities` | `page, perPage, sortBy, sortOrder, status, priority` | `{ data: Activity[], meta }` | périmètre | N |

`Project` = type frontend + `departmentId`, `departmentName`, `isOverdue`. `progress` est calculé.

### Activités — `/api/activities`
| Méthode | URL | Paramètres / corps | Réponse | Rôles | Front |
|---|---|---|---|---|---|
| GET | `/` | `page, perPage, sortBy (title, status, priority, progress, startDate, dueDate, createdAt, updatedAt), sortOrder, search, status, priority, projectId, assigneeId, overdue` | `{ data: Activity[], meta }` | périmètre | D |
| GET | `/{id}` | — | `Activity` | gestionnaires, personne affectée | D |
| POST | `/` | `{ title, projectId, description?, status?, priority?, assigneeId?, startDate?, dueDate?, plannedHours?, progress? }` | 201 `Activity` ; 409 projet clos | gestionnaires du projet | D |
| PATCH | `/{id}` | mêmes champs + `actualHours` | `Activity` | gestionnaires ; personne affectée : `status, progress, actualHours` | D |
| DELETE | `/{id}` | — | 204 | gestionnaires du projet | D |
| GET | `/{id}/comments` | `page, perPage` | `{ data: Comment[], meta }` | qui voit l'activité | N |
| POST | `/{id}/comments` | `{ content }` | 201 `Comment` | qui voit l'activité | N |

`Activity` = type frontend + `projectName`, `startDate`, `plannedHours`, `actualHours`, `isOverdue`, `completedAt`.
Passer `status` à `done` met `progress` à 100 et renseigne `completedAt`.

### Notifications — `/api/notifications` (uniquement les siennes)
| Méthode | URL | Paramètres | Réponse |
|---|---|---|---|
| GET | `/` | `page, perPage, type, status (all, read, unread)` | `{ data: Notification[], meta }` |
| GET | `/unread-count` | — | `{ count }` |
| PATCH | `/{id}/read` | — | 204 |
| POST | `/read-all` | — | 204 |
| DELETE | `/{id}` | — | 204 |

Créées automatiquement : affectation d'activité, fin d'activité (chef de projet), commentaire,
ajout à un projet ou à un département, et chaque jour à 7 h les rappels d'échéance (≤ 2 jours)
et le passage en retard des activités échues.

### Tableau de bord et bilans (calculés par le backend)
| Méthode | URL | Paramètres | Réponse | Rôles |
|---|---|---|---|---|
| GET | `/api/dashboard/stats` | — | totaux, `monthlyEvolution`, `statusDistribution`, `tasksPerWeek`, `upcomingDeadlines`, `recentActivities` (formats de `mocks/dashboardStats.ts`) | connecté (périmètre) |
| GET | `/api/reports/overview` | `period` ou `from` + `to` | `Bilan` | connecté (périmètre) |
| GET | `/api/reports/users/{id}` | idem | `Bilan` | ADMIN, MANAGER (département), soi-même |
| GET | `/api/reports/departments/{id}` | idem | `Bilan` | ADMIN, MANAGER du département |
| GET | `/api/reports/projects/{id}` | idem | `Bilan` | qui voit le projet |

`period` : `week`, `month`, `year` (cahier des charges) ou `7d`, `30d`, `3m`, `6m`, `12m` (sélecteur du frontend) ; 30d par défaut.
`Bilan` : `completionRate`, `overdueRate`, `plannedHours`, `actualHours`, `hoursVariance`, `hoursVarianceRate`,
`averageCompletionDays`, compteurs, `monthlyEvolution`, `statusDistribution`, `priorityDistribution`, `projectsSummary`.

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
FastAPI), trace la consommation de jetons, et ne refait jamais un appel en échec.

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
4. Remplacer `archived` / `blocked` ; accepter `cancelled` / `overdue`.
5. Ajouter le mot de passe initial dans `CreateUserModal` ; `assigneeId`, `startDate`, `plannedHours` dans `CreateActivityModal`.
6. Brancher les pages sur l'API (au lieu de `src/mocks`) et activer `ProtectedRoute`.
