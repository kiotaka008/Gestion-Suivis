# Base de données — correspondance avec le backend

Le backend utilise le **schéma officiel « ProjectManager SaaS »** fourni par l'équipe base de
données (branche `develop`, dossier `ProjectManager_SaaS_Database/`) :

| Fichier | Rôle |
|---|---|
| `database/migrations/20260930_001_projectmanager_initial_schema.sql` | schéma initial (tables, ENUM, index, RLS) |
| `database/migrations/20261104_004_add_start_date_to_activities.sql` | ajoute `activities.start_date` |
| `database/seeds/20260930_002_projectmanager_dev_seed.sql` | jeu de données de développement (mot de passe `password123`) |

**Le backend ne crée ni ne modifie aucune table** : `spring.jpa.hibernate.ddl-auto=validate`.
Au démarrage, Hibernate compare les entités au schéma réel et refuse de démarrer en cas d'écart.
Vérifié sur une base PostgreSQL 18 créée avec les deux migrations et le seed.

## Installation (développement)

```powershell
psql -U postgres -c "CREATE DATABASE projectmanager"
psql -U postgres -d projectmanager -v ON_ERROR_STOP=1 -f database/migrations/20260930_001_projectmanager_initial_schema.sql
psql -U postgres -d projectmanager -v ON_ERROR_STOP=1 -f database/migrations/20261104_004_add_start_date_to_activities.sql
psql -U postgres -d projectmanager -v ON_ERROR_STOP=1 -f database/seeds/20260930_002_projectmanager_dev_seed.sql
```

Puis `DB_URL=jdbc:postgresql://localhost:5432/projectmanager` dans `backend/.env`.

## Multi-organisation et RLS

Toutes les tables (sauf `organizations`) portent `organization_id`. Le backend l'applique à deux niveaux :

1. **Hibernate** — chaque entité porte `@TenantId` sur `organization_id` : les requêtes sont
   filtrées et les insertions renseignées avec l'organisation de l'utilisateur connecté
   (`ContexteOrganisation`, `MultiOrganisationConfig`). Elle est lue dans le compte rechargé à
   chaque requête (claim `org` du JWT), jamais dans une donnée envoyée par le client.
2. **PostgreSQL RLS** — à chaque emprunt de connexion, le backend exécute
   `set_config('app.current_organization_id', <organisation>, false)` (valeur vide sans
   utilisateur : aucune ligne visible).

Le RLS ne s'applique que si le rôle de connexion n'est **ni superutilisateur, ni propriétaire des
tables, ni `BYPASSRLS`**. Rôle conseillé :

```sql
CREATE ROLE projectmanager_app LOGIN PASSWORD '...' NOSUPERUSER NOBYPASSRLS;
GRANT CONNECT ON DATABASE projectmanager TO projectmanager_app;
GRANT USAGE ON SCHEMA public TO projectmanager_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO projectmanager_app;
```

Les traitements sans utilisateur connecté s'exécutent explicitement dans une organisation
(`ExecuteurOrganisation`) : connexion (recherche du compte organisation par organisation),
lecture du compte associé au JWT, tâche quotidienne des échéances, création du compte initial.

**Connexion** : l'email n'est unique qu'au sein d'une organisation. Si le même email et le même
mot de passe existent dans plusieurs organisations (cas `jean.rakoto@company.com` du seed), la
connexion répond 422 sur le champ `organization` : il faut alors envoyer le slug
(`"organization": "tenant-isolation-test"`).

## Tables et entités

| Table | Entité | Remarques |
|---|---|---|
| `organizations` | `Organisation` | non filtrée (pas de RLS) |
| `users` | `Utilisateur` | `name`, `role` (`user_role`), `avatar_url`, `is_active`, `last_login_at` (mis à jour à la connexion) |
| `projects` | `Projet` | `progress` recalculé par le backend à chaque modification d'activité ; `priority` obligatoire (`medium` par défaut) |
| `project_members` | `MembreProjet` | clé `(project_id, user_id)` ; le chef de projet est membre avec `role = owner`, les autres `member` |
| `activities` | `Activite` | `start_date` (migration 004) |
| `notifications` | `Notification` | destinataire `user_id` |
| `ai_conversations` | `ConversationIa` | « dernière mise à jour » = date du dernier message |
| `ai_messages` | `MessageConversationIa` | `tokens_used` : jetons du prompt (question) ou de la réponse |
| `ai_usage_events` | `EvenementUsageIa` | `endpoint` = route backend (`/api/ai/chat`…), `cost_usd` = 0 (non fourni par l'IA), `status` = `success` |
| `audit_logs` | `JournalAudit` | actions `<type>.<action>` (`user.login`, `project.created`, `activity.deleted`…) ; `metadata` non renseigné |

### Types ENUM PostgreSQL

Les colonnes ENUM sont lues et écrites avec un convertisseur (`ConvertisseurEnumPg`) et
`@ColumnTransformer(read = "col::text", write = "?::type")`. Les valeurs sont identiques en base,
en JSON et dans le frontend :

| Type PostgreSQL | Valeurs | Énumération Java |
|---|---|---|
| `user_role` | `admin`, `manager`, `member` | `Role` (ADMIN, MANAGER, EMPLOYE) |
| `project_status` | `active`, `on_hold`, `completed`, `archived` | `StatutProjet` |
| `activity_status` | `todo`, `in_progress`, `done`, `blocked` | `StatutActivite` |
| `item_priority` | `low`, `medium`, `high`, `urgent` | `Priorite` |
| `project_member_role` | `owner`, `member` | `RoleMembreProjet` |
| `notification_type` | `project`, `activity`, `comment`, `deadline`, `ai`, `user` | `TypeNotification` |
| `ai_message_role` | `user`, `assistant` | `RoleMessageIa` |

## Fonctionnalités retirées faute de table ou de colonne

| Ancienne fonctionnalité du backend | Raison |
|---|---|
| Départements (`/api/departments`, `/api/reports/departments/{id}`, filtres `departmentId`) | pas de table `departments` |
| Commentaires d'activité (`/api/activities/{id}/comments`) | pas de table de commentaires |
| Heures prévues / réelles et indicateurs d'écart d'heures | pas de colonnes `planned_hours` / `actual_hours` |
| Statuts `cancelled` (projet) et `overdue` (activité) | absents des ENUM ; le retard reste calculé (`isOverdue`) |
| Date d'achèvement exacte, créateur de l'activité, `bio` | pas de colonnes `completed_at`, `created_by`, `bio` |

Sans départements, le périmètre du rôle MANAGER devient l'organisation : il consulte tous les
projets, activités et utilisateurs de son organisation et gère les projets dont il est chef.

## Suppressions

Le schéma déclare `ON DELETE CASCADE` pour les membres, activités, notifications et
conversations, et `ON DELETE RESTRICT` pour `projects.owner_id` et `activities.assignee_id` :
un utilisateur chef de projet ou affecté à une activité ne peut pas être supprimé (409, le
désactiver à la place).
