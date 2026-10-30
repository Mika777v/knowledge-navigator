import { expect, test } from "@playwright/test";
import { collectErrors, login, uniqueLetters } from "./helpers";

// CI では AI をオフにしているため、回答は「使えない設定」になるが、質問と結果は履歴に残る（ADR-0014）
test("AI に質問すると履歴に残り、本人が開いて削除できる。他の人には見えない", async ({ page, browser }) => {
  const errors = collectErrors(page);
  const question = `結婚祝金 ${uniqueLetters()}`;
  await login(page, "sato@example.com");
  await page.goto("/assistant?q=" + encodeURIComponent(question));
  const answer = page.getByRole("region", { name: "AI の回答" });
  await expect(answer.getByRole("status")).toHaveText(/AI による回答は、いまは使えない設定/);

  // 記録は回答を送り終えた後なので、出るまで開き直す
  const item = page.getByRole("link", { name: question });
  await expect(async () => {
    await page.getByRole("link", { name: "AI の履歴" }).click();
    await expect(item).toBeVisible({ timeout: 1000 });
  }).toPass();

  await item.click();
  await expect(page.getByRole("heading", { level: 1, name: question })).toBeVisible();
  await expect(page.getByText(/AI による回答は、いまは使えない設定/)).toBeVisible();
  const url = page.url();

  // 別の社員には、一覧にも出ず、URL を開いても見られない
  const other = await browser.newPage();
  await login(other, "suzuki@example.com");
  await other.goto("/assistant/history");
  await expect(other.getByRole("heading", { level: 1, name: "AI の回答の履歴" })).toBeVisible();
  await expect(other.getByRole("link", { name: question })).toHaveCount(0);
  await other.goto(url);
  await expect(other.getByRole("alert")).toBeVisible();
  await other.close();

  // 本人は削除できる
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "この会話を削除する" }).click();
  await expect(page).toHaveURL(/\/assistant\/history$/);
  await expect(page.getByRole("link", { name: question })).toHaveCount(0);

  expect(errors).toEqual([]);
});
