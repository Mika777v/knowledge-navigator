# CSRF の API の説明から、Spring が渡す引数を隠す（Java・Spring・TypeScript）

- 関連: `fix(backend): CSRF トークンの API の説明から、Spring が渡す引数を外す`
- 触ったファイル: `backend/.../organization/api/AuthController.java`、`frontend/src/lib/api/schema.d.ts`

## 何をしたか

CI の「`schema.d.ts` が API とずれていないか」の比較が、ときどき落ちていた。原因は、CSRF トークンを発行する API（`GET /api/v1/auth/csrf`）の**引数**が、API の入力として説明（OpenAPI）に載っていたこと。その引数の型の項目の並び順が、ビルドのたびに変わることがあった。引数を説明から隠して直した。

```java
@GetMapping("/auth/csrf")
Map<String, String> csrf(@Parameter(hidden = true) CsrfToken token) {
    token.getToken();
    return Map.of("headerName", token.getHeaderName());
}
```

## Java・Spring 独特の動き

### 1. メソッドの引数は「フレームワークが入れてくれる」ことがある

Spring の Controller では、メソッドの引数に書いた型を見て、Spring が値を用意して渡す。`@RequestParam`（URL の `?q=...`）や `@RequestBody`（送られた JSON）のように「リクエストから取り出すもの」だけでなく、`CsrfToken`・`HttpServletRequest`・`@AuthenticationPrincipal` のログイン中のユーザーのように「**サーバの中で用意するもの**」も、引数に書くだけで受け取れる。

呼び出すのは Spring なので、このメソッドを自分で `csrf(...)` と呼ぶコードはどこにも無い。

### 2. アノテーション（`@…`）は「印」

`@GetMapping`・`@Parameter` などは、それ自体は何もしない**印**で、Spring や springdoc（API の説明を作る道具）が実行中に読み取って動きを決める。`@Parameter(hidden = true)` は springdoc への「この引数は説明に載せないで」という指示で、Spring が値を渡す動きは変わらない。

### 3. 「リフレクション」の順番は決まっていない

springdoc は `CsrfToken` を「入力の型」だと思い、Java のリフレクション（実行中にクラスの中身を調べるしくみ）で `getToken()`・`getHeaderName()` などのメソッドを集めて項目にしていた。**Java の仕様では、`Class.getMethods()` などが返すメソッドの順番は決まっていない。** 多くの場合は同じ順に見えるが、JVM や読み込みの状況で入れ替わることがある。これが「ときどき」落ちた理由。

自分で作る record（例: `record MeResponse(String email, ...)`）は、項目の順番が書いた順に決まるため、この問題は起きない。

## TypeScript 側の変化

`schema.d.ts` では、入力の項目が無いことを `never`（「あり得ない」型）で表す。

```ts
// 前: token という query の入力があることになっていた
parameters: { query: { token: components["schemas"]["CsrfToken"] }; ... };
// 後: query の入力は無い
parameters: { query?: never; ... };
```

`query?: never` は「`query` は書かないでください（書くと型の誤り）」という意味になる。

## 確かめてみる

- `AuthController.java` の `csrf` の引数に付けた `@Parameter(hidden = true)` を消して backend を起動し、`http://localhost:8080/swagger-ui.html` で `/auth/csrf` を開くと、`token` という入力欄が出てくる
