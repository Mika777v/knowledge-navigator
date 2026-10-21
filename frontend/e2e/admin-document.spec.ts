import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("コンテンツ担当者が規程を作って版を公開すると、社員の検索に条文が出る", async ({ page, browser }) => {
  // 同じ環境で何度実行しても重ならないよう、実行ごとにちがうコード・文言にする
  const run = Date.now().toString(36).toUpperCase();
  const word = `E2E${run}`;
  await login(page, "ito@example.com");
  await page.goto("/admin/documents/new");
  await page.getByLabel(/コード/).fill(`REG-E2E-${run}`);
  await page.getByLabel("文書名").fill(`${word} 規程`);
  await page.getByLabel("カテゴリ").selectOption({ index: 1 });
  await page.getByRole("button", { name: "作成して本文の登録へ" }).click();
  await expect(page).toHaveURL(/\/admin\/documents\/[0-9a-f-]{36}$/);

  await page.getByRole("button", { name: "新しい版を作る" }).click();
  await page.getByLabel("版", { exact: true }).fill("v1.0");
  await page.getByLabel("施行日").fill("2026-10-01");
  await page.getByLabel("本文（Markdown）").fill(`### 第1条（目的）\n\n${word} の目的を定める。\n\n### 第2条（申請）\n\n総務部に申請する。`);
  await page.getByRole("button", { name: "下書きとして作成" }).click();
  await expect(page.getByText("下書き（編集できます）")).toBeVisible();

  page.once("dialog", (dialog) => void dialog.accept());
  await page.getByRole("button", { name: "保存してこの版を公開する" }).click();
  await expect(page.getByText("v1.0 を公開しました。")).toBeVisible();

  const employee = await browser.newPage();
  await login(employee, "sato@example.com");
  await employee.goto("/search?q=" + encodeURIComponent(word));
  const documents = employee.getByRole("region", { name: "根拠となる規程" });
  await expect(documents.getByRole("link", { name: `${word} 規程` })).toBeVisible();
  await expect(documents.getByRole("link", { name: "第1条（目的）" })).toBeVisible();
});
