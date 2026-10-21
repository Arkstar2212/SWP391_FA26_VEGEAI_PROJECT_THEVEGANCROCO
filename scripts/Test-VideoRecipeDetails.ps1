param([switch]$Live, [string]$BaseUrl='http://localhost:8081', [string]$AccessToken)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Expand-VideoRecipeCatalog.ps1')
$catalog=@(Get-ExpandedVideoRecipes)
if ($catalog.Count -ne 11) { throw 'Recipe count mismatch.' }
$rice=$catalog | Where-Object slug -eq 'com-chien-mi-goi-chay'
if ($rice.nutrition.calories -ne 735 -or $rice.nutrition.proteinGrams -ne 20.7) { throw 'Weighted per-serving calculation changed.' }
$noodles=$catalog | Where-Object slug -eq 'bun-lut-xao-rau-cu'
if ($noodles.nutrition.calories -ne 392) { throw 'Two-serving division changed.' }
$unknownFiber=@($catalog | Where-Object { $null -eq $_.nutrition.fiberGrams })
if (@($unknownFiber.slug | Sort-Object) -join ',' -ne 'canh-chua-rau-cu-thanh-mat,com-hap-la-sen-chay') { throw 'Unexpected missing fiber data.' }
foreach ($r in $catalog) {
    if ($r.nutrition.calories -le 0 -or $r.description -notmatch 'USDA' -or $r.description -notmatch 'ước tính') { throw "Missing provenance: $($r.slug)" }
    if (@($r.ingredients | Where-Object { $_.amount -le 0 -or -not $_.unit }).Count) { throw "Missing quantities: $($r.slug)" }
}
Write-Output 'PASS: 11 complete detail records, weighted nutrition, servings, provenance and unknown-vs-zero handling.'
if (-not $Live) { return }
$list=(Invoke-RestMethod "$BaseUrl/api/recipes/filter" -Method Post -ContentType application/json -Body '{"page":0,"size":50}').data
if ($list.totalElements -ne 14) { throw 'Expected existing 14 recipes.' }
foreach ($r in $catalog) {
    $item=@($list.items | Where-Object slug -eq $r.slug)
    if ($item.Count -ne 1) { throw "Missing/duplicate recipe: $($r.slug)" }
    $headers=@{}
    if ($AccessToken) { $headers.Authorization="Bearer $AccessToken" }
    $actual=(Invoke-RestMethod "$BaseUrl/api/recipes/$($item[0].recipeId)" -Headers $headers).data
    foreach ($key in @('calories','proteinGrams','carbsGrams','fatGrams','fiberGrams')) {
        if ($actual.$key -ne $r.nutrition[$key]) { throw "Nutrition mismatch $key $($r.slug)" }
    }
    if ($actual.servings -ne $r.servings -or $actual.instructions.Count -ne $r.instructions.Count -or
        $actual.ingredients.Count -ne $r.ingredients.Count -or $actual.isVerified) {
        throw "Detail mismatch: $($r.slug)"
    }
    if ($AccessToken) {
        if ($actual.videos.Count -ne 1 -or $actual.videos[0].videoUrl -ne "https://www.youtube.com/watch?v=$($r.youtubeId)") { throw "Video mismatch: $($r.slug)" }
    } elseif ($actual.videos.Count -ne 0) { throw "Guest must not receive video links: $($r.slug)" }
    foreach ($i in $r.ingredients) {
        $actualIngredient=@($actual.ingredients | Where-Object name -eq $i.name)
        if ($actualIngredient.Count -ne 1 -or $actualIngredient[0].amount -ne $i.amount -or $actualIngredient[0].unit -ne $i.unit) { throw "Ingredient mismatch: $($i.name)" }
    }
    if ($r.category -notin $actual.tags.code) { throw "Missing category: $($r.slug)" }
}
$old=@{'nam-dui-ga-xao-sa-ot-gion-thom'=165;'canh-cai-ngot-nau-nam-bao-ngu'=110;'dau-phu-sot-ca-chua-tuoi'=240}
foreach ($slug in $old.Keys) {
    $r=$list.items | Where-Object slug -eq $slug
    if ($r.calories -ne $old[$slug] -or -not $r.isVerified) { throw "Existing recipe changed: $slug" }
}
Write-Output 'PASS: all 11 live APIs match generated details; original 3 recipes preserved; video access follows login state.'
