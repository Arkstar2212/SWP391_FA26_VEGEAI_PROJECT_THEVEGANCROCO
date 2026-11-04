param(
    [string]$OutputRoot = (Join-Path $PSScriptRoot '..\backups')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDirectory = Join-Path $OutputRoot $stamp
$databaseDump = Join-Path $backupDirectory 'vegeai-postgres.dump'
$uploadsDirectory = Join-Path $backupDirectory 'uploads'

New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $uploadsDirectory -Force | Out-Null

Push-Location $projectRoot
try {
    docker compose ps | Out-Null
    docker compose exec -T postgres pg_dump -U postgres -d vegeai -Fc -f /tmp/vegeai-postgres.dump
    docker cp 'vegeai-postgres:/tmp/vegeai-postgres.dump' $databaseDump
    docker compose exec -T postgres rm -f /tmp/vegeai-postgres.dump

    $backendRunning = docker compose ps --status running --services | Select-String '^backend$'
    if ($backendRunning) {
        docker cp 'vegeai-backend:/app/uploads/.' $uploadsDirectory
    }

    [ordered]@{
        createdAt = (Get-Date).ToString('o')
        database = 'vegeai-postgres.dump'
        uploads = 'uploads'
        project = 'VEGEAI'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backupDirectory 'manifest.json') -Encoding UTF8

    Write-Host "Backup completed: $backupDirectory" -ForegroundColor Green
} finally {
    Pop-Location
}
