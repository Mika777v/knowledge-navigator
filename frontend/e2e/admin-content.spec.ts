import { expect, test } from "@playwright/test";
import { login } from "./helpers";

// 同じ環境で何度実行しても重ならないよう、実行ごとにちがうコード・文言にする
const run = Date.now().toString(36).toUpperCase();

test("コンテンツ担当者が FAQ を作って公開すると、社員の検索に出る", async ({ page, browser }) => {
  const question = `E2E${run} の手続きは？`;
  await login(page, "ito@example.com");
  await page.goto("/admin/faqs/new");
  await page.getByLabel(/コード/).fill(`FAQ-E2E-${run}`);
  await page.getByLabel("質問").fill(question);
  await page.getByLabel("回答（Markdown）").fill("総務部に申請してください。");
  await page.getByLabel("カテゴリ").selectOption({ index: 1 });
  await page.getByLabel("問い合わせ先").selectOption("GA-GENERAL");
  await page.getByRole("button", { name: "下書きとして保存" }).click();

  await expect(page).toHaveURL(/\/admin\/faqs\/[0-9a-f-]{36}$/);
  await page.getByRole("button", { name: "保存して公開する" }).click();
  await expect(page.getByRole("status")).toHaveText("公開しました。");

  const employee = await browser.newPage();
  await login(employee, "sato@example.com");
  await employee.goto("/search?q=" + encodeURIComponent(`E2E${run}`));
  await expect(employee.getByRole("region", { name: "よくある質問" }).getByRole("link", { name: question })).toBeVisible();
});

test("コンテンツ担当者が制度の内容を直すと、制度の画面に反映される", async ({ page }) => {
  const benefit = `30,000円（E2E ${run}）`;
  await login(page, "ito@example.com");
  await page.goto("/admin/programs");
  await page.getByRole("link", { name: "結婚祝金", exact: true }).click();

  await page.getByLabel("内容（金額・日数など）").fill(benefit);
  await page.getByRole("button", { name: "保存", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("保存しました。");

  await page.getByRole("link", { name: "利用者向けの画面で見る" }).click();
  await expect(page).toHaveURL(/\/programs\/[0-9a-f-]{36}$/);
  await expect(page.getByRole("article").getByText(benefit)).toBeVisible();
});
