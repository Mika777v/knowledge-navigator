"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/http";
import type { CategoryNode, DepartmentContacts, DocumentSummary, ProgramSummary } from "@/lib/api/types";

/**
 * 管理画面の入力欄で選ぶもの（カテゴリ・窓口・制度・規程）。制度と規程は、操作する人が閲覧できる公開中のものだけ。
 * 画面をまたいで同じ問い合わせを使い回す（TanStack Query のキャッシュ）。
 */
export function useContentOptions() {
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => apiFetch<CategoryNode[]>("/api/v1/categories"),
  });
  const contacts = useQuery({
    queryKey: ["contacts"],
    queryFn: () => apiFetch<DepartmentContacts[]>("/api/v1/contacts"),
  });
  const programs = useQuery({
    queryKey: ["programs"],
    queryFn: () => apiFetch<ProgramSummary[]>("/api/v1/programs"),
  });
  const documents = useQuery({
    queryKey: ["documents"],
    queryFn: () => apiFetch<DocumentSummary[]>("/api/v1/documents"),
  });
  return { categories: categories.data, contacts: contacts.data, programs: programs.data, documents: documents.data };
}
