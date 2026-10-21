/** 条の見出し（例: 第5条（結婚祝金）） */
export function articleTitle(articleNo: string | null | undefined, heading: string | null | undefined) {
  if (articleNo && heading) return `${articleNo}（${heading}）`;
  return articleNo || heading || "";
}

/** 文書詳細の中で条へ移動するためのアンカー（id 属性） */
export function sectionAnchor(sectionId: string) {
  return `section-${sectionId}`;
}
