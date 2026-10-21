import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test("社員がホームから問い合わせ先の一覧を開き、窓口を絞り込める", async ({ page }) => {
  await login(page, "suzuki@example.com");
  await page.getByRole("link", { name: "部署ごとの問い合わせ先の一覧" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "問い合わせ先の一覧" })).toBeVisible();
  await expect(page.getByRole("region", { name: "人事部" }).getByText("人事部 代表")).toBeVisible();

  await page.getByLabel("部署・窓口で絞り込む").fill("経費");
  await expect(page.getByText("経理部 経費精算デスク")).toBeVisible();
  await expect(page.getByRole("region", { name: "人事部" })).toHaveCount(0);
});
