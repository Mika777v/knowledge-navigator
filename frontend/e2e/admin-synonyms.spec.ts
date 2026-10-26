import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("コンテンツ担当者が類義語を登録すると、その言葉でも同じ FAQ が見つかる", async ({ page }) => {
  // 毎回ちがう言葉にする（同じ環境で何度実行しても重複にならないように）
  const word = `ぱすわ${Date.now().toString(36)}`;
  await login(page, "ito@example.com");

  const before = await (await page.request.get("/api/v1/navigate?q=" + encodeURIComponent(word))).json();
  expect(before.fallback).toBe(true);

  await page.getByRole("link", { name: "管理" }).click();
  await page.getByRole("link", { name: "類義語辞書" }).first().click();
  await page.getByLabel("代表の言葉").first().fill(word);
  await page.getByLabel("同じ意味の言葉（「、」で区切る）").first().fill("パスワード");
  await page.getByRole("button", { name: "追加" }).click();

  const terms = page.getByRole("list", { name: `「${word}」と同じ意味の言葉` });
  await expect(terms.getByText("パスワード", { exact: true })).toBeVisible();

  const after = await (await page.request.get("/api/v1/navigate?q=" + encodeURIComponent(word))).json();
  expect(after.faqs.map((f: { code: string }) => f.code)).toContain("FAQ-043");

  // 後片付け（「パスワード」はほかのグループに入れられないため、次の実行のために消す）
  page.once("dialog", (d) => d.accept());
  await terms.locator("xpath=ancestor::li[1]").getByRole("button", { name: "削除" }).click();
  await expect(terms).toHaveCount(0);
});
