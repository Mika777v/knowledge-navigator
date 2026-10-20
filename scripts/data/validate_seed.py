"""data/seed/ のダミーデータの整合性を検査する。

使い方: python3 scripts/data/validate_seed.py [data/seed]
エラーがあれば一覧を表示して終了コード 1 で終わる。
"""

import re
import sys
from pathlib import Path

import yaml

ROLES = {"EMPLOYEE", "MANAGER", "CONTENT_EDITOR", "SYSTEM_ADMIN"}
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


def validate(seed_dir: Path) -> list[str]:
    errors: list[str] = []
    departments = load(seed_dir, "departments.yaml")
    contacts = load(seed_dir, "contacts.yaml")
    users = load(seed_dir, "users.yaml")

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
