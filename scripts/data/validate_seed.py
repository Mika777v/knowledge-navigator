"""data/seed/ のダミーデータの整合性を検査する。

使い方: python3 scripts/data/validate_seed.py [data/seed]
エラーがあれば一覧を表示して終了コード 1 で終わる。
"""

import re
import sys
from pathlib import Path

import yaml

ROLES = {"EMPLOYEE", "MANAGER", "CONTENT_EDITOR", "SYSTEM_ADMIN"}
VISIBILITIES = {"INTERNAL", "MANAGERS", "RESTRICTED", "CONFIDENTIAL"}
DOCUMENT_TYPES = {"REGULATION", "PROCEDURE", "MANUAL", "RULE", "ORG_CHART"}
DOCUMENT_FIELDS = (
    "code", "title", "document_type", "category", "owner_department",
    "visibility", "version_label", "effective_from", "summary",
)
DISCLAIMER = "架空企業「架空商事株式会社」のサンプル"
CONTACT_ROLES = {"APPLY", "INQUIRY", "PAYMENT"}
LIFE_EVENT_RELATIONS = {"PRIMARY", "RELATED"}
# 公開範囲の広さ（数字が小さいほど広い）。制度・FAQ は根拠文書より広くしてはいけない
VISIBILITY_RANK = {"INTERNAL": 0, "MANAGERS": 1, "RESTRICTED": 2, "CONFIDENTIAL": 3}
ARTICLE_HEADING = re.compile(r"^### 第(\d+)条（(.+)）\s*$", re.MULTILINE)
EMPLOYMENT_TYPES = {"REGULAR", "CONTRACT", "PART_TIME"}
# 架空データであることを保証するため、連絡先は予約済みドメインのみ許可する
ALLOWED_EMAIL = re.compile(r"^[a-z0-9.\-]+@([a-z0-9\-]+\.)*example\.com$")
ALLOWED_URL = re.compile(r"^https://([a-z0-9\-]+\.)*example\.com/")


def load(seed_dir: Path, name: str) -> list[dict]:
    with (seed_dir / name).open(encoding="utf-8") as f:
        return yaml.safe_load(f) or []


def check_unique(items: list[dict], key: str, label: str, errors: list[str]) -> None:
    seen: set = set()
    for item in items:
        value = item.get(key)
        if value in seen:
            errors.append(f"{label}: {key} が重複しています: {value}")
        seen.add(value)


def parse_document(path: Path) -> tuple[dict, str]:
    """front matter（YAML）と本文に分ける"""
    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        raise ValueError("front matter がありません")
    _, front, body = text.split("---\n", 2)
    return yaml.safe_load(front) or {}, body


def validate_documents(
    seed_dir: Path, dept_codes: set[str], category_slugs: set[str], errors: list[str]
) -> dict[str, tuple[dict, str, set[int]]]:
    docs: dict[str, tuple[dict, str, set[int]]] = {}
    for path in sorted((seed_dir / "documents").glob("*.md")):
        label = f"documents/{path.name}"
        try:
            meta, body = parse_document(path)
        except ValueError as e:
            errors.append(f"{label}: {e}")
            continue
        for field in DOCUMENT_FIELDS:
            if not meta.get(field):
                errors.append(f"{label}: front matter に {field} がありません")
        code = meta.get("code", "")
        if not path.name.startswith(f"{code}_"):
            errors.append(f"{label}: ファイル名は「{code}_タイトル.md」にしてください")
        if code in docs:
            errors.append(f"{label}: code {code} が重複しています")
        if meta.get("category") not in category_slugs:
            errors.append(f"{label}: カテゴリ {meta.get('category')} が存在しません")
        if meta.get("owner_department") not in dept_codes:
            errors.append(f"{label}: 所管部署 {meta.get('owner_department')} が存在しません")
        if meta.get("visibility") not in VISIBILITIES:
            errors.append(f"{label}: visibility が不正です: {meta.get('visibility')}")
        if meta.get("document_type") not in DOCUMENT_TYPES:
            errors.append(f"{label}: document_type が不正です: {meta.get('document_type')}")
        if DISCLAIMER not in body.split("##", 1)[0]:
            errors.append(f"{label}: 冒頭に架空データである旨の注記がありません")

        numbers = [int(n) for n, _ in ARTICLE_HEADING.findall(body)]
        if not numbers:
            errors.append(f"{label}: 「### 第N条（見出し）」形式の条文がありません")
        elif numbers != list(range(1, len(numbers) + 1)):
            errors.append(f"{label}: 条番号が第1条からの連番になっていません: {numbers}")
        docs[code] = (meta, body, set(numbers))

    # 規程どうしの条文参照（例: 就業規則第32条）が実在する条を指しているか
    by_title = {meta.get("title"): numbers for meta, _, numbers in docs.values()}
    for code, (_, body, _) in docs.items():
        for title, numbers in by_title.items():
            for n in re.findall(re.escape(title) + r"第(\d+)条", body):
                if int(n) not in numbers:
                    errors.append(f"documents[{code}]: 参照先「{title}第{n}条」が存在しません")
    return docs


def check_document_ref(
    label: str, ref: dict, docs: dict[str, tuple[dict, str, set[int]]], errors: list[str]
) -> dict | None:
    """{document, article} の参照先（文書と条）が存在するか。存在すれば文書の front matter を返す"""
    code = ref.get("document")
    if code not in docs:
        errors.append(f"{label}: 文書 {code} が存在しません")
        return None
    article = ref.get("article")
    if article is not None:
        m = re.fullmatch(r"第(\d+)条", str(article))
        if not m or int(m.group(1)) not in docs[code][2]:
            errors.append(f"{label}: {docs[code][0].get('title')}に {article} がありません")
    return docs[code][0]


def check_visibility_not_wider(label: str, visibility: str, doc_meta: dict | None, errors: list[str]) -> None:
    if doc_meta is None or visibility not in VISIBILITY_RANK:
        return
    if VISIBILITY_RANK[visibility] < VISIBILITY_RANK.get(doc_meta.get("visibility"), 0):
        errors.append(
            f"{label}: 公開範囲 {visibility} が根拠文書 {doc_meta.get('code')} の {doc_meta.get('visibility')} より広くなっています"
        )


def validate_programs_and_faqs(
    seed_dir: Path,
    dept_codes: set[str],
    category_slugs: set[str],
    contact_codes: set[str],
    docs: dict[str, tuple[dict, str, set[int]]],
    errors: list[str],
) -> None:
    programs = load(seed_dir, "programs.yaml")
    life_events = load(seed_dir, "life_events.yaml")
    faqs = load(seed_dir, "faqs.yaml")

    check_unique(programs, "code", "programs", errors)
    program_codes = {p["code"] for p in programs}
    for p in programs:
        label = f"programs[{p.get('code')}]"
        for field in ("code", "name", "summary", "category", "owner_department", "visibility"):
            if not p.get(field):
                errors.append(f"{label}: {field} がありません")
        if p.get("category") not in category_slugs:
            errors.append(f"{label}: カテゴリ {p.get('category')} が存在しません")
        if p.get("owner_department") not in dept_codes:
            errors.append(f"{label}: 所管部署 {p.get('owner_department')} が存在しません")
        if p.get("visibility") not in VISIBILITIES:
            errors.append(f"{label}: visibility が不正です: {p.get('visibility')}")
        if not p.get("contacts"):
            errors.append(f"{label}: 窓口（contacts）が1件もありません（公開する制度には必須）")
        for c in p.get("contacts") or []:
            if c.get("contact") not in contact_codes:
                errors.append(f"{label}: 窓口 {c.get('contact')} が存在しません")
            if c.get("role") not in CONTACT_ROLES:
                errors.append(f"{label}: 窓口の role が不正です: {c.get('role')}")
        if not p.get("basis"):
            errors.append(f"{label}: 根拠（basis）が1件もありません")
        for ref in p.get("basis") or []:
            meta = check_document_ref(label, ref, docs, errors)
            check_visibility_not_wider(label, p.get("visibility"), meta, errors)

    check_unique(life_events, "code", "life_events", errors)
    for e in life_events:
        label = f"life_events[{e.get('code')}]"
        for field in ("code", "name"):
            if not e.get(field):
                errors.append(f"{label}: {field} がありません")
        if not e.get("programs"):
            errors.append(f"{label}: 制度（programs）が1件もありません")
        for link in e.get("programs") or []:
            if link.get("program") not in program_codes:
                errors.append(f"{label}: 制度 {link.get('program')} が存在しません")
            if link.get("relation") not in LIFE_EVENT_RELATIONS:
                errors.append(f"{label}: relation が不正です: {link.get('relation')}")

    check_unique(faqs, "code", "faqs", errors)
    for f in faqs:
        label = f"faqs[{f.get('code')}]"
        for field in ("code", "question", "answer", "category", "owner_department"):
            if not f.get(field):
                errors.append(f"{label}: {field} がありません")
        if f.get("category") not in category_slugs:
            errors.append(f"{label}: カテゴリ {f.get('category')} が存在しません")
        if f.get("owner_department") not in dept_codes:
            errors.append(f"{label}: 所管部署 {f.get('owner_department')} が存在しません")
        visibility = f.get("visibility", "INTERNAL")
        if visibility not in VISIBILITIES:
            errors.append(f"{label}: visibility が不正です: {visibility}")
        if f.get("contact") and f["contact"] not in contact_codes:
            errors.append(f"{label}: 窓口 {f['contact']} が存在しません")
        links = f.get("links") or []
        if not f.get("contact") and not any("program" in link for link in links):
            errors.append(f"{label}: 窓口（contact）か関連制度（links.program）のどちらかが必要です")
        for link in links:
            if "program" in link:
                if link["program"] not in program_codes:
                    errors.append(f"{label}: 制度 {link['program']} が存在しません")
            elif "document" in link:
                meta = check_document_ref(label, link, docs, errors)
                check_visibility_not_wider(label, visibility, meta, errors)
            else:
                errors.append(f"{label}: links には program か document を指定してください: {link}")


def validate(seed_dir: Path) -> list[str]:
    errors: list[str] = []
    departments = load(seed_dir, "departments.yaml")
    contacts = load(seed_dir, "contacts.yaml")
    users = load(seed_dir, "users.yaml")
    categories = load(seed_dir, "categories.yaml")

    # ---------- departments ----------
    check_unique(departments, "code", "departments", errors)
    dept_codes = {d["code"] for d in departments}
    for d in departments:
        for field in ("code", "name"):
            if not d.get(field):
                errors.append(f"departments: {field} がありません: {d}")
        parent = d.get("parent")
        if parent and parent not in dept_codes:
            errors.append(f"departments[{d['code']}]: 親部署 {parent} が存在しません")
        if parent == d["code"]:
            errors.append(f"departments[{d['code']}]: 自分自身を親にしています")

    # ---------- categories ----------
    check_unique(categories, "slug", "categories", errors)
    category_slugs = {c["slug"] for c in categories}
    for c in categories:
        if not c.get("name"):
            errors.append(f"categories[{c.get('slug')}]: name がありません")
        if c.get("parent") and c["parent"] not in category_slugs:
            errors.append(f"categories[{c['slug']}]: 親カテゴリ {c['parent']} が存在しません")

    # ---------- contacts ----------
    check_unique(contacts, "code", "contacts", errors)
    primary_count: dict[str, int] = {}
    for c in contacts:
        label = f"contacts[{c.get('code')}]"
        for field in ("code", "department", "name", "office_hours"):
            if not c.get(field):
                errors.append(f"{label}: {field} がありません")
        if c.get("department") not in dept_codes:
            errors.append(f"{label}: 部署 {c.get('department')} が存在しません")
        if not (c.get("email") or c.get("phone_ext") or c.get("chat_url")):
            errors.append(f"{label}: 連絡手段（email / phone_ext / chat_url）が1つもありません")
        if c.get("email") and not ALLOWED_EMAIL.match(c["email"]):
            errors.append(f"{label}: email は example.com ドメインにしてください: {c['email']}")
        if c.get("chat_url") and not ALLOWED_URL.match(c["chat_url"]):
            errors.append(f"{label}: chat_url は example.com ドメインにしてください: {c['chat_url']}")
        if c.get("phone_ext") and not re.fullmatch(r"\d{4}", str(c["phone_ext"])):
            errors.append(f"{label}: phone_ext は4桁の内線番号（文字列）にしてください")
        if c.get("is_primary"):
            primary_count[c["department"]] = primary_count.get(c["department"], 0) + 1
    for dept, count in primary_count.items():
        if count > 1:
            errors.append(f"contacts: 部署 {dept} の代表窓口（is_primary）が {count} 件あります（最大1件）")
    # 窓口を持つ部署には代表窓口が必要（窓口導出のフォールバック先）
    for dept in {c["department"] for c in contacts if c.get("department")}:
        if primary_count.get(dept, 0) == 0:
            errors.append(f"contacts: 部署 {dept} に代表窓口（is_primary: true）がありません")

    # ---------- users ----------
    check_unique(users, "email", "users", errors)
    for u in users:
        label = f"users[{u.get('email')}]"
        if not ALLOWED_EMAIL.match(str(u.get("email", ""))):
            errors.append(f"{label}: email は example.com ドメインにしてください")
        if u.get("department") not in dept_codes:
            errors.append(f"{label}: 部署 {u.get('department')} が存在しません")
        if u.get("role") not in ROLES:
            errors.append(f"{label}: role が不正です: {u.get('role')}")
        if u.get("employment_type") not in EMPLOYMENT_TYPES:
            errors.append(f"{label}: employment_type が不正です: {u.get('employment_type')}")
        if "password" in u or "password_hash" in u:
            errors.append(f"{label}: パスワードを seed に書かないでください")
    roles_present = {u.get("role") for u in users}
    for role in ROLES - roles_present:
        errors.append(f"users: ロール {role} のデモユーザーがいません")

    # ---------- documents ----------
    docs = validate_documents(seed_dir, dept_codes, category_slugs, errors)

    # ---------- programs / life_events / faqs ----------
    contact_codes = {c["code"] for c in contacts if c.get("code")}
    validate_programs_and_faqs(seed_dir, dept_codes, category_slugs, contact_codes, docs, errors)

    return errors


def main() -> int:
    seed_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "data/seed")
    errors = validate(seed_dir)
    if errors:
        print(f"NG: {len(errors)} 件のエラー")
        for e in errors:
            print(f"  - {e}")
        return 1
    print(f"OK: {seed_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
