import { defineConfig, devices } from "@playwright/test";

/**
 * E2E テスト（ブラウザで画面を操作する）。make up と make seed で起動した環境に対して実行する。
 * CI では ci-compose が実行する。ローカル: pnpm e2e（E2E_BASE_URL で接続先を変えられる）
 */
export default defineConfig({
  testDir: "./e2e",
  // 検索ログ・辞書などのデータを書き換えるテストがあるため、1つずつ順に実行する
  workers: 1,
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    locale: "ja-JP",
    timezoneId: "Asia/Tokyo",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
