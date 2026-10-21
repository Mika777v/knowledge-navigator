import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("システム管理者が部署と、その部署の窓口を追加できる", async ({ page }) => {
  // 同じ環境で何度実行しても重ならないよう、実行ごとにちがうコード・名前にする
  const run = Date.now().toString(36).toUpperCase();
  await login(page, "watanabe@example.com");
  await page.goto("/admin");
  await page.getByRole("navigation", { name: "管理メニュー" }).getByRole("link", { name: "組織" }).click();
  await expect(page.getByRole("heading", { name: "組織" })).toBeVisible();

  await page.getByRole("button", { name: "部署を追加" }).click();
  await page.getByLabel("コード").fill(`E2E-${run}`);
  await page.getByLabel("名前").fill(`E2E部${run}`);
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.getByRole("status")).toHaveText(`E2E部${run}を保存しました。`);
  await expect(page.getByRole("button", { name: `E2E部${run}を編集` })).toBeVisible();

  await page.getByRole("link", { name: "窓口" }).click();
  await page.getByRole("button", { name: "窓口を追加" }).click();
  await page.getByLabel("コード").fill(`E2E-${run}-DESK`);
  await page.getByLabel("名前").fill(`E2E窓口${run}`);
  await page.getByLabel("部署", { exact: true }).selectOption(`E2E-${run}`);
  await page.getByLabel("内線").fill("9999");
  await page.getByLabel("部署の代表窓口").check();
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.getByRole("status")).toHaveText(`E2E窓口${run}を保存しました。`);
  await expect(page.getByRole("row", { name: new RegExp(`E2E窓口${run}`) })).toContainText("代表窓口");
});

test("コンテンツ担当者には組織の管理を出さない", async ({ page }) => {
  await login(page, "takahashi@example.com");
  await page.goto("/admin");
  await expect(page.getByRole("navigation", { name: "管理メニュー" }).getByRole("link", { name: "規程" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "管理メニュー" }).getByRole("link", { name: "組織" })).toHaveCount(0);
});
