import type { ContactView } from "@/lib/api/types";

const ROLE_LABELS: Record<string, string> = {
  APPLY: "申請先",
  INQUIRY: "問い合わせ先",
  PAYMENT: "支給窓口",
};

/** 問い合わせ先の窓口。連絡手段（内線・メール・チャット）と受付時間を表示する */
export function ContactCard({ contact, note }: { contact: ContactView; note?: string }) {
  const label = note ?? (contact.role ? ROLE_LABELS[contact.role] : undefined);
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-border p-3 text-sm">
      <div className="flex flex-wrap items-baseline gap-2">
        <span className="font-semibold">{contact.name}</span>
        {label && <span className="rounded bg-foreground/10 px-1.5 py-0.5 text-xs">{label}</span>}
      </div>
      {contact.description && <p className="text-muted-foreground">{contact.description}</p>}
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5">
        {contact.phoneExt && (
          <>
            <dt className="text-muted-foreground">内線</dt>
            <dd>{contact.phoneExt}</dd>
          </>
        )}
        {contact.email && (
          <>
            <dt className="text-muted-foreground">メール</dt>
            <dd>
              <a className="underline" href={`mailto:${contact.email}`}>
                {contact.email}
              </a>
            </dd>
          </>
        )}
        {contact.chatUrl && (
          <>
            <dt className="text-muted-foreground">チャット</dt>
            <dd>
              <a className="underline" href={contact.chatUrl} target="_blank" rel="noreferrer">
                チャットで問い合わせる
              </a>
            </dd>
          </>
        )}
        {contact.officeHours && (
          <>
            <dt className="text-muted-foreground">受付</dt>
            <dd>{contact.officeHours}</dd>
          </>
        )}
      </dl>
    </div>
  );
}
