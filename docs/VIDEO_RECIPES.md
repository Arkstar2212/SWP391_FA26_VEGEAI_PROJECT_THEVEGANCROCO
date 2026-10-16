# Video recipe catalog

`data/video-recipes.json` contains 11 short recipe summaries sourced from the
original Ducan Kitchen pages credited in each entry. Nine existing published
YouTube videos are linked; condensed milk and plum syrup/jam each contain two
recipes. The shopping vlog is deliberately not converted into a recipe.

## Import

With the existing PostgreSQL container running, from the backend directory:

```powershell
.\scripts\Import-VideoRecipes.ps1 -WhatIf
.\scripts\Import-VideoRecipes.ps1
```

Container, database and user can be overridden with `-Container`, `-Database`
and `-DatabaseUser`. This script targets PostgreSQL 16 and requires a database
user allowed to read the copied catalog file inside the container.

The import is transactional, resolves videos by their original YouTube URL,
and inserts only missing slugs. It never updates or deletes existing recipes,
ingredients, videos or accounts. Repeating the import reports zero new recipes.
Do not rename slugs to force an update; use the existing admin recipe editor.

## Detailed standardized recipes

To enrich the imported recipes after the initial import:

```powershell
.\scripts\Update-VideoRecipeDetails.ps1 -WhatIf
.\scripts\Update-VideoRecipeDetails.ps1
.\scripts\Test-VideoRecipeDetails.ps1 -Live
```

This separate, explicit update targets exactly the 11 unverified video-linked
recipes. It checks video links and ingredient identities before updating, fails
atomically if the structure differs, preserves ingredient IDs, and never modifies
the original three recipes. Repeating it does not add duplicate recipes or links.
PowerShell 7 is required for these detail scripts.

`data/video-recipe-details.json` records standardized serving counts, quantified
ingredients, revised instructions, dish roles and assumptions. These are adapted
recipes, not claims that the original author supplied all quantities. Nutrition
uses `sum(edible ingredient grams * nutrient per 100 g / 100) / servings`.
Ingredient amounts are for the whole batch; stored calories and macros are per
serving. Calories round to an integer, macros to one decimal, only after summation.
Approximate 1 ml = 1 g conversions for milk and coconut water are explicitly noted.
Frying oil means estimated oil retained, not the volume of an entire frying bath.
Discarded tea, lemongrass and wrapping leaves are excluded from the calculation.
Cooked yield, absorption, fermentation and brand differences introduce uncertainty.

`data/video-food-profiles.json` is a small reproducible reference snapshot from
[USDA SR Legacy April 2018](https://fdc.nal.usda.gov/download-datasets/), with FDC
IDs and source links. It is not a lab analysis of the completed dishes. Proxy
ingredients (instant noodles, brown-rice noodles, king oyster mushrooms, taro
shoots and tapioca) are identified in notes. Missing source nutrients remain null:
fresh lotus seeds and taro shoots have no fiber value in this dataset, so the two
affected recipes do not assert a fiber total. Never substitute missing data with 0.
To regenerate the reference snapshot from the public USDA CSV archive:

```powershell
.\scripts\Build-VideoFoodProfiles.ps1 -DatasetDirectory C:\path\to\extracted-usda-csv
```

Recipes remain unverified, with visible estimated-nutrition wording. Condiments,
desserts and explicitly classified side dishes are not independent meal-plan
candidates. Main dishes still require complete nutrition and quantified
ingredients plus the existing diet/allergy checks. The lotus rice remains
ineligible while fiber is unknown. No live Gemini calls are needed for the import
or calculation; meal-plan tests use the existing provider test fixture.

## Original import data quality

- Source quantities and units are retained; unspecified amounts remain null.
- Serving ranges and preparation/waiting notes remain in descriptions instead
  of being silently converted into invented integer serving counts.
- In the base import, nutrition is intentionally null and `is_verified` is false. The repetitive,
  incomplete nutrition metadata on the source pages is not treated as verified.
- Before enrichment these recipes are public for browsing and video playback, but are excluded
  by the meal planner's completeness check until an admin supplies validated
  nutrition, servings and quantified ingredients. No AI calls are made on import.
- Ingredient allergy labels are conservative, not a food-safety certification.
  Soy yogurt is not tagged vegan because the starter's composition is unclear.
- Instructions are concise paraphrases, not copies of the original articles.
  Source links and original video thumbnails are preserved for attribution.
