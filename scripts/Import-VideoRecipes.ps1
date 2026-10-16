[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Container = 'vegeai-postgres',
    [string]$Database = 'vegeai',
    [string]$DatabaseUser = 'postgres'
)

$ErrorActionPreference = 'Stop'
$catalogPath = Join-Path $PSScriptRoot '../data/video-recipes.json'
$sqlPath = Join-Path $PSScriptRoot 'import-video-recipes.sql'
$catalog = @(Get-Content -LiteralPath $catalogPath -Raw -Encoding UTF8 | ConvertFrom-Json)
if ($catalog.Count -ne 11 -or @($catalog.slug | Sort-Object -Unique).Count -ne 11) {
    throw 'Expected 11 unique curated recipes.'
}
foreach ($recipe in $catalog) {
    if ($recipe.youtubeId -notmatch '^[A-Za-z0-9_-]{11}$' -or
        $recipe.sourceUrl -notmatch '^https://ducankitchen\.com/' -or
        $recipe.title.Length -gt 150 -or $recipe.slug -notmatch '^[a-z0-9-]+$' -or
        @($recipe.ingredients).Count -eq 0 -or @($recipe.instructions).Count -eq 0) {
        throw "Invalid catalog entry: $($recipe.slug)"
    }
    foreach ($ingredient in $recipe.ingredients) {
        if ([string]::IsNullOrWhiteSpace($ingredient.name) -or $ingredient.name.Length -gt 100 -or
            [string]::IsNullOrWhiteSpace($ingredient.unit) -or $ingredient.unit.Length -gt 30 -or
            ($null -ne $ingredient.amount -and $ingredient.amount -le 0)) {
            throw "Invalid ingredient in $($recipe.slug)"
        }
    }
    foreach ($step in $recipe.instructions) {
        if ([string]::IsNullOrWhiteSpace($step) -or $step.Length -gt 1000) {
            throw "Invalid instruction in $($recipe.slug)"
        }
    }
}
if (-not $PSCmdlet.ShouldProcess("$Container/$Database", 'Import missing video recipes without updating existing recipes')) {
    return
}

$previousEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    docker cp $catalogPath "${Container}:/tmp/vegeai-video-recipes.json"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy catalog into database container.' }
    Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 |
        docker exec -i $Container psql -U $DatabaseUser -d $Database -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) { throw 'Recipe import failed; transaction rolled back.' }
} finally {
    $OutputEncoding = $previousEncoding
}
