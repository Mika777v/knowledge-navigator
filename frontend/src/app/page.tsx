import { LifeEventLinks } from "@/components/life-event-links";
import { SearchForm } from "@/components/search-form";

export default function HomePage() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-10 px-4 py-12">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold">社内規程ナビ</h1>
        <p className="text-muted-foreground">
          知りたいことを入力すると、使える制度・根拠となる規程・問い合わせ先を案内します。
        </p>
      </header>

      <section aria-label="検索" className="flex flex-col gap-2">
        <SearchForm autoFocus />
        <p className="text-sm text-muted-foreground">制度の名前が分からなくても、「PC壊れた」「引っ越した」のように書けば探せます。</p>
      </section>

      <section aria-labelledby="life-events-heading" className="flex flex-col gap-3">
        <h2 id="life-events-heading" className="text-lg font-semibold">
          きっかけから探す
        </h2>
        <LifeEventLinks />
      </section>

      <footer className="mt-auto text-sm text-muted-foreground">
        表示されるデータはすべて架空企業「架空商事株式会社」のダミーデータです。
      </footer>
    </main>
  );
}
