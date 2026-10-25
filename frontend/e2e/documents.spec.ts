import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("社員がホームから規程の一覧を開き、タグで絞り込める", async ({ page }) => {
  await login(page, "sato@example.com");
  await page.getByRole("link", { name: "規程の一覧" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "規程の一覧" })).toBeVisible();
  await expect(page.getByRole("link", { name: "情報セキュリティ規程" })).toBeVisible();

  await page.getByRole("navigation", { name: "タグで絞り込む" }).getByRole("link", { name: /^お金（\d+）$/ }).click();
  await expect(page.getByText("「お金」のタグが付いた規程を表示しています。")).toBeVisible();
  await expect(page.getByRole("link", { name: "通勤手当規程" })).toBeVisible();
  await expect(page.getByRole("link", { name: "情報セキュリティ規程" })).toHaveCount(0);

  // 規程の詳細のタグからも、同じタグの一覧へ移動できる
  await page.getByRole("link", { name: "通勤手当規程" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "通勤手当規程" })).toBeVisible();
  // make seed で原本（data/pdf）を結び付けているので、原本と条ごとのページを開ける
  await expect(page.getByRole("link", { name: "原本（PDF）を開く" })).toHaveAttribute("href", /\/source$/);
  await expect(page.getByRole("link", { name: /^第1条（目的）を原本の1ページで開く$/ })).toHaveAttribute("href", /#page=1$/);
  await page.getByRole("list", { name: "タグ", exact: true }).getByRole("link", { name: "全社員" }).click();
  await expect(page.getByText("「全社員」のタグが付いた規程を表示しています。")).toBeVisible();
});

test("コンテンツ担当者が規程にタグを付けると、社員の一覧に出る", async ({ page, browser }) => {
  // 同じ環境で何度実行しても重ならないよう、実行ごとにちがう名前にする
  const tag = `E2E${Date.now().toString(36).toUpperCase()}`;
  await login(page, "ito@example.com");
  await page.goto("/admin/documents");
  await page.getByRole("link", { name: "慶弔見舞金規程" }).click();
  await page.getByLabel("新しいタグ").fill(tag);
  await page.getByRole("button", { name: "タグを追加" }).click();
  await page.getByRole("button", { name: "文書の情報を保存" }).click();
  await expect(page.getByText("文書の情報を保存しました。")).toBeVisible();

  const employee = await browser.newPage();
  await login(employee, "sato@example.com");
  await employee.goto(`/documents?tag=${encodeURIComponent(tag)}`);
  await expect(employee.getByRole("link", { name: "慶弔見舞金規程" })).toBeVisible();

  // あとかたづけ（付けたタグを外す）
  await page.getByRole("button", { name: tag }).click();
  await page.getByRole("button", { name: "文書の情報を保存" }).click();
  await expect(page.getByText("文書の情報を保存しました。")).toBeVisible();
});
