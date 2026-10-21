import { fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { ContactDirectory } from "./contact-directory";

const contact = (code: string, name: string, over: object = {}) => ({
  code,
  name,
  description: null,
  email: null,
  phoneExt: null,
  chatUrl: null,
  officeHours: null,
  primary: false,
  ...over,
});

const directory = [
  { code: "HR", name: "人事部", description: "採用・労務・福利厚生", parentCode: null, contacts: [
    contact("HR-GENERAL", "人事部 総合窓口", { primary: true, phoneExt: "1100" }),
    contact("HR-WELFARE", "人事部 福利厚生担当", { email: "welfare@example.com", description: "慶弔金・社宅" }),
  ] },
  { code: "IT", name: "情報システム部", description: null, parentCode: null, contacts: [
    contact("IT-HELPDESK", "ヘルプデスク", { description: "PC・アカウントの不具合" }),
  ] },
  { code: "IT-DEV", name: "開発第一課", description: null, parentCode: "IT", contacts: [] },
];

describe("ContactDirectory", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(Response.json(directory))));
  afterEach(() => vi.unstubAllGlobals());

  it("部署ごとに窓口を出し、代表窓口と連絡先を示す（窓口の無い部署は出さない）", async () => {
    renderWithQuery(<ContactDirectory />);
    const hr = await screen.findByRole("region", { name: "人事部" });
    expect(within(hr).getByText("代表窓口")).toBeInTheDocument();
    expect(within(hr).getByText("1100")).toBeInTheDocument();
    expect(within(hr).getByRole("link", { name: "welfare@example.com" })).toHaveAttribute("href", "mailto:welfare@example.com");
    expect(screen.queryByRole("region", { name: "開発第一課" })).toBeNull();
  });

  it("窓口の説明でも絞り込める", async () => {
    renderWithQuery(<ContactDirectory />);
    await screen.findByRole("region", { name: "人事部" });

    fireEvent.change(screen.getByLabelText("部署・窓口で絞り込む"), { target: { value: "pc" } });
    expect(screen.getByRole("region", { name: "情報システム部" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "人事部" })).toBeNull();

    fireEvent.change(screen.getByLabelText("部署・窓口で絞り込む"), { target: { value: "経理" } });
    expect(screen.getByText("「経理」に合う部署・窓口はありません。")).toBeInTheDocument();
  });

  it("部署名が合えば、その部署の窓口をすべて出す", async () => {
    renderWithQuery(<ContactDirectory />);
    await screen.findByRole("region", { name: "人事部" });

    fireEvent.change(screen.getByLabelText("部署・窓口で絞り込む"), { target: { value: "人事部" } });
    const hr = screen.getByRole("region", { name: "人事部" });
    expect(within(hr).getByText("人事部 福利厚生担当")).toBeInTheDocument();
    expect(within(hr).getByText("人事部 総合窓口")).toBeInTheDocument();
  });
});
