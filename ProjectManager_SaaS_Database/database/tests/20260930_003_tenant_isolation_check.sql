-- ProjectManager SaaS
-- Tenant isolation verification
-- Run after the initial migration and development seed.

-- Summary by tenant.
SELECT
    o.slug,
    COUNT(DISTINCT u.id) AS users_count,
    COUNT(DISTINCT p.id) AS projects_count,
    COUNT(DISTINCT a.id) AS activities_count
FROM organizations o
LEFT JOIN users u ON u.organization_id = o.id
LEFT JOIN projects p ON p.organization_id = o.id
LEFT JOIN activities a ON a.organization_id = o.id
WHERE o.slug IN ('demo-corp', 'tenant-isolation-test')
GROUP BY o.id, o.slug
ORDER BY o.slug;

-- This test expects the composite foreign keys to reject a cross-tenant
-- project membership. The savepoint keeps the test transaction clean.
DO $$
DECLARE
    demo_org UUID;
    demo_project UUID;
    other_org_user UUID;
    rejected BOOLEAN := FALSE;
BEGIN
    SELECT id INTO demo_org
    FROM organizations
    WHERE slug = 'demo-corp';

    SELECT id INTO demo_project
    FROM projects
    WHERE organization_id = demo_org
      AND name = 'Website redesign';

    SELECT u.id INTO other_org_user
    FROM users u
    JOIN organizations o ON o.id = u.organization_id
    WHERE o.slug = 'tenant-isolation-test'
      AND u.email = 'jean.rakoto@company.com';

    IF demo_org IS NULL OR demo_project IS NULL OR other_org_user IS NULL THEN
        RAISE EXCEPTION 'Required seed data is missing.';
    END IF;

    BEGIN
        INSERT INTO project_members (organization_id, project_id, user_id, role)
        VALUES (demo_org, demo_project, other_org_user, 'member');
    EXCEPTION
        WHEN foreign_key_violation THEN
            rejected := TRUE;
    END;

    IF NOT rejected THEN
        RAISE EXCEPTION 'FAIL: cross-tenant membership was accepted.';
    END IF;

    RAISE NOTICE 'PASS: cross-tenant project membership was rejected.';
END;
$$;

-- Verify that each activity belongs to the same tenant as its project.
SELECT a.id, a.organization_id, a.project_id
FROM activities a
JOIN projects p ON p.id = a.project_id
WHERE a.organization_id <> p.organization_id;
-- Expected result: 0 rows.
