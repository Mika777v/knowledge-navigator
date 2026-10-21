import { expect, test } from "@playwright/test";
import { collectErrors, login } from "./helpers";

test("未ログインならログイン画面へ移動し、ログイン後は元のページに戻る", async ({ page }) => {
  await page.goto("/search?q=" + encodeURIComponent("結婚"));
  await expect(page).toHaveURL(/\/login\?next=/);
  await page.getByLabel("メールアドレス").fill("sato@example.com");
  await page.getByLabel("パスワード").fill(process.env.DEMO_USER_PASSWORD ?? "demo-pass-2026");
  await page.getByRole("button", { name: "ログイン" }).click();
  await expect(page).toHaveURL(/\/search\?q=/);
  await expect(page.getByRole("heading", { name: "使える制度" })).toBeVisible();
});

test("質問から制度・規程・窓口を案内し、条文へ移動できる", async ({ page }) => {
  const errors = collectErrors(page);
  await login(page, "sato@example.com");

  await page.getByRole("searchbox").fill("結婚したら何か制度ある？");
  await page.getByRole("button", { name: "検索" }).click();

  await expect(page.getByText("「結婚」のときに使える制度と手続きを表示しています。")).toBeVisible();
  const programs = page.getByRole("region", { name: "使える制度" });
  await expect(programs.getByRole("link", { name: "結婚祝金" })).toBeVisible();
  await expect(programs.getByRole("link", { name: "結婚休暇" })).toBeVisible();
  await expect(page.getByRole("region", { name: "問い合わせ先" }).getByText("総務部 庶務グループ")).toBeVisible();

  // 規程の条へ移動すると、その条までスクロールし、検索語が強調されている
  await page.getByRole("link", { name: "第5条（結婚祝金）" }).first().click();
  await expect(page).toHaveURL(/\/documents\/.+hl=.+#section-/);
  const article = page.locator("section", { has: page.getByRole("heading", { name: "第5条（結婚祝金）" }) });
  await expect(article).toBeInViewport();
  await expect(article.locator("mark").first()).toHaveText("結婚");

  expect(errors).toEqual([]);
});

test("該当がなければ総合窓口を案内し、フィードバックを送れる", async ({ page }) => {
  await login(page, "suzuki@example.com");
  await page.goto("/search?q=" + encodeURIComponent("ぴよぴよ"));

  await expect(page.getByRole("status")).toHaveText(/該当する制度・規程が見つかりませんでした/);
  await expect(page.getByRole("region", { name: "問い合わせ先" }).getByText("該当する案内が見つからないときの総合窓口")).toBeVisible();

  await page.getByRole("button", { name: "いいえ" }).click();
  await page.getByLabel("探していたもの（任意）").fill("E2E テストのコメント");
  await page.getByRole("button", { name: "送信" }).click();
  await expect(page.getByText(/ご協力ありがとうございました/)).toBeVisible();
});

test("制度の詳細から根拠の規程へ移動できる", async ({ page }) => {
  await login(page, "sato@example.com");
  await page.goto("/search?q=" + encodeURIComponent("育休を取りたい"));
  await page.getByRole("region", { name: "使える制度" }).getByRole("link", { name: "育児休業", exact: true }).click();

  await expect(page.getByRole("heading", { level: 1, name: "育児休業" })).toBeVisible();
  await expect(page.getByRole("region", { name: "根拠となる規程" })).toBeVisible();
  await page.getByRole("region", { name: "根拠となる規程" }).getByRole("link").first().click();
  await expect(page).toHaveURL(/\/documents\//);
  await expect(page.getByRole("navigation", { name: "目次" })).toBeVisible();
});
