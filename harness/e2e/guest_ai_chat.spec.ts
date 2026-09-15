import { test, expect } from '@playwright/test';

test.describe('FR-CHAT-01: Unauthorized User Limited AI Chatbot Trial', () => {
  test.beforeEach(async ({ page }) => {
    // Mock the backend AI chat endpoint to prevent live token consumption and network flakiness
    await page.route('**/api/ai/chatbot/query', async (route) => {
      const request = route.request();
      const postData = JSON.parse(request.postData() || '{}');

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          reply: `Phản hồi giả lập AI cho câu hỏi: "${postData.message}". Ăn chay cung cấp nhiều chất xơ và vitamin dồi dào!`,
          remainingTrials: Math.max(0, 3 - (postData.trialCount || 1)),
        }),
      });
    });
  });

  test('Guest can ask up to 3 questions, 4th question triggers registration prompt', async ({ page }) => {
    await page.goto('/');

    // Open AI Chatbot floating widget
    const chatButton = page.locator('#ai-chat-floating-button');
    await chatButton.click();

    const chatInput = page.locator('#ai-chat-input');
    const sendButton = page.locator('#ai-chat-send-btn');

    // Questions 1 to 3: should succeed
    for (let i = 1; i <= 3; i++) {
      await chatInput.fill(`Câu hỏi thử nghiệm ${i}: Người ăn chay nên ăn gì để đủ protein?`);
      await sendButton.click();
      await expect(page.locator('.ai-chat-bubble').last()).toContainText('Phản hồi giả lập AI');
    }

    // 4th Question: quota exceeded
    await chatInput.fill('Câu hỏi thứ 4 vượt hạn mức');
    await sendButton.click();

    // Verify registration lock modal is displayed
    const registrationModal = page.locator('#ai-trial-limit-modal');
    await expect(registrationModal).toBeVisible();
    await expect(registrationModal).toContainText('Đăng ký để tiếp tục trò chuyện với AI');

    // Clicking register directs to registration form
    await page.locator('#modal-register-btn').click();
    await expect(page).toHaveURL(/.*\/register/);
  });
});
