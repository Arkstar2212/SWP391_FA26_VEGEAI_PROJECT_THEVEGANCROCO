param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = 'Stop'
if (-not $ConfirmRestore) {
    throw 'Restore replaces current database data. Run again with -ConfirmRestore after checking the backup path.'
}

$resolvedBackup = (Resolve-Path -LiteralPath $BackupDirectory).Path
$databaseDump = Join-Path $resolvedBackup 'vegeai-postgres.dump'
$uploadsDirectory = Join-Path $resolvedBackup 'uploads'
if (-not (Test-Path -LiteralPath $databaseDump)) { throw "Database dump not found: $databaseDump" }

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    docker compose up -d postgres redis
    docker compose stop backend
    docker cp $databaseDump 'vegeai-postgres:/tmp/vegeai-postgres.dump'
    docker compose exec -T postgres pg_restore -U postgres -d vegeai --clean --if-exists --no-owner /tmp/vegeai-postgres.dump
    docker compose exec -T postgres rm -f /tmp/vegeai-postgres.dump
    docker compose up -d backend

    if (Test-Path -LiteralPath $uploadsDirectory) {
        docker compose exec -T backend sh -c 'rm -rf /app/uploads/* && mkdir -p /app/uploads/community /app/uploads/content-interactions'
        docker cp (Join-Path $uploadsDirectory '.') 'vegeai-backend:/app/uploads/'
    }
    Write-Host 'Restore completed.' -ForegroundColor Green
} finally {
    Pop-Location
}
