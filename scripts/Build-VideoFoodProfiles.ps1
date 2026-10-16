param([Parameter(Mandatory)][string]$DatasetDirectory)
$ErrorActionPreference = 'Stop'
$ids = Get-Content (Join-Path $PSScriptRoot '../data/video-recipe-food-ids.json') -Raw | ConvertFrom-Json -AsHashtable
$wanted = [System.Collections.Generic.HashSet[string]]::new()
foreach ($id in $ids.Values) { [void]$wanted.Add([string]$id) }
$foodFile = Get-ChildItem -LiteralPath $DatasetDirectory -Recurse -Filter food.csv | Select-Object -First 1
$nutrientFile = Get-ChildItem -LiteralPath $DatasetDirectory -Recurse -Filter food_nutrient.csv | Select-Object -First 1
if (-not $foodFile -or -not $nutrientFile) { throw 'USDA SR Legacy CSV files not found.' }
$foods = @{}
Import-Csv $foodFile.FullName | Where-Object { $wanted.Contains($_.fdc_id) } | ForEach-Object { $foods[$_.fdc_id] = $_.description }
$values = @{}
Import-Csv $nutrientFile.FullName | Where-Object {
    $wanted.Contains($_.fdc_id) -and $_.nutrient_id -in @('1008','1003','1004','1005','1079')
} | ForEach-Object {
    if (-not $values.ContainsKey($_.fdc_id)) { $values[$_.fdc_id] = @{} }
    $values[$_.fdc_id][$_.nutrient_id] = [double]::Parse($_.amount, [cultureinfo]::InvariantCulture)
}
$result = [ordered]@{}
foreach ($key in ($ids.Keys | Sort-Object)) {
    $id = [string]$ids[$key]
    $n = $values[$id]
    foreach ($nutrient in @('1008','1003','1004','1005')) {
        if (-not $n.ContainsKey($nutrient)) { throw "Missing nutrient $nutrient for $key ($id)" }
    }
    $result[$key] = [ordered]@{
        fdcId = [int]$id; description = $foods[$id]
        sourceUrl = "https://fdc.nal.usda.gov/food-details/$id/nutrients"
        per100g = @{ calories=$n['1008']; proteinGrams=$n['1003']; carbsGrams=$n['1005']; fatGrams=$n['1004']; fiberGrams=$(if ($n.ContainsKey('1079')) { $n['1079'] } else { $null }) }
    }
}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot '../data/video-food-profiles.json') -Encoding utf8
Write-Output "Built $($result.Count) USDA reference profiles."
