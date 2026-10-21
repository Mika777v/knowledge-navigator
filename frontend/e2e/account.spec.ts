import { expect, type Page, test } from "@playwright/test";
import { login, PASSWORD } from "./helpers";

async function changePassword(page: Page, current: string, next: string) {
  await page.goto("/account");
  await page.getByLabel("今のパスワード").fill(current);
  await page.getByLabel("新しいパスワード（8文字以上）").fill(next);
  await page.getByLabel("新しいパスワード（確認）").fill(next);
  await page.getByRole("button", { name: "変更する" }).click();
  await expect(page.getByRole("status")).toHaveText(/パスワードを変更しました。/);
}

test("社員が自分のパスワードを変えると、新しいパスワードでログインできる", async ({ page, browser }) => {
  const changed = `${PASSWORD}-e2e`;
  await login(page, "yamamoto@example.com");
  await page.getByRole("link", { name: "アカウント" }).click();
  await expect(page.getByRole("heading", { name: "アカウント" })).toBeVisible();
  await expect(page.getByRole("main").getByText("西日本営業課")).toBeVisible();

  try {
    await changePassword(page, PASSWORD, changed);

    // 別のブラウザで、新しいパスワードでログインできることを確かめる
    const other = await browser.newPage();
    await login(other, "yamamoto@example.com", changed);
    await other.close();
  } finally {
    // ほかのテストや次の実行のため、デモのパスワードに戻す（変更した画面はログインしたまま）
    await changePassword(page, changed, PASSWORD);
  }
});
