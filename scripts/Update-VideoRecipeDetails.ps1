[CmdletBinding(SupportsShouldProcess)]
param([string]$Container='vegeai-postgres', [string]$Database='vegeai', [string]$DatabaseUser='postgres')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Expand-VideoRecipeCatalog.ps1')
$catalog = @(Get-ExpandedVideoRecipes)
$catalog | ForEach-Object {
    [pscustomobject]@{ slug=$_.slug; servings=$_.servings; calories=$_.nutrition.calories; protein=$_.nutrition.proteinGrams; carbs=$_.nutrition.carbsGrams; fat=$_.nutrition.fatGrams; fiber=$_.nutrition.fiberGrams }
} | Format-Table
if (-not $PSCmdlet.ShouldProcess("$Container/$Database", 'Update only the 11 linked video recipes with standardized quantities and estimated nutrition')) { return }
$temporary = Join-Path ([System.IO.Path]::GetTempPath()) ('vegeai-recipe-details-' + [guid]::NewGuid().ToString('N') + '.json')
$previousEncoding = $OutputEncoding
try {
    $catalog | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $temporary -Encoding utf8NoBOM
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    docker cp $temporary "${Container}:/tmp/vegeai-video-recipe-details.json"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy recipe details.' }
    Get-Content (Join-Path $PSScriptRoot 'update-video-recipe-details.sql') -Raw -Encoding UTF8 |
        docker exec -i $Container psql -U $DatabaseUser -d $Database -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) { throw 'Details update failed; transaction rolled back.' }
} finally {
    $OutputEncoding = $previousEncoding
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
}
