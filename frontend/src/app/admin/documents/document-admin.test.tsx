import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { EditDocument } from "./[id]/edit-document";
import { withoutFrontMatter } from "./[id]/versions-panel";
import type { AdminDocument, VersionDetail, VersionSummary } from "./document-api";

vi.mock("next/navigation", () => ({ useParams: () => ({ id: "d1" }), useRouter: () => ({ push: vi.fn() }) }));

const doc = (over: Partial<AdminDocument> = {}): AdminDocument => ({
  id: "d1",
  code: "REG-GA-001",
  title: "慶弔見舞金規程",
  summary: "慶弔時の祝金・見舞金",
  documentType: "REGULATION",
  category: { slug: "welfare", name: "福利厚生" },
  ownerDepartment: { code: "GA", name: "総務部" },
  visibility: "INTERNAL",
  status: "PUBLISHED",
  currentVersionId: "v1",
  reviewDueOn: null as never,
  rowVersion: 4,
  ...over,
});

const summary = (over: Partial<VersionSummary>): VersionSummary => ({
  id: "v1",
  versionLabel: "v2.1",
  effectiveFrom: "2026-04-01",
  changeNote: null as never,
  publishedAt: "2026-03-01T00:00:00Z",
  current: true,
  sectionCount: 12,
  ...over,
});

const version = (over: Partial<VersionDetail> = {}): VersionDetail => ({
  id: "v1",
  versionLabel: "v2.1",
  effectiveFrom: "2026-04-01",
  changeNote: null as never,
  publishedAt: "2026-03-01T00:00:00Z",
  bodyMarkdown: "### 第5条（結婚祝金）\n\n30,000円を支給する。",
  sections: [{ id: "s5", ordinal: 5, path: null as never, articleNo: "第5条", heading: "結婚祝金", content: "30,000円" }],
  ...over,
});

type Call = { url: string; method: string; ifMatch: string | null; body: unknown };

function stubApi(handle: (call: Call) => Response | undefined) {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>(async (input, init) => {
      const call = {
        url: String(input),
        method: init?.method ?? "GET",
        ifMatch: new Headers(init?.headers).get("If-Match"),
        body: init?.body ? JSON.parse(String(init.body)) : undefined,
      };
      calls.push(call);
      const res = handle(call);
      if (res) return res;
      if (call.url === "/api/v1/categories") return Response.json([{ slug: "welfare", name: "福利厚生", children: [] }]);
      if (call.url === "/api/v1/admin/documents/d1" && call.method === "GET") return Response.json(doc());
      throw new Error(`unexpected ${call.method} ${call.url}`);
    }),
  );
  return calls;
}

describe("withoutFrontMatter", () => {
  it("先頭の front matter だけを除く", () => {
    expect(withoutFrontMatter("---\ncode: REG-1\ntitle: 規程\n---\n\n### 第1条（目的）\n\n本文\n\n---\n")).toBe(
      "### 第1条（目的）\n\n本文\n\n---\n",
    );
    expect(withoutFrontMatter("### 第1条\n本文")).toBe("### 第1条\n本文");
  });
});

describe("EditDocument", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("文書の情報を、版番号を付けて保存する", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") return Response.json([summary({})]);
      if (url.endsWith("/versions/v1")) return Response.json(version());
      if (url === "/api/v1/admin/documents/d1" && method === "PUT") return Response.json(doc({ rowVersion: 5, title: "慶弔見舞金規程（改）" }));
    });
    renderWithQuery(<EditDocument />);

    fireEvent.change(await screen.findByLabelText("文書名"), { target: { value: "慶弔見舞金規程（改）" } });
    fireEvent.click(screen.getByRole("button", { name: "文書の情報を保存" }));

    expect(await screen.findByText("文書の情報を保存しました。")).toBeInTheDocument();
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.ifMatch).toBe('"4"');
    expect(put?.body).toMatchObject({ title: "慶弔見舞金規程（改）", documentType: "REGULATION", categorySlug: "welfare" });
  });

  it("新しい版は、最新の版の本文を初期値にして下書きで作る", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") return Response.json([summary({})]);
      if (url.endsWith("/versions/v1")) return Response.json(version());
      if (url.endsWith("/versions") && method === "POST") {
        return Response.json(version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never }), { status: 201 });
      }
      if (url.endsWith("/versions/v2")) return Response.json(version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never }));
    });
    renderWithQuery(<EditDocument />);

    fireEvent.click(await screen.findByRole("button", { name: "新しい版を作る" }));
    const body = await screen.findByLabelText("本文（Markdown）");
    expect(body).toHaveValue("### 第5条（結婚祝金）\n\n30,000円を支給する。");
    fireEvent.change(screen.getByLabelText("版"), { target: { value: "v2.2" } });
    fireEvent.change(screen.getByLabelText("施行日"), { target: { value: "2026-10-01" } });
    fireEvent.click(screen.getByRole("button", { name: "下書きとして作成" }));

    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({
      versionLabel: "v2.2",
      effectiveFrom: "2026-10-01",
      bodyMarkdown: "### 第5条（結婚祝金）\n\n30,000円を支給する。",
    });
    expect(await screen.findByText("下書き（編集できます）")).toBeInTheDocument();
  });

  it("下書きは保存して条の分かれ方を確かめ、保存してから文書の版番号で公開する", async () => {
    const draft = version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never, sections: [] });
    const saved = version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never });
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") {
        return Response.json([summary({ id: "v2", versionLabel: "v2.2", publishedAt: null as never, current: false, sectionCount: 0 }), summary({})]);
      }
      if (url.endsWith("/versions/v2") && method === "GET") return Response.json(draft);
      if (url.endsWith("/versions/v2") && method === "PUT") return Response.json(saved);
      if (url.endsWith("/publish")) return Response.json(doc({ rowVersion: 5, currentVersionId: "v2" }));
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    renderWithQuery(<EditDocument />);

    expect(await screen.findByText("下書き（編集できます）")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "下書きを保存" }));
    expect(await screen.findByText("保存しました。本文は 1 個の条・見出しに分かれました。")).toBeInTheDocument();
    const split = screen.getByText(/条・見出しの分かれ方/).closest("details")!;
    expect(within(split).getByText("第5条（結婚祝金）")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "保存してこの版を公開する" }));
    expect(await screen.findByText("v2.2 を公開しました。")).toBeInTheDocument();
    const publish = calls.find((c) => c.url.endsWith("/publish"));
    expect(publish?.ifMatch).toBe('"4"');
    expect(publish?.body).toEqual({ versionId: "v2" });
    // 公開の前に本文を保存している
    expect(calls.filter((c) => c.method === "PUT").length).toBe(2);
  });
});
