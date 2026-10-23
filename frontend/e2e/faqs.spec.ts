import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("社員が検索結果から FAQ の一覧を開き、カテゴリと質問の文で絞り込める", async ({ page }) => {
  await login(page, "sato@example.com");
  await page.goto("/search?q=" + encodeURIComponent("結婚したら何か制度ある？"));
  await page.getByRole("link", { name: "よくある質問をすべて見る" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "よくある質問" })).toBeVisible();

  await page.getByRole("navigation", { name: "カテゴリで絞り込む" }).getByRole("link", { name: "福利厚生" }).click();
  await expect(page.getByRole("status")).toHaveText(/「福利厚生」の質問 \d+ 件/);
  await page.getByLabel("質問の文で絞り込む").fill("結婚祝金");
  const link = page.getByRole("link", { name: /結婚祝金/ }).first();
  await expect(link).toBeVisible();

  // 詳細から一覧へ戻れる
  await link.click();
  await page.getByRole("main").getByRole("link", { name: "よくある質問" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "よくある質問" })).toBeVisible();
});
