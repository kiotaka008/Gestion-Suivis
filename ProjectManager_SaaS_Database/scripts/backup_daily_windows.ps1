param(
    [string]$Database = "projectmanager",
    [string]$DbUser = "postgres",
    [string]$BackupDirectory = "$PSScriptRoot\..\backups",
    [int]$RetentionDays = 30
)

$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupPath = Join-Path $BackupDirectory "$Database`_$timestamp.dump"

if (-not (Get-Command pg_dump -ErrorAction SilentlyContinue)) {
    throw "pg_dump was not found. Add PostgreSQL bin directory to PATH."
}

New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null

Write-Host "Starting backup: $Database"
pg_dump --username=$DbUser --format=custom --no-owner --no-acl --file="$backupPath" $Database

if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed with exit code $LASTEXITCODE"
}

Get-ChildItem -Path $BackupDirectory -Filter "$Database`_*.dump" -File |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
    Remove-Item -Force

Write-Host "Backup completed: $backupPath"
