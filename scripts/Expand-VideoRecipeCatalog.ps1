function Get-ExpandedVideoRecipes {
    $base = @(Get-Content (Join-Path $PSScriptRoot '../data/video-recipes.json') -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable)
    $details = @(Get-Content (Join-Path $PSScriptRoot '../data/video-recipe-details.json') -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable)
    $foods = Get-Content (Join-Path $PSScriptRoot '../data/video-food-profiles.json') -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
    if ($details.Count -ne 11 -or @($details.slug | Sort-Object -Unique).Count -ne 11) { throw 'Expected 11 unique detail records.' }
    foreach ($recipe in $base) {
        $detail = @($details | Where-Object { $_.slug -eq $recipe.slug })
        if ($detail.Count -ne 1) { throw "Missing details: $($recipe.slug)" }
        $detail = $detail[0]
        if ($detail.servings -le 0 -or $detail.prepTimeMinutes -lt 0 -or $detail.cookTimeMinutes -lt 0 -or
            $detail.difficulty -notin @('EASY','MEDIUM','HARD') -or
            $detail.category -notin @('MAIN_DISH','SIDE_DISH','CONDIMENT','DESSERT') -or
            @($detail.instructions).Count -lt 4 -or $detail.ingredients.Count -ne $recipe.ingredients.Count) {
            throw "Invalid details: $($recipe.slug)"
        }
        $totals = @{ calories=0.0; proteinGrams=0.0; carbsGrams=0.0; fatGrams=0.0; fiberGrams=0.0 }
        $missing = [System.Collections.Generic.HashSet[string]]::new()
        foreach ($ingredient in $recipe.ingredients) {
            $inputName = $ingredient.name
            $quantity = $detail.ingredients[$inputName]
            if (-not $quantity) { throw "Missing quantity: $inputName" }
            $ingredient.sourceName = $inputName
            if ($quantity.name) { $ingredient.name = $quantity.name }
            if ($quantity.allergenType) { $ingredient.allergenType = $quantity.allergenType }
            $ingredient.amount = $(if ($quantity.ContainsKey('amount')) { $quantity.amount } else { $quantity.grams })
            $ingredient.unit = $(if ($quantity.unit) { $quantity.unit } else { 'g' })
            if ($ingredient.amount -le 0 -or -not [double]::IsFinite([double]$ingredient.amount)) { throw "Invalid quantity: $inputName" }
            if ($quantity.notEaten) { continue }
            $food = $foods[$quantity.food]
            if (-not $food -or $quantity.grams -le 0) { throw "Missing food reference: $inputName" }
            foreach ($nutrient in @($totals.Keys)) {
                if ($null -eq $food.per100g[$nutrient]) { [void]$missing.Add($nutrient) }
                else { $totals[$nutrient] += $quantity.grams / 100.0 * $food.per100g[$nutrient] }
            }
        }
        $recipe.nutrition = @{}
        foreach ($nutrient in $totals.Keys) {
            $recipe.nutrition[$nutrient] = $(if ($missing.Contains($nutrient)) { $null }
                elseif ($nutrient -eq 'calories') { [int][math]::Round($totals[$nutrient] / $detail.servings, 0, [MidpointRounding]::AwayFromZero) }
                else { [math]::Round($totals[$nutrient] / $detail.servings, 1, [MidpointRounding]::AwayFromZero) })
        }
        foreach ($field in @('servings','prepTimeMinutes','cookTimeMinutes','difficulty','instructions','category')) { $recipe[$field] = $detail[$field] }
        foreach ($step in $recipe.instructions) {
            if ([string]::IsNullOrWhiteSpace($step) -or $step.Length -gt 1000) { throw "Invalid instruction: $($recipe.slug)" }
        }
        $recipe.tags = @($recipe.tags) + $detail.category
        $recipe.description = $detail.summary + "`n" + $detail.notes +
            "`nDinh dưỡng ước tính cho 1/$($detail.servings) mẻ, từ định lượng chuẩn hóa và USDA SR Legacy; chưa thẩm định. Nguyên liệu hiển thị cho cả mẻ, không phải mỗi phần." +
            "`nPhỏng theo Ducan Kitchen; bản chuẩn hóa có điều chỉnh. Nguồn: " + $recipe.sourceUrl
        if ($recipe.description.Length -gt 1000) { throw "Description too long: $($recipe.slug)" }
        $recipe
    }
}
