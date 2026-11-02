# 社内規程ナビで学ぶ TypeScript

このリポジトリの `frontend/` に実際にあるコードを教材にして、TypeScript の基本から React の画面まで、12 章で順に読めるようにした教科書です。各章の終わりに練習問題（答えつき）があります。

> 対象: JavaScript を少し読める人 ／ 題材: `frontend/src/` ／ 目安: 1 章 15〜30 分

練習でコードを書き換えたら、最後に元に戻す（`git checkout -- .`）。

## 目次

- [0. はじめに：TypeScript とは](#ch0)
- [1. 型の書き方の基本](#ch1)
- [2. 型推論と配列](#ch2)
- [3. オブジェクトの型・省略できる項目](#ch3)
- [4. ユニオン型・リテラル型・null](#ch4)
- [5. 型に名前を付ける：type と型の取り出し](#ch5)
- [6. 自動で作る型：schema.d.ts](#ch6)
- [7. クラスとエラー](#ch7)
- [8. 非同期処理：Promise・async・await](#ch8)
- [9. ジェネリクス：型を引数にする](#ch9)
- [10. 型の絞り込みと as の注意](#ch10)
- [11. React の部品（コンポーネント）の型](#ch11)
- [12. テストを書く（Vitest）](#ch12)
- [付録：用語と次に読むコード](#appendix)

---

<a id="ch0"></a>

## 0. はじめに：TypeScript とは

*この章でわかること: TypeScript が何をしてくれるか、手元で試す方法*

**TypeScript は「型」を書き足せる JavaScript** です。型とは「この値は文字列」「これは数値」といった**値の種類の約束**のことです。約束を書いておくと、プログラムを動かす前に、エディタや `tsc`（TypeScript の検査の道具）が「数値を入れるはずの所に文字列を入れていますよ」と教えてくれます。

ブラウザが実行するのは JavaScript だけなので、TypeScript は最後に型を取り除いて JavaScript に変換されます。**型は「実行前の検査」のためのもので、実行中の動きは変えません**。

### このリポジトリで試す

1. GitHub の README の「Codespaces で開く」ボタンを押す（ブラウザの中に VS Code が開く）
2. 下のターミナルで `cd frontend && pnpm install`
3. `pnpm typecheck` … 全ファイルの型を検査する。何も出なければ合格
4. `pnpm test` … テストを流す（12 章）

> **要点**
> - 型 = 値の種類の約束。破ると実行前に教えてもらえる
> - 型は実行前に消える。動きは JavaScript と同じ
> - 練習でコードを壊したら、最後に元に戻す（`git checkout -- .` で全部戻せる）

---

<a id="ch1"></a>

## 1. 型の書き方の基本

*この章でわかること: `string`・`number`・`boolean`、関数の引数と戻り値の型*

いちばん小さい教材は、日付や割合を画面用の文字に直す関数です。

`frontend/src/lib/format.ts（抜粋）`

```ts
/** 日時を日本時間で表示する（例: 2026/10/21 14:30） */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** 割合を百分率で表示する（分母が 0 のときは「—」） */
export function formatRatio(numerator: number, denominator: number): string {
  return denominator === 0 ? "—" : `${Math.round((numerator / denominator) * 100)}%`;
}
```

- `iso: string` … 引数 `iso` は**文字列**だけを受け取る
- `(…): string` … 関数の**戻り値**は文字列（かっこの後ろのコロン）
- 基本の型は `string`（文字列）・`number`（数値。整数も小数も同じ）・`boolean`（`true` / `false`）の 3 つから覚える

たとえば `formatRatio("1", 3)` と書くと、実行する前に「`string` は `number` に入れられません」という誤りになります。JavaScript だと `"1" / 3` は黙って計算されてしまうので、ここで止めてくれるのが TypeScript の価値です。

> **要点**
> - 書き方は「名前`: 型`」。戻り値は「`(引数): 型`」
> - `export` は「他のファイルから使ってよい」という印（型とは別の話）

#### 練習問題 1

「件数」を受け取って `"12件"` のような文字列を返す関数 `formatCount` を、型つきで書いてください。

<details><summary>答え</summary>

```ts
export function formatCount(count: number): string {
  return `${count}件`;
}
```

</details>

---

<a id="ch2"></a>

## 2. 型推論と配列

*この章でわかること: 型を書かなくてよい場面、配列の型、`readonly`*

TypeScript は、書かなくても分かる型は自分で決めます。これを**型推論**といいます。

```ts
const XSRF_COOKIE = "XSRF-TOKEN";                        // "XSRF-TOKEN" という文字列だと推論される
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);  // Set<string> と推論される
const usable = terms.filter((t) => t.trim().length > 0);  // t も usable も型を書いていない
```

上の 2 行は `frontend/src/lib/api/http.ts`、3 行目は `frontend/src/components/highlight.tsx` にあります。**関数の引数と戻り値には型を書き、中の変数は推論に任せる**のが、このリポジトリの書き方です。

### 配列の型

```ts
tags: string[]              // 文字列の配列（tag-links.tsx）
terms: readonly string[]     // 読むだけの文字列の配列（highlight.tsx）
```

`readonly` を付けると、その関数の中では `push` などで中身を変えられなくなります。「受け取った配列を勝手に書き換えない」という約束を、型で表しています。

> **要点**
> - 変数の型は推論に任せてよい。マウスを変数に乗せると、推論された型が見える
> - `T[]` は「T の配列」。`readonly T[]` は中身を変えない約束

#### 練習問題 2

`const names = ["人事部", "総務部"];` の `names` は何型と推論されますか。また `names.push(3)` はどうなりますか。

<details><summary>答え</summary>

`string[]`（文字列の配列）です。`names.push(3)` は数値を文字列の配列に入れようとしているので、型の誤りになります。

</details>

---

<a id="ch3"></a>

## 3. オブジェクトの型・省略できる項目

*この章でわかること: `{ 名前: 型 }` の書き方、`?`、既定値、分割代入*

規程のタグを並べる部品の引数は、**オブジェクト**（名前と値の組の集まり）です。

`frontend/src/components/tag-links.tsx（抜粋）`

```ts
export function TagLinks({ tags, label = "タグ" }: { tags: string[]; label?: string }) {
  if (tags.length === 0) return null;
  // …
}
```

- `{ tags: string[]; label?: string }` … 「`tags` は必ずある文字列の配列、`label` は**無くてもよい**文字列」という形の約束
- `label?` の `?` … 省略できる項目。省略すると値は `undefined`（未定義）になる
- `{ tags, label = "タグ" }` … 分割代入。オブジェクトから項目を取り出して変数にする。`= "タグ"` は省略されたときの既定値

使う側は `<TagLinks tags={["育児", "休暇"]} />` のように書きます。`tags` を書き忘れると、型の誤りになります。

> **要点**
> - オブジェクトの型は `{ 名前: 型; 名前: 型 }`
> - `?` で省略可。既定値と組み合わせると、中では必ず値がある状態で使える

#### 練習問題 3

窓口を表すオブジェクトの型を書いてください。部署名（必須・文字列）、内線番号（必須・文字列）、メールアドレス（無くてもよい・文字列）。

<details><summary>答え</summary>

```ts
{ department: string; extension: string; email?: string }
```

内線番号は計算に使わず、先頭の 0 も残したいので `number` ではなく `string` にするのが普通です。

</details>

---

<a id="ch4"></a>

## 4. ユニオン型・リテラル型・null

*この章でわかること: 「A か B のどちらか」の型、決まった値だけを許す型*

`|`（縦棒）は「または」です。CSRF トークン（なりすまし防止の合言葉）を Cookie から読む関数は、見つからなければ `null`（値が無いことを表す特別な値）を返します。

`frontend/src/lib/api/http.ts（抜粋）`

```ts
export function readXsrfToken(cookie: string = …): string | null {
  for (const part of cookie.split(";")) {
    const [name, ...rest] = part.trim().split("=");
    if (name === XSRF_COOKIE) return decodeURIComponent(rest.join("="));
  }
  return null;
}
```

戻り値が `string | null` なので、使う側は**null かもしれない**ことを必ず考えさせられます（10 章）。

### リテラル型：決まった値だけを許す

規程の公開範囲は、API の型（`schema.d.ts`）でこう決まっています。

```ts
visibility: "INTERNAL" | "MANAGERS" | "RESTRICTED" | "CONFIDENTIAL";
```

これは「どんな文字列でもよい」ではなく、**この 4 つの文字列のどれか**という型です。`"PUBLIC"` と書いたり、`"MANAGER"` と綴りを間違えたりすると、実行前に見つかります。社内公開・管理職限定・部署限定・機密の 4 つに対応しています。

> **要点**
> - `A | B` は「A か B」。`string | null` は「文字列か、値が無い」
> - `"X" | "Y"` のように値そのものを並べると、選べる値を絞れる

#### 練習問題 4

規程の状態「下書き・公開中・廃止」を、英大文字の 3 つの値だけを許す型 `Status` として書いてください。

<details><summary>答え</summary>

```ts
type Status = "DRAFT" | "PUBLISHED" | "ARCHIVED";
```

`type` で型に名前を付ける書き方は次の章で扱います。

</details>

---

<a id="ch5"></a>

## 5. 型に名前を付ける：type と型の取り出し

*この章でわかること: `type`、`import type`、`型["項目名"]`*

同じ型を何度も書かずに済むよう、`type 名前 = 型` で名前を付けます。このリポジトリでは、画面で使う型の名前を 1 つのファイルにまとめています。

`frontend/src/lib/api/types.ts（抜粋）`

```ts
import type { components } from "./schema";

/** backend の OpenAPI から生成したスキーマ（schema.d.ts）の別名 */
type Schemas = components["schemas"];

export type ProgramDetail = Schemas["ProgramDetail"];
export type AdminFaq = Schemas["FaqResponse"];
export type Visibility = AdminFaq["visibility"];
```

- `import type` … 型だけを読み込む。実行時には何も読み込まない（型は消えるため）
- `Schemas["ProgramDetail"]` … 大きな型の中から、名前を指定して一部の型を**取り出す**書き方
- `AdminFaq["visibility"]` … FAQ の型の `visibility` 項目の型、つまり前の章の 4 つの値のユニオン型が取り出される

同じものを手で書き直さず「取り出す」ことで、**backend の型が変わると画面の型も自動で追いかける**ようになっています。

> **要点**
> - `type 名前 = 型` で名前を付ける（似た書き方に `interface` もある。このリポジトリは主に `type`）
> - `型["項目名"]` で中の型を取り出せる

#### 練習問題 5

`types.ts` には `export type KeywordResponse = Schemas["KeywordResponse"];` があります。その `targetType` 項目の型に `KeywordTargetType` という名前を付ける 1 行を書いてください。

<details><summary>答え</summary>

```ts
export type KeywordTargetType = KeywordResponse["targetType"];
```

実際に `types.ts` にこの 1 行があります。答え合わせに開いてみてください。

</details>

---

<a id="ch6"></a>

## 6. 自動で作る型：schema.d.ts

*この章でわかること: 画面と backend の型をずらさないしくみ*

backend（Java）の API の説明（OpenAPI）から、`openapi-typescript` という道具が TypeScript の型を作ります。それが `frontend/src/lib/api/schema.d.ts` です。`.d.ts` は「型だけが書かれたファイル」という意味です。

```bash
# backend を起動した状態で（package.json の api:types）
cd frontend && pnpm api:types
```

CI は、backend から作り直した型と、コミットされた `schema.d.ts` が**一字一句同じか**を比べます。違えば失敗します。これで「backend は項目名を変えたのに、画面は古い名前のまま」というずれを防いでいます。

ときどき CI が落ちる原因として話した件は、ここで起きています。backend が出す項目の**並び順**がビルドのたびに変わる箇所が 1 つあり、型としては同じでも「一字一句同じ」ではなくなるためです。

> **要点**
> - `schema.d.ts` は手で直さない。backend から作り直す
> - 型を共有することで、API の変更が画面側の型の誤りとしてすぐ見つかる

#### 練習問題 6

backend が FAQ の項目 `question` を `title` に名前を変え、`schema.d.ts` を作り直しました。画面のコードで `faq.question` と書いている所はどうなりますか。

<details><summary>答え</summary>

`pnpm typecheck` で「`question` という項目はありません」という誤りになり、直す場所がすべて一覧で分かります。型が無い JavaScript だと、画面に何も表示されないだけで気付きにくい、という違いがあります。

</details>

---

<a id="ch7"></a>

## 7. クラスとエラー

*この章でわかること: `class`・`extends`・引数のプロパティ・`readonly`*

API の呼び出しが失敗したときに投げるエラーを、専用のクラスとして作っています。

`frontend/src/lib/api/http.ts（抜粋）`

```ts
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
  ) {
    super(detail);
    this.name = "ApiError";
  }
}
```

- `extends Error` … JavaScript 標準の `Error` を引き継ぎ、HTTP の状態コード（404 など）を足したもの
- `constructor(readonly status: number, …)` … 引数の前に `readonly` を付けると、その引数がそのまま**読むだけのプロパティ**になる（TypeScript の省略記法）
- `super(detail)` … 元の `Error` にメッセージを渡す

使う側は `throw new ApiError(401, "ログインが必要です。")` と書き、受け取った側は `error.status === 404` のように状態コードで分けられます。

> **要点**
> - エラーに種類と情報を持たせたいときは、`Error` を引き継いだクラスを作る
> - 引数に `readonly` などを付けると、プロパティの宣言と代入を省略できる

#### 練習問題 7

`const e = new ApiError(404, "見つかりません");` の後で `e.status = 500;` と書くとどうなりますか。

<details><summary>答え</summary>

`status` は `readonly` なので、書き換えは型の誤りになります。エラーの情報が途中で変わらないことを保証しています。

</details>

---

<a id="ch8"></a>

## 8. 非同期処理：Promise・async・await

*この章でわかること: 時間のかかる処理の型 `Promise<T>`*

通信のように**結果がすぐには返らない処理**は、「あとで値が届く約束」＝ `Promise` を返します。`async` を付けた関数の中では、`await` で値が届くまで待てます。

`frontend/src/lib/api/http.ts（抜粋）`

```ts
/** 更新系のリクエストの前に CSRF トークンを用意する（Cookie が無ければ発行してもらう） */
export async function ensureXsrfToken(): Promise<string> {
  const existing = readXsrfToken();
  if (existing) return existing;
  await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
  const issued = readXsrfToken();
  if (!issued) throw new ApiError(500, "CSRF トークンを取得できませんでした。");
  return issued;
}
```

- `Promise<string>` … 「あとで文字列が届く」という型。`<…>` の中が届く値の型
- `async` 関数の `return issued`（文字列）は、自動で `Promise<string>` に包まれる
- 使う側は `const token = await ensureXsrfToken();` と書くと、`token` は `string` になる

> **要点**
> - 時間のかかる処理の戻り値は `Promise<届く値の型>`
> - `await` は `async` 関数の中でだけ使える

#### 練習問題 8

`const t = ensureXsrfToken();`（`await` なし）と書いたとき、`t` の型は何ですか。`t.length` は使えますか。

<details><summary>答え</summary>

`t` は `Promise<string>` で、文字列そのものではありません。`t.length` は型の誤りになります。`await` の付け忘れを見つけてくれる、よくある助けです。

</details>

---

<a id="ch9"></a>

## 9. ジェネリクス：型を引数にする

*この章でわかること: `<T>` の意味、1 つの関数で色々な型を返す方法*

API を呼ぶ関数は 1 つですが、返ってくるデータは「制度」「FAQ」「検索結果」と色々です。そこで、**返す型を呼ぶ側が指定できる**ようにしています。

`frontend/src/lib/api/http.ts（抜粋・中身は省略）`

```ts
export async function apiFetch<T>(
  path: string,
  init: RequestInit & { json?: unknown } = {},
  onUnauthorized: () => void = redirectToLogin,
): Promise<T> {
  // …通信して、失敗なら ApiError を投げる…
  return (await response.json()) as T;
}
```

- `<T>` … 型の引数。`T` は「呼ぶ側が決める何かの型」の仮の名前
- 呼ぶ側: `apiFetch<ProgramDetail>("/api/v1/programs/…")` と書くと、戻り値は `Promise<ProgramDetail>` になる
- `RequestInit & { json?: unknown }` … `&` は「両方を合わせた型」。標準の fetch の設定に、自前の `json` 項目を足している
- `() => void` … 「引数なし・何も返さない関数」という型
- `unknown` … 「何でも入るが、使う前に中身を確かめる必要がある」型

> **要点**
> - `<T>` で、型を外から受け取る関数を作れる（配列の `T[]`、`Promise<T>` も同じしくみ）
> - 関数そのものにも型がある: `(引数: 型) => 戻り値の型`

#### 練習問題 9

配列の最初の要素を返す関数 `first` を、どんな型の配列にも使えるようにジェネリクスで書いてください（空の配列なら `undefined`）。

<details><summary>答え</summary>

```ts
function first<T>(items: readonly T[]): T | undefined {
  return items[0];
}
first(["育児", "休暇"]);  // string | undefined
first([1, 2, 3]);        // number | undefined
```

呼ぶ側で `<string>` を書かなくても、渡した配列から `T` が推論されます。

</details>

---

<a id="ch10"></a>

## 10. 型の絞り込みと as の注意

*この章でわかること: `if` で型が狭まるしくみ、`as` を使うときの責任*

`string | null` のような型は、`if` で確かめると、その先では型が**狭まります**。8 章のコードをもう一度見てください。

```ts
const issued = readXsrfToken();          // string | null
if (!issued) throw new ApiError(500, …);  // null（や空文字）ならここで終わる
return issued;                           // ここでは string に狭まっている
```

ほかにも `typeof document === "undefined"`（ブラウザの外＝サーバの上で動いているかを確かめる。`http.ts`）や、`response.status === 204` のような比較でも分岐できます。

### as は「検査しない」という宣言

```ts
return (await response.json()) as T;
```

`as T` は「この値は T 型だと**私が保証する**」という宣言で、TypeScript は中身を確かめません。ここで使えるのは、届く JSON の形を `schema.d.ts` で backend と合わせているからです。**根拠なく `as` を使うと、型の守りを自分で外すことになる**ので、使う場所は最小限にします。

> **要点**
> - `if`・`typeof`・`===` で確かめると、その先で型が狭まる（絞り込み）
> - `as` は検査の省略。使うなら理由をコメントに書く

#### 練習問題 10

次の関数は型の誤りになります。どこがなぜ誤りで、どう直せばよいですか。

```ts
function tokenLength(token: string | null): number {
  return token.length;
}
```

<details><summary>答え</summary>

`token` が `null` かもしれないのに `.length` を読んでいるためです（`null` には `length` が無い）。確かめてから使います。

```ts
function tokenLength(token: string | null): number {
  if (token === null) return 0;
  return token.length;   // ここでは string
}
```

</details>

---

<a id="ch11"></a>

## 11. React の部品（コンポーネント）の型

*この章でわかること: `.tsx`、props の型、JSX の中の TypeScript*

画面の部品は `.tsx` ファイルに書きます。TypeScript に、HTML に似た書き方（JSX）を混ぜられるファイルです。検索語を黄色で強調する部品を読みます。

`frontend/src/components/highlight.tsx（抜粋）`

```ts
/** 本文中の検索語を <mark> で強調する。HTML として解釈せず、React のテキストとして描画する */
export function Highlight({ text, terms }: { text: string; terms: readonly string[] }) {
  const usable = terms.filter((t) => t.trim().length > 0);
  if (usable.length === 0) return <>{text}</>;
  const pattern = new RegExp(`(${usable.map(escapeRegExp).join("|")})`, "gi");
  const lowerTerms = new Set(usable.map((t) => t.toLowerCase()));
  return (
    <>
      {text.split(pattern).map((part, i) =>
        lowerTerms.has(part.toLowerCase()) ? <mark key={i}>{part}</mark> : part,
      )}
    </>
  );
}
```

- 部品は「props（部品に渡す値）を 1 つのオブジェクトで受け取り、画面の部品を返す関数」。props の型の書き方は 3 章と同じ
- `{…}` の中は TypeScript の式。`text.split(pattern)` の結果も型で守られている
- `<>…</>` は、余計な囲みを作らずに複数の要素を返すための書き方
- 使う側: `<Highlight text={section.body} terms={["育児"]} />`。`terms` を忘れたり数値を渡したりすると型の誤り

コメントにある「HTML として解釈しない」は安全のための工夫です。文字列を HTML として埋め込まず、React に**文字として**描かせることで、本文に紛れ込んだ悪意のあるタグが動かないようにしています。

> **要点**
> - React の部品 = props の型を持つ関数
> - JSX の `{ }` の中は普通の TypeScript。型の検査もそのまま効く

#### 練習問題 11

件数を「全 12 件」のように表示する部品 `CountLabel` を書いてください。props は `count`（数値・必須）。

<details><summary>答え</summary>

```ts
export function CountLabel({ count }: { count: number }) {
  return <span>全 {count} 件</span>;
}
```

</details>

---

<a id="ch12"></a>

## 12. テストを書く（Vitest）

*この章でわかること: 型では見つからない誤りをテストで見つける*

型は「種類」の間違いを見つけますが、「計算結果が正しいか」は分かりません。そこはテストで確かめます。1 章の関数のテストです。

`frontend/src/lib/format.test.ts`

```ts
import { describe, expect, it } from "vitest";
import { formatDateTime, formatRatio } from "./format";

describe("format", () => {
  it("日時を日本時間で表示する", () => {
    expect(formatDateTime("2026-10-21T15:30:00Z")).toBe("2026/10/22 00:30");
  });

  it("割合を百分率にする（分母 0 は —）", () => {
    expect(formatRatio(1, 3)).toBe("33%");
    expect(formatRatio(0, 0)).toBe("—");
  });
});
```

- `describe` … テストのまとまり、`it` … 1 つの確かめごと（名前は日本語で書いている）
- `expect(実際の値).toBe(期待する値)` … 同じでなければ失敗
- UTC の 15:30 が日本時間では翌日 0:30 になる、という間違えやすい所をテストで押さえている
- 流し方: `cd frontend && pnpm test`（ファイルを指定するなら `pnpm test format`）

> **要点**
> - 型 = 種類の検査、テスト = 結果の検査。両方そろって安心できる
> - このリポジトリでは、テストは対象のファイルの隣に `○○.test.ts` で置く

#### 練習問題 12

練習問題 1 の `formatCount` のテストを 1 つ書いてください。

<details><summary>答え</summary>

```ts
it("件数に「件」を付ける", () => {
  expect(formatCount(12)).toBe("12件");
  expect(formatCount(0)).toBe("0件");
});
```

0 のような「端の値」を 1 つ入れておくのがコツです。

</details>

---

<a id="appendix"></a>

## 付録：用語と次に読むコード

*困ったときに戻ってくる場所*

| 書き方 | 読み方・意味 | 出てくる章 |
|---|---|---|
| `x: string` | x は文字列 | 1 |
| `(…): number` | 関数の戻り値は数値 | 1 |
| `string[]` / `readonly string[]` | 文字列の配列 / 中身を変えない配列 | 2 |
| `{ a: string; b?: number }` | オブジェクトの形。`?` は省略可 | 3 |
| `A \| B` | A か B（ユニオン型） | 4 |
| `"X" \| "Y"` | 決まった値だけ（リテラル型） | 4 |
| `type 名前 = …` | 型に名前を付ける | 5 |
| `型["項目"]` | 中の型を取り出す | 5 |
| `Promise<T>` | あとで T が届く | 8 |
| `<T>` | 型の引数（ジェネリクス） | 9 |
| `A & B` | A と B を合わせた型 | 9 |
| `unknown` | 何でも入るが、使う前に確かめる | 9 |
| `as T` | 検査を省いて T とみなす（使いすぎ注意） | 10 |

### 次に読むコード（難しくなる順）

- `frontend/src/components/basis-link.tsx`（24 行）… 根拠の条へのリンク。11 章の復習
- `frontend/src/lib/security-headers.ts` とテスト … 画面のセキュリティヘッダーを組み立てる
- `frontend/src/app/search/search-results.tsx`（250 行）… 検索結果の画面。型・部品・データ取得がすべて出てくる
- `frontend/src/proxy.ts` … 画面のサーバが backend へ中継する所（BFF。ADR-0005）

---

題材のコードは Mika777v/knowledge-navigator の `frontend/`（2026-10-30 時点の main）から引用。抜粋のため一部を省略しています。データはすべて架空の「架空商事株式会社」のものです。
