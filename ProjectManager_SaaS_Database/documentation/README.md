# ProjectManager SaaS — Database

Version: 1.0  
Database: PostgreSQL 15+  
Timezone convention: `TIMESTAMPTZ` values are stored internally in UTC.

## 1. Contents

```text
database/
  migrations/   Versioned schema migrations
  seeds/        Development-only fixtures
  tests/        Database and tenant-isolation tests
  rollback/     Destructive rollback scripts
scripts/        Windows backup and restore-test scripts
documentation/  ERD and technical documentation
```

## 2. Requirements

- PostgreSQL 15 or newer
- `psql`, `pg_dump`, `pg_restore`, `createdb`, `dropdb`
- A database created for the application

## 3. Installation

Create a database:

```sql
CREATE DATABASE projectmanager;
```

Apply the schema:

```powershell
psql -U postgres -d projectmanager -v ON_ERROR_STOP=1 -f database/migrations/20260930_001_projectmanager_initial_schema.sql
```

Load development data:

```powershell
psql -U postgres -d projectmanager -v ON_ERROR_STOP=1 -f database/seeds/20260930_002_projectmanager_dev_seed.sql
```

Run the isolation test:

```powershell
psql -U postgres -d projectmanager -v ON_ERROR_STOP=1 -f database/tests/20260930_003_tenant_isolation_check.sql
```

## 4. Development accounts

All development accounts use `password123`. Passwords are stored as bcrypt hashes using `pgcrypto`.

| Email | Role | Organization |
|---|---|---|
| rickael@company.com | admin | demo-corp |
| tiavina@company.com | manager | demo-corp |
| romeo@company.com | manager | demo-corp |
| agathe@company.com | member | demo-corp |
| jean.rakoto@company.com | member | demo-corp |
| jean.rakoto@company.com | member | tenant-isolation-test |

The second organization is a test fixture for tenant isolation. It is not an additional customer in the business demo.

Never use development credentials or seed data in production.

## 5. Multi-tenancy

Tenant ownership is represented by `organization_id`. Composite foreign keys ensure that project owners, project members, activity assignees, notification recipients, AI conversation owners and AI messages belong to the same organization as the related record.

Row Level Security (RLS) policies provide an additional database-level filter. The application must set the tenant context after authentication and before executing tenant-scoped queries:

```sql
BEGIN;
SELECT set_config('app.current_organization_id', 'ORGANIZATION-UUID', true);
-- Tenant-scoped queries run here.
COMMIT;
```

Use a transaction-scoped setting (`true`) with connection pooling. The application must derive the organization UUID from the authenticated server-side identity, never trust a client-supplied organization ID. The application database role must not have `BYPASSRLS` and must not own the tables; consider `FORCE ROW LEVEL SECURITY` for stricter enforcement after provisioning the runtime role.

RLS is not a substitute for backend authorization. Validate role permissions and object access in the API.

## 6. Index strategy

Indexes cover tenant filters, common status/date filters, and foreign-key joins. PostgreSQL automatically creates indexes for primary keys and unique constraints. Composite indexes should be adjusted using real query plans and workload measurements.

Example:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, name, status, due_date
FROM projects
WHERE organization_id = '00000000-0000-4000-8000-000000000000'
  AND status = 'active'
ORDER BY due_date;
```

Do not add indexes indiscriminately: indexes improve reads but add write and storage cost.

## 7. Backup and recovery

Daily custom-format backup:

```powershell
.\scripts\backup_daily_windows.ps1 -Database projectmanager -DbUser postgres
```

The script retains the latest 30 days of local dump files. Configure Windows Task Scheduler to run it daily. Store copies off-host and encrypt them.

Restore test:

```powershell
.\scripts\restore_test_windows.ps1 -BackupFile "C:\path\projectmanager_20260930_020000.dump"
```

The restore test creates a temporary database, restores the dump, checks that tables exist, and removes the temporary database.

Point-in-time recovery (PITR) requires PostgreSQL physical base backups plus continuous WAL archiving. A daily `pg_dump` alone does not provide PITR. Configure and test `archive_mode`, `archive_command`, base backups, WAL retention, and recovery targets according to the PostgreSQL version and hosting environment.

## 8. Redis cache

The application layer may cache dashboard/report aggregates in Redis with a 300-second TTL. Cache keys must include the organization ID, for example:

`reports:{organization_id}:overview`

Invalidate or refresh relevant keys after project/activity mutations. Do not cache authorization decisions across tenants.

## 9. Migration policy

- Never edit a migration that has already been applied in a shared environment.
- Create a new migration for every schema change.
- Test migrations against a clean database and a recent sanitized backup.
- Review rollback scripts before execution; rollback may destroy data.
- Run `ANALYZE` after large imports or major data changes.

## 10. Production checklist

- Replace development passwords and rotate secrets.
- Use a dedicated least-privilege application database role.
- Ensure RLS policies are tested using the actual application role.
- Enable TLS for database connections.
- Configure automated off-site backups and WAL archiving.
- Test point-in-time recovery and document RPO/RTO.
- Monitor disk, connections, slow queries, replication and backup status.
- Run security, load and migration tests before release.
