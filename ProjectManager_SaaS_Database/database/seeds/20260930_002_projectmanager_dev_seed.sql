-- ProjectManager SaaS
-- Development seed only. Do not run in production.
-- All demo accounts use the development password: password123.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM organizations
        WHERE slug IN ('demo-corp', 'tenant-isolation-test')
    ) THEN
        RAISE EXCEPTION 'Seed data already exists. Use a clean development database.';
    END IF;
END;
$$;

INSERT INTO organizations (name, slug)
VALUES
    ('Demo Corp', 'demo-corp'),
    ('Tenant Isolation Test', 'tenant-isolation-test');

INSERT INTO users (organization_id, email, password_hash, name, role)
SELECT o.id, v.email, crypt('password123', gen_salt('bf', 12)), v.name, v.role::user_role
FROM organizations o
CROSS JOIN (VALUES
    ('rickael@company.com', 'Rickael Brayan', 'admin'),
    ('tiavina@company.com', 'Tiavina Herinjaka', 'manager'),
    ('romeo@company.com', 'Romeo Joseph', 'manager'),
    ('agathe@company.com', 'Agathe Tsiresimiady', 'member'),
    ('jean.rakoto@company.com', 'Jean Rakoto', 'member')
) AS v(email, name, role)
WHERE o.slug = 'demo-corp';

INSERT INTO users (organization_id, email, password_hash, name, role)
SELECT o.id, 'jean.rakoto@company.com',
       crypt('password123', gen_salt('bf', 12)),
       'Jean Rakoto - isolation test', 'member'::user_role
FROM organizations o
WHERE o.slug = 'tenant-isolation-test';

INSERT INTO projects
    (organization_id, name, description, status, priority, progress, owner_id, start_date, due_date)
SELECT o.id, v.name, v.description, v.status::project_status,
       v.priority::item_priority, v.progress, u.id,
       CURRENT_DATE - v.days_ago, CURRENT_DATE + v.days_until
FROM organizations o
CROSS JOIN (VALUES
    ('Website redesign', 'Refonte de l’interface du gestionnaire de projets.', 'active', 'high', 45, 12, 30, 'tiavina@company.com'),
    ('API development', 'Développement des endpoints du backend.', 'active', 'urgent', 20, 8, 21, 'romeo@company.com'),
    ('Database migration', 'Conception et migration du schéma PostgreSQL multi-tenant.', 'active', 'high', 70, 15, 18, 'tiavina@company.com'),
    ('Mobile application', 'Préparation de la version mobile.', 'on_hold', 'medium', 35, 20, 50, 'agathe@company.com'),
    ('CI/CD pipeline', 'Automatisation des tests et déploiements.', 'completed', 'medium', 100, 45, 5, 'rickael@company.com'),
    ('AI project assistant', 'Assistant pour analyse et rapports.', 'active', 'urgent', 10, 3, 25, 'rickael@company.com')
) AS v(name, description, status, priority, progress, days_ago, days_until, owner_email)
JOIN users u ON u.organization_id = o.id AND u.email = v.owner_email
WHERE o.slug = 'demo-corp';

INSERT INTO projects
    (organization_id, name, description, status, priority, progress, owner_id, start_date, due_date)
SELECT o.id, 'Private tenant test project',
       'Project restricted to the second organization.',
       'active', 'low', 5, u.id, CURRENT_DATE, CURRENT_DATE + 14
FROM organizations o
JOIN users u ON u.organization_id = o.id
    AND u.email = 'jean.rakoto@company.com'
WHERE o.slug = 'tenant-isolation-test';

INSERT INTO project_members (organization_id, project_id, user_id, role)
SELECT p.organization_id, p.id, p.owner_id, 'owner'
FROM projects p;

INSERT INTO project_members (organization_id, project_id, user_id, role)
SELECT p.organization_id, p.id, u.id, 'member'
FROM projects p
JOIN organizations o ON o.id = p.organization_id
JOIN users u ON u.organization_id = o.id
WHERE o.slug = 'demo-corp'
  AND (
      (p.name = 'Website redesign' AND u.email = 'agathe@company.com') OR
      (p.name = 'API development' AND u.email = 'romeo@company.com') OR
      (p.name = 'Database migration' AND u.email = 'tiavina@company.com') OR
      (p.name = 'CI/CD pipeline' AND u.email = 'jean.rakoto@company.com')
  )
ON CONFLICT (project_id, user_id) DO NOTHING;

INSERT INTO activities
    (organization_id, project_id, title, description, status, priority, progress, assignee_id, due_date)
SELECT o.id, p.id, v.title, v.description, v.status::activity_status,
       v.priority::item_priority, v.progress, u.id, CURRENT_DATE + v.days_until
FROM organizations o
JOIN projects p ON p.organization_id = o.id
CROSS JOIN (VALUES
    ('Website redesign', 'Collecte des besoins', 'Recueillir les besoins des utilisateurs.', 'done', 'high', 100, 'agathe@company.com', 2),
    ('API development', 'Créer les endpoints projets', 'Préparer les routes de gestion des projets.', 'in_progress', 'urgent', 45, 'romeo@company.com', 7),
    ('Database migration', 'Valider les relations multi-tenant', 'Tester les relations et l’isolation des organisations.', 'in_progress', 'high', 70, 'tiavina@company.com', 5),
    ('CI/CD pipeline', 'Configurer les tests automatiques', 'Exécuter les tests avant déploiement.', 'done', 'medium', 100, 'jean.rakoto@company.com', 1),
    ('AI project assistant', 'Définir le prompt système', 'Préparer les instructions du backend.', 'todo', 'urgent', 0, 'rickael@company.com', 10)
) AS v(project_name, title, description, status, priority, progress, assignee_email, days_until)
JOIN users u ON u.organization_id = o.id AND u.email = v.assignee_email
WHERE o.slug = 'demo-corp' AND p.name = v.project_name;

INSERT INTO notifications (organization_id, user_id, type, title, message)
SELECT u.organization_id, u.id, v.type::notification_type, v.title, v.message
FROM (VALUES
    ('tiavina@company.com', 'project', 'Projet à vérifier', 'La progression du projet Database migration doit être revue.'),
    ('romeo@company.com', 'activity', 'Activité assignée', 'Une activité API vous a été assignée.'),
    ('agathe@company.com', 'deadline', 'Échéance proche', 'Une échéance approche pour le projet Website redesign.')
) AS v(email, type, title, message)
JOIN organizations o ON o.slug = 'demo-corp'
JOIN users u ON u.organization_id = o.id AND u.email = v.email;

INSERT INTO audit_logs (organization_id, user_id, action, entity_type, entity_id, metadata)
SELECT o.id, u.id, 'project.created', 'project', p.id,
       jsonb_build_object('source', 'development_seed', 'project_name', p.name)
FROM organizations o
JOIN users u ON u.organization_id = o.id AND u.email = 'rickael@company.com'
JOIN projects p ON p.organization_id = o.id AND p.name = 'AI project assistant'
WHERE o.slug = 'demo-corp';

WITH conversation AS (
    INSERT INTO ai_conversations (user_id, organization_id, title)
    SELECT u.id, o.id, 'Project status demo'
    FROM organizations o
    JOIN users u ON u.organization_id = o.id AND u.email = 'tiavina@company.com'
    WHERE o.slug = 'demo-corp'
    RETURNING id, organization_id
)
INSERT INTO ai_messages (organization_id, conversation_id, role, content, tokens_used)
SELECT c.organization_id, c.id, v.role::ai_message_role, v.content, v.tokens_used
FROM conversation c
CROSS JOIN (VALUES
    ('user', 'Summarize the current project status.', 12),
    ('assistant', 'Development sample response. Connect the backend AI service for live responses.', 24)
) AS v(role, content, tokens_used);

INSERT INTO ai_usage_events
    (organization_id, user_id, endpoint, model, prompt_tokens, completion_tokens, cost_usd, status)
SELECT o.id, u.id, '/api/ai/chat', 'seed-placeholder', 12, 24, 0, 'success'
FROM organizations o
JOIN users u ON u.organization_id = o.id AND u.email = 'tiavina@company.com'
WHERE o.slug = 'demo-corp';

COMMIT;
