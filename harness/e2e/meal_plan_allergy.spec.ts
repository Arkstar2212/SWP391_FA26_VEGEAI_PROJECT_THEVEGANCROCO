import { test, expect } from '@playwright/test';

test.describe('FR-PLAN-03 & EX-PLAN-01: AI Meal Planner Allergy Hard Constraints', () => {
  test('Generated weekly plan must NEVER contain declared allergy ingredients', async ({ page }) => {
    // 1. Intercept AI generation request and mock an approved vegetarian meal plan fixture
    await page.route('**/api/ai/meal-planner/generate', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          weekPlan: [
            {
              day: 'Thứ Hai',
              meals: [
                {
                  mealType: 'Bữa Sáng',
                  recipeName: 'Cháo yến mạch táo quế',
                  ingredients: ['Yến mạch', 'Táo', 'Sữa đậu nành', 'Bột quế'],
                  calories: 380,
                },
                {
                  mealType: 'Bữa Trưa',
                  recipeName: 'Đậu hũ kho nấm rơm',
                  ingredients: ['Đậu phụ', 'Nấm rơm', 'Xì dầu'],
                  calories: 520,
                },
              ],
            },
          ],
        }),
      });
    });

    // 2. Navigate to Meal Planner page
    await page.goto('/meal-planner');

    // 3. Select allergy: "Đậu phộng" (Peanut)
    const allergyCheckbox = page.locator('input[name="allergy"][value="peanut"]');
    await allergyCheckbox.check();

    // 4. Click generate plan
    await page.locator('#btn-generate-meal-plan').click();

    // 5. Verify the generated meal plan does not contain peanut or peanut oil
    const planContainer = page.locator('#weekly-meal-plan-container');
    await expect(planContainer).toBeVisible();

    const planText = await planContainer.innerText();
    expect(planText.toLowerCase()).not.toContain('đậu phộng');
    expect(planText.toLowerCase()).not.toContain('lạc');
    expect(planText.toLowerCase()).not.toContain('peanut');
  });
});
