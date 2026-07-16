import { defineConfig, devices } from '@playwright/test'

const backendPort = 18082
const frontendPort = 4173

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : 2,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['junit', { outputFile: 'test-results/e2e-junit.xml' }],
  ],
  use: {
    baseURL: `http://127.0.0.1:${frontendPort}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `java -jar ../platform-bootstrap/target/platform-bootstrap-0.1.0-SNAPSHOT.jar --server.port=${backendPort} --spring.profiles.active=local`,
      url: `http://127.0.0.1:${backendPort}/actuator/health`,
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      env: {
        SPRING_DATASOURCE_URL: 'jdbc:h2:mem:platform-e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1',
        PLATFORM_FILE_STORAGE_ROOT: '../target/e2e-file-storage',
        PLATFORM_ALLOWED_ORIGINS: `http://127.0.0.1:${frontendPort}`,
      },
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: `pnpm preview --host 127.0.0.1 --port ${frontendPort}`,
      url: `http://127.0.0.1:${frontendPort}`,
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
      env: {
        VITE_PROXY_TARGET: `http://127.0.0.1:${backendPort}`,
        VITE_TENANT_ID: 'e2e-government',
        VITE_USER_ID: 'e2e-user',
      },
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
})
