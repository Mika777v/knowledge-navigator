import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("コンテンツ担当者がきっかけを作ると、ホームの「きっかけから探す」に出る", async ({ page, browser }) => {
  // 同じ環境で何度実行しても重ならないよう、実行ごとにちがうコード・名前にする
  const run = Date.now().toString(36).toUpperCase();
  const name = `E2E${run}`;
  await login(page, "takahashi@example.com");
  await page.goto("/admin/life-events/new");
  await page.getByLabel(/コード/).fill(`E2E-${run}`);
  await page.getByLabel("名前").fill(name);
  await page.getByRole("button", { name: "制度を追加" }).click();
  const option = page.getByLabel("制度 1", { exact: true }).locator("option", { hasText: "育児休業（PRG-CHILDCARE-LEAVE）" });
  await expect(option).toHaveCount(1);
  await page.getByLabel("制度 1", { exact: true }).selectOption("PRG-CHILDCARE-LEAVE");
  await page.getByRole("button", { name: "作成" }).click();
  await expect(page).toHaveURL(new RegExp(`/admin/life-events/E2E-${run}$`));

  const employee = await browser.newPage();
  await login(employee, "sato@example.com");
  await employee.getByRole("link", { name }).click();
  await expect(employee).toHaveURL(/\/search\?q=/);
});
