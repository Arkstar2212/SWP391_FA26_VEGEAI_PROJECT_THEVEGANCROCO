import { defineConfig, devices } from '@playwright/test';

/**
 * VEGEAI Automated Test Harness Configuration
 * Designed for headless E2E testing and AI endpoint mocking.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30 * 1000,
  expect: {
    timeout: 5000,
  },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list']
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    headless: true,
  },

  projects: [
    {
      name: 'chromium-headless',
      use: { ...devices['Desktop Chrome'], headless: true },
    },
    {
      name: 'firefox-headless',
      use: { ...devices['Desktop Firefox'], headless: true },
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'], headless: true },
    },
  ],
});
