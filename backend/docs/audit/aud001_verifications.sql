-- AUD-001 : vérifications préalables, LECTURE SEULE, base projectmanager uniquement.
BEGIN TRANSACTION READ ONLY;

-- V0. Garde : arrêt immédiat si mauvaise base ou transaction non en lecture seule.
DO $$
BEGIN
    IF current_database() <> 'projectmanager' THEN
        RAISE EXCEPTION 'ARRET : base inattendue (%) ; seule projectmanager est concernée', current_database();
    END IF;
    IF current_setting('transaction_read_only') <> 'on' THEN
        RAISE EXCEPTION 'ARRET : la transaction n''est pas en lecture seule';
    END IF;
END $$;

-- Informations de session (aucun privilège particulier requis). Elles NE SUFFISENT PAS à
-- identifier l'instance : via localhost, l'adresse vaut 127.0.0.1 ou ::1 sur n'importe quel
-- ordinateur, et le port et la version peuvent être identiques ailleurs. adresse_serveur vaut NULL
-- pour une connexion par socket Unix. L'identification repose d'abord sur l'exécution depuis
-- l'ordinateur du backend, puis sur l'identifiant système ci-dessous lorsqu'il est disponible.
SELECT current_database() AS base, current_user AS compte,
       inet_server_addr() AS adresse_serveur, inet_server_port() AS port_serveur,
       current_setting('server_version_num') AS version, version() AS version_complete,
       current_setting('transaction_read_only') AS lecture_seule;

-- V0c (facultatif). Identifiant système de l'instance, comparé à la valeur relevée sur
-- l'ordinateur du backend par le journal de la campagne L1 du 15/11 (valeur de comparaison,
-- non vérifiée par la présente campagne). Ne fait jamais échouer le script : en cas de droits
-- insuffisants ou de toute autre erreur, affiche « non disponible ». Résultat affiché en NOTICE.
DO $$
DECLARE
    identifiant text;
BEGIN
    SELECT system_identifier::text INTO identifiant FROM pg_control_system();
    RAISE NOTICE 'V0c identifiant_systeme = % (référence journal L1 : 7703171660279105004) : %',
        identifiant,
        CASE WHEN identifiant = '7703171660279105004' THEN 'IDENTIQUE' ELSE 'DIFFERENT' END;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'V0c identifiant_systeme = non disponible (%)', SQLSTATE;
END $$;

-- V0b. Le compte utilisé voit-il toutes les lignes de audit_logs (V8) ?
-- Oui si : superutilisateur, ou BYPASSRLS, ou RLS non activée sur la table, ou propriétaire sans FORCE.
-- Table absente : toutes les conditions liées à la table valent false (résultat prudent).
SELECT r.rolname AS compte, r.rolsuper, r.rolbypassrls,
       c.oid IS NOT NULL AS audit_logs_existe,
       c.relrowsecurity AS rls_activee_audit_logs,
       pg_has_role(current_user, c.relowner, 'USAGE') AS proprietaire_audit_logs,
       c.relforcerowsecurity AS rls_forcee_audit_logs,
       (r.rolsuper OR r.rolbypassrls
        OR NOT coalesce(c.relrowsecurity, true)
        OR (coalesce(pg_has_role(current_user, c.relowner, 'USAGE'), false) AND NOT coalesce(c.relforcerowsecurity, true)))
           AS comptage_v8_exhaustif
FROM pg_roles r
LEFT JOIN pg_class c ON c.relname = 'audit_logs' AND c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
WHERE r.rolname = current_user;

-- V1. Attributs de postgres, rôles qui ignorent la RLS, existence du futur rôle.
SELECT rolname, rolsuper, rolbypassrls, rolcanlogin, rolcreatedb, rolcreaterole, rolreplication,
       rolinherit, rolconnlimit, rolvaliduntil IS NOT NULL AS expiration_definie
FROM pg_roles WHERE rolname = 'postgres';

SELECT rolname, rolsuper, rolbypassrls, rolcanlogin
FROM pg_roles WHERE rolsuper OR rolbypassrls ORDER BY rolname;

SELECT rolname, rolsuper, rolbypassrls, rolcanlogin FROM pg_roles WHERE rolname = 'projectmanager_app';

-- V2 et V3. Les 10 tables : existence, propriétaire, RLS activée, RLS forcée.
WITH attendues(nom) AS (VALUES ('organizations'), ('users'), ('projects'), ('project_members'), ('activities'),
                               ('notifications'), ('audit_logs'), ('ai_conversations'), ('ai_messages'),
                               ('ai_usage_events'))
SELECT a.nom AS table_, c.oid IS NOT NULL AS existe, pg_get_userbyid(c.relowner) AS proprietaire,
       c.relrowsecurity AS rls_activee, c.relforcerowsecurity AS rls_forcee
FROM attendues a
LEFT JOIN pg_class c ON c.relname = a.nom AND c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
ORDER BY a.nom;

SELECT c.relname, pg_get_userbyid(c.relowner) AS proprietaire, c.relrowsecurity
FROM pg_class c
WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
  AND c.relname NOT IN ('organizations', 'users', 'projects', 'project_members', 'activities', 'notifications',
                        'audit_logs', 'ai_conversations', 'ai_messages', 'ai_usage_events')
ORDER BY 1;

SELECT datname, pg_get_userbyid(datdba) AS proprietaire_base FROM pg_database WHERE datname = current_database();
SELECT nspname, pg_get_userbyid(nspowner) AS proprietaire_schema FROM pg_namespace WHERE nspname = 'public';

-- V4. Politiques RLS, et tables RLS sans politique.
SELECT tablename, policyname, permissive, roles, cmd, qual AS expression_using, with_check AS expression_with_check
FROM pg_policies WHERE schemaname = 'public' ORDER BY tablename, policyname;

SELECT c.relname FROM pg_class c
WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p') AND c.relrowsecurity
  AND NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid)
ORDER BY 1;

-- V5. Droits : base, schéma public, tables, colonnes ; séquences et extensions.
SELECT CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS beneficiaire,
       a.privilege_type, pg_get_userbyid(a.grantor) AS accorde_par, a.is_grantable
FROM pg_database d CROSS JOIN LATERAL aclexplode(coalesce(d.datacl, acldefault('d', d.datdba))) a
WHERE d.datname = current_database() ORDER BY 1, 2;

SELECT CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS beneficiaire,
       a.privilege_type, a.is_grantable
FROM pg_namespace n CROSS JOIN LATERAL aclexplode(coalesce(n.nspacl, acldefault('n', n.nspowner))) a
WHERE n.nspname = 'public' ORDER BY 1, 2;

SELECT c.relname AS table_, CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS beneficiaire,
       string_agg(a.privilege_type, ',' ORDER BY a.privilege_type) AS droits
FROM pg_class c CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) a
WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
GROUP BY 1, 2 ORDER BY 1, 2;

SELECT a.attrelid::regclass AS table_, a.attname, a.attacl
FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p') AND a.attacl IS NOT NULL
ORDER BY 1, 2;

SELECT count(*) AS sequences FROM pg_class WHERE relkind = 'S' AND relnamespace = 'public'::regnamespace;
SELECT extname, extversion, extnamespace::regnamespace AS schema_, pg_get_userbyid(extowner) AS proprietaire
FROM pg_extension ORDER BY 1;

-- V6. Privilèges par défaut (ALTER DEFAULT PRIVILEGES).
SELECT pg_get_userbyid(d.defaclrole) AS role_createur,
       CASE WHEN d.defaclnamespace = 0 THEN '(tous les schémas)' ELSE d.defaclnamespace::regnamespace::text END AS schema_,
       CASE d.defaclobjtype WHEN 'r' THEN 'tables' WHEN 'S' THEN 'séquences' WHEN 'f' THEN 'fonctions'
                            WHEN 'T' THEN 'types' WHEN 'n' THEN 'schémas' ELSE d.defaclobjtype::text END AS objets,
       CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS beneficiaire,
       a.privilege_type
FROM pg_default_acl d CROSS JOIN LATERAL aclexplode(d.defaclacl) a
ORDER BY 1, 2, 3, 4, 5;

-- V7. Appartenances, paramètres permanents, app.current_organization_id, row_security.
SELECT pg_get_userbyid(m.roleid) AS role_parent, pg_get_userbyid(m.member) AS membre,
       m.admin_option, m.inherit_option, m.set_option, pg_get_userbyid(m.grantor) AS accorde_par
FROM pg_auth_members m ORDER BY 1, 2;

SELECT coalesce(d.datname, '(toutes les bases)') AS base, coalesce(r.rolname, '(tous les rôles)') AS role_, s.setconfig
FROM pg_db_role_setting s
LEFT JOIN pg_database d ON d.oid = s.setdatabase
LEFT JOIN pg_roles r ON r.oid = s.setrole
WHERE s.setdatabase IN (0, (SELECT oid FROM pg_database WHERE datname = current_database()))
ORDER BY 1, 2;

SELECT coalesce(d.datname, '(toutes les bases)') AS base, coalesce(r.rolname, '(tous les rôles)') AS role_, c AS parametre
FROM pg_db_role_setting s
CROSS JOIN LATERAL unnest(s.setconfig) c
LEFT JOIN pg_database d ON d.oid = s.setdatabase
LEFT JOIN pg_roles r ON r.oid = s.setrole
WHERE c LIKE 'app.current_organization_id=%';

SELECT current_setting('app.current_organization_id', true) AS valeur_session;
SELECT name, setting, source FROM pg_settings WHERE name LIKE 'app.%' OR name = 'row_security';

-- V8. audit_logs sans organisation (exhaustif seulement si V0b.comptage_v8_exhaustif = t).
SELECT count(*) AS total,
       count(*) FILTER (WHERE organization_id IS NULL) AS sans_organisation,
       count(*) FILTER (WHERE user_id IS NULL) AS sans_utilisateur,
       count(*) FILTER (WHERE organization_id IS NULL AND user_id IS NULL) AS sans_organisation_ni_utilisateur
FROM audit_logs;

SELECT entity_type, action, count(*) AS nombre, min(created_at) AS premiere, max(created_at) AS derniere
FROM audit_logs WHERE organization_id IS NULL
GROUP BY entity_type, action ORDER BY nombre DESC;

SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE conrelid = 'public.audit_logs'::regclass AND contype = 'f' ORDER BY 1;

ROLLBACK;
