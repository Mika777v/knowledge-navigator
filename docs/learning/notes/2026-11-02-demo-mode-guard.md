# デモ環境で、他の人を締め出せる操作を止める（Java・Spring）

- 関連: `feat(backend): デモ環境では、パスワードの変更とユーザーの更新を断る`（ADR-0015 決定 5）
- 触ったファイル: `organization/application/DemoMode.java`（新規）、`AuthController.java`、`AdminOrganizationController.java`、`shared/web/ApiErrors.java`、`application.yaml`、`compose.demo.yaml`

## 何をしたか

デモ環境は全員共通のパスワードで使うため、誰かがパスワードを変えたり、管理者がユーザーを無効にしたりすると、他の人が入れなくなる。設定 `knavi.demo.enabled` が `true` のときだけ、この 2 つの操作を 403（禁止）で断るようにした。

```java
@Component
public class DemoMode {

    private final boolean enabled;

    DemoMode(@Value("${knavi.demo.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public void rejectIfEnabled(String action) {
        if (enabled) {
            throw ApiErrors.forbidden("デモ環境では" + action + "できません（データは毎日初期化されます）。");
        }
    }
}
```

使う側（`AuthController` のパスワード変更の最初の行）:

```java
demoMode.rejectIfEnabled("パスワードを変更");
```

## Java・Spring 独特の動き

### 1. `@Component` とコンストラクタ注入（DI）

`@Component` を付けたクラスは、Spring が起動時に**1 つだけ作って持っておく**（Bean と呼ぶ）。別のクラスのコンストラクタの引数に `DemoMode demoMode` と書くと、Spring がその 1 つを渡してくれる。これを **DI（依存性の注入）** という。

```java
AdminOrganizationController(OrganizationAdminService service, DemoMode demoMode) {
    this.service = service;
    this.demoMode = demoMode;
}
```

自分で `new DemoMode(...)` と書かないのがポイント。テストでは逆に `new DemoMode(true)` と自分で作れるので、Spring を起動せずに確かめられる（`DemoModeTest`）。

### 2. `@Value("${名前:既定値}")` で設定を受け取る

`${knavi.demo.enabled:false}` は「設定 `knavi.demo.enabled` の値。無ければ `false`」。設定は `application.yaml` に書いてあり、そこでも同じ書き方で環境変数を読んでいる。

```yaml
knavi:
  demo:
    enabled: ${KNAVI_DEMO_ENABLED:false}   # 環境変数 KNAVI_DEMO_ENABLED。無ければ false
```

つまり「環境変数 → yaml → Java の引数」の順に値が流れる。デモ環境では `compose.demo.yaml` で `KNAVI_DEMO_ENABLED: "true"` を渡している。文字列の `"true"` は、Spring が引数の型（`boolean`）に合わせて変換する。

### 3. 例外を投げると、そのままエラーの応答になる

`ApiErrors.forbidden(...)` は `ErrorResponseException` という例外を作るだけで、`throw` すると処理はそこで止まる。Spring はこの例外を受け取り、HTTP の 403 と、次のような JSON（RFC 9457 の Problem Details という決まった形）に変えて返す。

```json
{ "status": 403, "detail": "デモ環境ではパスワードを変更できません（データは毎日初期化されます）。", ... }
```

画面（`account-view.tsx`）は `ApiError` の `detail` をそのまま表示するので、frontend を変えなくても理由が出る（TypeScript の教科書 7 章の `ApiError`）。

### 4. `public` と、何も書かない（パッケージ内だけ）

`DemoMode` のクラスと `rejectIfEnabled` は `public`（別のパッケージの Controller から使うため）、コンストラクタは何も付けない＝**同じパッケージの中だけ**から呼べる。Spring は特別な方法で呼べるので問題ない。「外から勝手に作らせない」という意図を表している。

### 5. テストで設定を変える

```java
@SpringBootTest(properties = "knavi.demo.enabled=true")
class DemoModeApiTests { ... }
```

`properties` で、そのテストのときだけ設定を上書きできる。ふだんの設定（`false`）のまま動いている他のテストには影響しない。

## 確かめてみる

- `cd backend && ./gradlew test --tests '*DemoMode*'`
- `make up` した後に、`.env` に `KNAVI_DEMO_ENABLED=true` を足して backend を起動し直し、画面の「パスワードを変更」を試すと、理由つきで断られる
