export default function HomePage() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-8 px-4 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold">社内規程ナビ</h1>
        <p className="text-muted-foreground">
          知りたいことを入力すると、使える制度・根拠となる規程・問い合わせ先を案内します。
        </p>
      </header>

      {/* 検索フォームとナビゲーション結果は #36 で実装する */}
      <section
        aria-label="検索（準備中）"
        className="rounded-lg border border-dashed border-border p-6 text-muted-foreground"
      >
        検索機能は準備中です。
      </section>

      <footer className="mt-auto text-sm text-muted-foreground">
        表示されるデータはすべて架空企業「架空商事株式会社」のダミーデータです。
      </footer>
    </main>
  );
}
