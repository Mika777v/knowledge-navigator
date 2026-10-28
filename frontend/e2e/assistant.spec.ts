import { expect, test } from "@playwright/test";
import { collectErrors, login } from "./helpers";

// CI と make up では AI（ai-service）を動かさないため、回答の機能はオフ（knavi.assistant.enabled=false）。
// オフでも、検索の4ブロックと窓口は出て、回答できない理由を案内することを確かめる（ADR-0013）
test("検索結果から AI の回答の画面へ移り、オフのときも検索結果と窓口を案内する", async ({ page }) => {
  const errors = collectErrors(page);
  await login(page, "sato@example.com");
  await page.goto("/search?q=" + encodeURIComponent("結婚祝金はいくら？"));

  await page.getByRole("link", { name: "AI に根拠付きで答えてもらう" }).click();
  await expect(page).toHaveURL(/\/assistant\?q=/);

  const answer = page.getByRole("region", { name: "AI の回答" });
  await expect(answer.getByRole("status")).toHaveText(/AI による回答は、いまは使えない設定/);
  await expect(answer.getByText(/AI による要約です/)).toBeVisible();
  await expect(page.getByRole("region", { name: "使える制度" }).getByRole("link", { name: "結婚祝金" })).toBeVisible();
  await expect(page.getByRole("region", { name: "問い合わせ先" }).getByText("総務部 庶務グループ")).toBeVisible();

  // 画面の中で質問し直せる
  await page.getByLabel("AI に質問する").fill("忌引は何日？");
  await page.getByRole("button", { name: "質問する" }).click();
  await expect(page).toHaveURL(new RegExp(`q=${encodeURIComponent("忌引は何日？")}`));
  await expect(answer.getByRole("status")).toHaveText(/AI による回答は、いまは使えない設定/);

  expect(errors).toEqual([]);
});
