const dateTime = new Intl.DateTimeFormat("ja-JP", {
  timeZone: "Asia/Tokyo",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

/** 日時を日本時間で表示する（例: 2026/10/21 14:30） */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** 割合を百分率で表示する（分母が 0 のときは「—」） */
export function formatRatio(numerator: number, denominator: number): string {
  return denominator === 0 ? "—" : `${Math.round((numerator / denominator) * 100)}%`;
}
