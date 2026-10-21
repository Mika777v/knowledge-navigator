import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { AuditLogViewer } from "./audit-log-viewer";

const entry = (id: number, action: string) => ({
  id,
  occurredAt: "2026-10-21T03:00:00Z",
  actor: { id: "u1", displayName: "伊藤 誠", email: "ito@example.com" },
  action,
  resourceType: "NAVIGATION_KEYWORD",
  resourceId: "0192f1c4-aaaa-bbbb-cccc-000000000000",
  details: { term: "社食" },
});

describe("AuditLogViewer", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("新しい順に表示し、続きを読み込める", async () => {
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const url = String(input);
      return url.includes("cursor=2")
        ? Response.json({ items: [entry(1, "NAVIGATION_KEYWORD_ADDED")], nextCursor: null })
        : Response.json({ items: [entry(3, "NAVIGATION_KEYWORD_DELETED"), entry(2, "NAVIGATION_KEYWORD_ADDED")], nextCursor: 2 });
    });
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<AuditLogViewer />);

    expect(await screen.findByText("NAVIGATION_KEYWORD_DELETED")).toBeInTheDocument();
    expect(screen.getByText("キーワードを削除")).toBeInTheDocument();
    expect(screen.getAllByText("伊藤 誠")).toHaveLength(2);
    expect(screen.getAllByText("社食")).toHaveLength(2);
    expect(screen.getAllByText("2026/10/21 12:00")).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "さらに読み込む" }));
    await waitFor(() => expect(screen.getAllByRole("row")).toHaveLength(4));
    expect(screen.queryByRole("button", { name: "さらに読み込む" })).toBeNull();
  });

  it("絞り込みの条件を API に渡す", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json({ items: [], nextCursor: null }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<AuditLogViewer />);
    expect(await screen.findByText("該当する記録はありません。")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("対象"), { target: { value: "DOCUMENT" } });
    fireEvent.change(screen.getByLabelText("操作"), { target: { value: " DOCUMENT_PUBLISHED " } });
    fireEvent.click(screen.getByRole("button", { name: "絞り込む" }));

    await waitFor(() => {
      const url = String(fetchMock.mock.calls.at(-1)?.[0]);
      expect(url).toContain("resourceType=DOCUMENT");
      expect(url).toContain("action=DOCUMENT_PUBLISHED");
    });
  });
});
