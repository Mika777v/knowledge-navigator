import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("一般社員には管理メニューを出さない", async ({ page }) => {
  await login(page, "sato@example.com");
  await expect(page.getByRole("link", { name: "管理" })).toHaveCount(0);
  await page.goto("/admin/dictionary");
  await expect(page.getByText("管理画面は、コンテンツ担当者とシステム管理者だけが利用できます。")).toBeVisible();
});

test("コンテンツ担当者が辞書に言葉を追加すると、検索の案内が変わる", async ({ page }) => {
  // 毎回ちがう言葉にする（同じ環境で何度実行しても重複にならないように）
  const term = `社食${Date.now().toString(36)}`;
  await login(page, "ito@example.com");

  const before = await page.request.get("/api/v1/navigate?q=" + encodeURIComponent(term));
  expect((await before.json()).fallback).toBe(true);

  await page.getByRole("link", { name: "管理" }).click();
  await page.getByRole("link", { name: "キーワード辞書" }).first().click();
  await page.getByLabel("種類", { exact: true }).selectOption("CONTACT");
  const option = page.getByLabel("案内先", { exact: true }).locator("option", { hasText: "GA-GENERAL" });
  await expect(option).toHaveCount(1);
  await page.getByLabel("案内先", { exact: true }).selectOption((await option.getAttribute("value")) ?? "");
  await page.getByLabel("追加する言葉").fill(term);
  await page.getByRole("button", { name: "追加" }).click();
  await expect(page.getByRole("cell", { name: term })).toBeVisible();

  const after = await (await page.request.get("/api/v1/navigate?q=" + encodeURIComponent(term))).json();
  expect(after.contacts.map((c: { code: string }) => c.code)).toContain("GA-GENERAL");
});

test("システム管理者は分析と監査ログを見られる", async ({ page }) => {
  await login(page, "watanabe@example.com");
  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "何も見つからなかった質問" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "フィードバック" })).toBeVisible();

  await page.getByRole("link", { name: "監査ログ" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "監査ログ" })).toBeVisible();
  await expect(page.getByRole("table").or(page.getByText("該当する記録はありません。"))).toBeVisible();
});
