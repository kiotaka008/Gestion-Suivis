param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,

    [string]$TestDatabase = "projectmanager_restore_test",
    [string]$DbUser = "postgres"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

foreach ($command in @("createdb", "pg_restore", "dropdb")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command was not found. Add PostgreSQL bin directory to PATH."
    }
}

Write-Host "Creating isolated restore-test database: $TestDatabase"
createdb --username=$DbUser $TestDatabase
if ($LASTEXITCODE -ne 0) {
    throw "Could not create test database. It may already exist."
}

try {
    pg_restore --username=$DbUser --dbname=$TestDatabase --no-owner --no-acl $BackupFile
    if ($LASTEXITCODE -ne 0) {
        throw "Restore failed with exit code $LASTEXITCODE"
    }

    $tables = psql --username=$DbUser --dbname=$TestDatabase --tuples-only --command="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';"
    if ($LASTEXITCODE -ne 0 -or [int]$tables.Trim() -lt 1) {
        throw "Restore validation failed."
    }

    Write-Host "Restore test passed. Base tables found: $($tables.Trim())"
}
finally {
    Write-Host "Removing temporary restore-test database."
    dropdb --username=$DbUser $TestDatabase
}
