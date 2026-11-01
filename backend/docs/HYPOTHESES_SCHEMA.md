# Hypothèses de schéma PostgreSQL — à valider par l'équipe base de données

Le schéma PostgreSQL 18 définitif n'a pas été fourni. Les entités JPA du backend
(`src/main/java/com/gestionsuivis/backend/entity/`) reposent donc sur les
hypothèses ci-dessous, déduites du cahier des charges et des types du frontend
(`src/types/models.ts`, branche `feature/frontend`).

**Le backend ne crée ni ne modifie aucune table** : `spring.jpa.hibernate.ddl-auto=validate`.
Au démarrage, Hibernate compare les entités aux tables réelles et **refuse de démarrer
en indiquant chaque écart** (table ou colonne manquante, type différent). Il suffira
alors d'ajuster les annotations `@Table` / `@Column` des entités.

Ce document n'est pas un script SQL : c'est la liste de ce que le code attend.

## Conventions supposées

| Élément | Hypothèse |
|---|---|
| Identifiants | `uuid`, générés par l'application (`@UuidGenerator`) ; une valeur par défaut `gen_random_uuid()` côté base est compatible |
| Dates et heures | `timestamp with time zone` (`created_at`, `updated_at`, `completed_at`), écrites en UTC |
| Dates seules | `date` (`start_date`, `due_date`) |
| Énumérations | texte (`varchar`) contenant le nom Java : `ADMIN`, `EN_COURS`, `HAUTE`… Si ce sont des types `ENUM` PostgreSQL, il faudra ajouter `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` |
| Heures | `numeric(7,2)` |

## Tables et colonnes attendues

### `users` — entité `Utilisateur`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| email | varchar(255) | NOT NULL, UNIQUE |
| password_hash | varchar(255) | NOT NULL (BCrypt) |
| full_name | varchar(150) | NOT NULL |
| role | varchar(20) | NOT NULL — `ADMIN`, `MANAGER`, `EMPLOYE` |
| is_active | boolean | NOT NULL |
| avatar_url | varchar(500) | |
| bio | varchar(1000) | |
| department_id | uuid | FK → departments.id |
| created_at / updated_at | timestamptz | NOT NULL |

### `departments` — entité `Departement`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| name | varchar(150) | NOT NULL, UNIQUE |
| description | varchar(1000) | |
| manager_id | uuid | FK → users.id (responsable) |
| created_at / updated_at | timestamptz | NOT NULL |

### `projects` — entité `Projet`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| name | varchar(200) | NOT NULL |
| description | varchar(5000) | |
| status | varchar(20) | NOT NULL — `EN_COURS`, `TERMINE`, `EN_ATTENTE`, `ANNULE` |
| priority | varchar(20) | `BASSE`, `MOYENNE`, `HAUTE`, `URGENTE` (le frontend a une priorité de projet) |
| start_date / due_date | date | |
| owner_id | uuid | NOT NULL, FK → users.id (chef de projet) |
| department_id | uuid | FK → departments.id |
| created_at / updated_at | timestamptz | NOT NULL |

### `project_members` — association projets ↔ utilisateurs
| Colonne | Type | Contraintes |
|---|---|---|
| project_id | uuid | FK → projects.id |
| user_id | uuid | FK → users.id |
| | | PK composée (project_id, user_id) |

### `activities` — entité `Activite`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| project_id | uuid | NOT NULL, FK → projects.id |
| title | varchar(200) | NOT NULL |
| description | varchar(5000) | |
| status | varchar(20) | NOT NULL — `A_FAIRE`, `EN_COURS`, `TERMINE`, `EN_RETARD` |
| priority | varchar(20) | NOT NULL |
| progress | integer | NOT NULL (0-100) |
| assignee_id | uuid | FK → users.id |
| start_date / due_date | date | |
| planned_hours / actual_hours | numeric(7,2) | |
| completed_at | timestamptz | |
| created_by | uuid | FK → users.id |
| created_at / updated_at | timestamptz | NOT NULL |

### `activity_comments` — entité `Commentaire`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| activity_id | uuid | NOT NULL, FK → activities.id |
| author_id | uuid | NOT NULL, FK → users.id |
| content | varchar(5000) | NOT NULL |
| created_at | timestamptz | NOT NULL |

### `notifications` — entité `Notification`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| recipient_id | uuid | NOT NULL, FK → users.id |
| type | varchar(20) | NOT NULL — `PROJET`, `ACTIVITE`, `COMMENTAIRE`, `ECHEANCE`, `IA`, `UTILISATEUR` |
| title | varchar(200) | NOT NULL |
| message | varchar(1000) | |
| is_read | boolean | NOT NULL |
| project_id / activity_id | uuid | liens facultatifs (sans FK supposée) |
| created_at | timestamptz | NOT NULL |

### `ai_conversations` — entité `ConversationIa`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | NOT NULL, FK → users.id |
| title | varchar(200) | |
| created_at / updated_at | timestamptz | NOT NULL |

### `ai_messages` — entité `MessageConversationIa`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| conversation_id | uuid | NOT NULL, FK → ai_conversations.id |
| role | varchar(20) | NOT NULL — `USER`, `ASSISTANT` |
| content | text | NOT NULL |
| created_at | timestamptz | NOT NULL |

### `ai_usage_events` — entité `EvenementUsageIa`
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | NOT NULL |
| conversation_id | uuid | |
| operation | varchar(50) | NOT NULL — `chat`, `analyze_risks`, `generate_report` |
| model | varchar(100) | |
| prompt_tokens / completion_tokens / total_tokens | integer | NOT NULL |
| created_at | timestamptz | NOT NULL |

## Suppressions

Le backend supprime explicitement les données dépendantes (commentaires avant
activités, activités avant projet, messages avant conversation) : il ne suppose
**aucun** `ON DELETE CASCADE`. Si le schéma en définit, c'est compatible.

## Non couvert (en attente de décision)

- `organizations` / `organization_id` (multi-organisation) : non implémenté.
- `audit_logs` : non implémenté.
