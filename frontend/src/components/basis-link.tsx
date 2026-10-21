import Link from "next/link";
import type { BasisView } from "@/lib/api/types";

/** 制度詳細の根拠（BasisView）と検索結果の根拠（BasisRef）のどちらも受け取る */
type BasisLinkData = Pick<BasisView, "documentId" | "documentTitle"> & {
  sectionId?: string | null;
  articleNo?: string | null;
  heading?: string | null;
};
import { articleTitle, sectionAnchor } from "@/lib/content";

/** 根拠の条文へのリンク（条が分かれば文書内のその条へ移動する） */
export function BasisLink({ basis, onClick }: { basis: BasisLinkData; onClick?: () => void }) {
  const href = basis.sectionId
    ? `/documents/${basis.documentId}#${sectionAnchor(basis.sectionId)}`
    : `/documents/${basis.documentId}`;
  const article = articleTitle(basis.articleNo, basis.heading);
  return (
    <Link className="underline" href={href} onClick={onClick}>
      {basis.documentTitle}
      {article && ` ${article}`}
    </Link>
  );
}
