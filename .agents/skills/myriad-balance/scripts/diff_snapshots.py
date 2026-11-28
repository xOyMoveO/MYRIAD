#!/usr/bin/env python
"""Compare two MYRIAD balance snapshots and separate real numeric changes from unchanged checks.

Standard library only. Accepts either balance-snapshot.json files or directories containing them
(e.g. build/balance or build/ci-release).
"""
import argparse
import json
import sys
from pathlib import Path


RULE_META_KEYS = {"source", "reason", "condition", "sampledAt"}
SECTION_ORDER = ("attributePurchases", "skillRules", "modeProfiles", "battlefield")
SECTION_LABELS = {
    "attributePurchases": "属性购点、永久增益与专精",
    "skillRules": "公共技能规则（急速／额外物攻／盾疗／减伤）",
    "modeProfiles": "模式供给配置（3v3 / 5v5）",
    "battlefield": "战场规则（经济／补给／控制／兵线／野怪／建筑）",
}


def resolve_snapshot(path: Path) -> tuple[Path, Path | None]:
    if path.is_dir():
        snap = path / "balance-snapshot.json"
        if not snap.is_file():
            raise ValueError(f"目录中缺少 balance-snapshot.json：{path}")
        cov = path / "balance-coverage.json"
        return snap, cov if cov.is_file() else None
    if not path.is_file():
        raise ValueError(f"找不到快照文件：{path}")
    cov = path.parent / "balance-coverage.json"
    return path, cov if cov.is_file() else None


def is_rule_leaf(node) -> bool:
    return isinstance(node, dict) and "status" in node and ("value" in node or "unit" in node)


def compact(value, max_len: int = 88) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return f"{value:g}" if isinstance(value, float) else str(value)
    if isinstance(value, list) and len(value) > 4 and all(isinstance(x, (int, float)) for x in value):
        return f"[{compact(value[0])} .. {compact(value[-1])}] (len={len(value)})"
    text = json.dumps(value, ensure_ascii=False, separators=(", ", ": "))
    return text if len(text) <= max_len else text[: max_len - 1] + "…"


def leaf_summary(node: dict) -> str:
    status = node.get("status", "")
    val = node.get("value")
    unit = node.get("unit", "")
    clock = node.get("clock", "")
    if status in ("not_applicable", "missing_input"):
        return status
    val_str = compact(val)
    parts = [val_str]
    if unit and unit not in ("none", "formula", "rule", "rules", "table", "boolean", "string", "object", "enum"):
        parts.append(unit)
    if clock and clock != "not_timed":
        parts.append(f"({clock})")
    if status and status not in ("defined", "zero"):
        parts.append(f"[{status}]")
    return " ".join(parts)


def diff_nodes(before, after, path: str, include_meta: bool, out: list[dict]) -> None:
    if before == after:
        return
    if before is None and after is not None:
        out.append({"path": path, "kind": "added", "before": "—", "after": leaf_summary(after) if is_rule_leaf(after) else compact(after)})
        return
    if before is not None and after is None:
        out.append({"path": path, "kind": "removed", "before": leaf_summary(before) if is_rule_leaf(before) else compact(before), "after": "—"})
        return

    if is_rule_leaf(before) and is_rule_leaf(after):
        core_before = {k: v for k, v in before.items() if k not in RULE_META_KEYS}
        core_after = {k: v for k, v in after.items() if k not in RULE_META_KEYS}
        if core_before != core_after:
            if isinstance(before.get("value"), dict) and isinstance(after.get("value"), dict) and before.get("status") == after.get("status"):
                diff_nodes(before["value"], after["value"], path, include_meta, out)
            else:
                out.append({
                    "path": path,
                    "kind": "changed",
                    "before": leaf_summary(before),
                    "after": leaf_summary(after),
                })
        elif include_meta:
            meta_keys = sorted(k for k in RULE_META_KEYS if before.get(k) != after.get(k))
            out.append({
                "path": path,
                "kind": "meta",
                "before": compact({k: before.get(k) for k in meta_keys}),
                "after": compact({k: after.get(k) for k in meta_keys}),
            })
        return

    if isinstance(before, dict) and isinstance(after, dict):
        for key in sorted(set(before) | set(after)):
            if not include_meta and key in RULE_META_KEYS and key not in ("value", "status"):
                continue
            sub_path = f"{path}.{key}" if path else key
            if key not in before:
                diff_nodes(None, after[key], sub_path, include_meta, out)
            elif key not in after:
                diff_nodes(before[key], None, sub_path, include_meta, out)
            else:
                diff_nodes(before[key], after[key], sub_path, include_meta, out)
        return

    if isinstance(before, list) and isinstance(after, list):
        if all(isinstance(x, dict) and "id" in x for x in before) and all(isinstance(x, dict) and "id" in x for x in after):
            b_map = {x["id"]: x for x in before}
            a_map = {x["id"]: x for x in after}
            for item_id in sorted(set(b_map) | set(a_map)):
                sub_path = f"{path}[{item_id}]"
                diff_nodes(b_map.get(item_id), a_map.get(item_id), sub_path, include_meta, out)
            return
        if all(isinstance(x, dict) and "rank" in x for x in before) and all(isinstance(x, dict) and "rank" in x for x in after):
            b_map = {x["rank"]: x for x in before}
            a_map = {x["rank"]: x for x in after}
            for rank in sorted(set(b_map) | set(a_map)):
                sub_path = f"{path}[r{rank}]"
                diff_nodes(b_map.get(rank), a_map.get(rank), sub_path, include_meta, out)
            return
        out.append({"path": path, "kind": "changed", "before": compact(before), "after": compact(after)})
        return

    out.append({"path": path, "kind": "changed", "before": compact(before), "after": compact(after)})


def ordered_global_sections(b_rules: dict, a_rules: dict) -> list[str]:
    all_keys = (set(b_rules) | set(a_rules)) - {"roles"}
    ordered = [k for k in SECTION_ORDER if k in all_keys]
    ordered.extend(sorted(all_keys - set(ordered)))
    return ordered


def diff_snapshots(before_snap: dict, after_snap: dict, *, role_filter: set[str] | None, include_meta: bool,
                   before_cov: dict | None = None, after_cov: dict | None = None) -> dict:
    b_rules = before_snap.get("rules", {})
    a_rules = after_snap.get("rules", {})

    sections: dict[str, list[dict]] = {}
    if not role_filter:
        for sec in ordered_global_sections(b_rules, a_rules):
            changes: list[dict] = []
            diff_nodes(b_rules.get(sec), a_rules.get(sec), sec, include_meta, changes)
            if changes:
                sections[sec] = changes

    b_roles = {r["id"]: r for r in b_rules.get("roles", [])}
    a_roles = {r["id"]: r for r in a_rules.get("roles", [])}
    all_role_ids = sorted(set(b_roles) | set(a_roles))
    if role_filter:
        all_role_ids = [rid for rid in all_role_ids if rid in role_filter]

    role_changes: dict[str, list[dict]] = {}
    roles_value_changed: list[str] = []
    roles_schema_only: list[str] = []
    unchanged_roles: list[str] = []
    skills_value_changed: set[str] = set()
    skills_schema_only: set[str] = set()
    total_skills = 0

    for rid in all_role_ids:
        br = b_roles.get(rid)
        ar = a_roles.get(rid)
        if ar:
            total_skills += len(ar.get("skills", []))
        elif br:
            total_skills += len(br.get("skills", []))
        changes = []
        if br is None or ar is None:
            diff_nodes(br, ar, rid, include_meta, changes)
        else:
            for field in ("attributes", "basicAttack", "legalBranches"):
                diff_nodes(br.get(field), ar.get(field), field, include_meta, changes)
            bs = {s["id"]: s for s in br.get("skills", [])}
            as_ = {s["id"]: s for s in ar.get("skills", [])}
            for sid in sorted(set(bs) | set(as_)):
                s_changes: list[dict] = []
                b_skill, a_skill = bs.get(sid), as_.get(sid)
                if b_skill is None or a_skill is None:
                    diff_nodes(b_skill, a_skill, f"skills[{sid}]", include_meta, s_changes)
                else:
                    for sfield in ("availability", "category", "ranks", "effects"):
                        diff_nodes(b_skill.get(sfield), a_skill.get(sfield), f"skills[{sid}].{sfield}", include_meta, s_changes)
                if s_changes:
                    if any(row["kind"] == "changed" for row in s_changes):
                        skills_value_changed.add(sid)
                    else:
                        skills_schema_only.add(sid)
                    changes.extend(s_changes)
        if changes:
            role_changes[rid] = changes
            if any(row["kind"] == "changed" for row in changes):
                roles_value_changed.append(rid)
            else:
                roles_schema_only.append(rid)
        else:
            unchanged_roles.append(rid)

    reminder_diff = None
    if before_cov is not None or after_cov is not None:
        b_rem = (before_cov or {}).get("presentationReminders", [])
        a_rem = (after_cov or {}).get("presentationReminders", [])
        if b_rem != a_rem:
            reminder_diff = {"before": b_rem, "after": a_rem}

    return {
        "before": {
            "formatVersion": before_snap.get("formatVersion"),
            "sourceSha": before_snap.get("sourceSha"),
            "sourceDirty": before_snap.get("sourceDirty"),
            "rulesSha256": before_snap.get("rulesSha256"),
        },
        "after": {
            "formatVersion": after_snap.get("formatVersion"),
            "sourceSha": after_snap.get("sourceSha"),
            "sourceDirty": after_snap.get("sourceDirty"),
            "rulesSha256": after_snap.get("rulesSha256"),
        },
        "identicalRules": before_snap.get("rulesSha256") == after_snap.get("rulesSha256"),
        "summary": {
            "rolesChecked": len(all_role_ids),
            "rolesValueChanged": len(roles_value_changed),
            "rolesSchemaOnly": len(roles_schema_only),
            "rolesUnchanged": len(unchanged_roles),
            "unchangedRoleIds": unchanged_roles,
            "skillsChecked": total_skills,
            "skillsValueChanged": len(skills_value_changed),
            "skillsSchemaOnly": len(skills_schema_only),
            "skillsUnchanged": max(0, total_skills - len(skills_value_changed) - len(skills_schema_only)),
            "globalSectionChanges": {
                k: {
                    "changed": sum(1 for r in v if r["kind"] == "changed"),
                    "addedOrRemoved": sum(1 for r in v if r["kind"] in ("added", "removed")),
                }
                for k, v in sections.items()
            },
        },
        "sections": sections,
        "roles": role_changes,
        "presentationReminders": reminder_diff,
    }


def esc(text: str) -> str:
    return str(text).replace("\\", "\\\\").replace("|", "\\|").replace("\n", "<br>")


def render_rows(rows: list[dict], limit: int) -> list[str]:
    lines = ["| 类型 | 路径 | 改前 | 改后 |", "| --- | --- | --- | --- |"]
    for row in rows[:limit]:
        lines.append(f"| {row['kind']} | `{esc(row['path'])}` | {esc(row['before'])} | {esc(row['after'])} |")
    if len(rows) > limit:
        lines.append(f"\n_以上仅显示前 {limit} 条，共 {len(rows)} 条；传 `--limit 0` 查看全部。_")
    return lines


def format_markdown(result: dict, limit: int) -> str:
    b = result["before"]
    a = result["after"]
    s = result["summary"]
    lines = [
        "# 万象平衡快照前后对照",
        "",
        f"- **基线**：`{b['sourceSha']}`（v{b['formatVersion']}，dirty={b['sourceDirty']}，`rulesSha256={str(b['rulesSha256'])[:12]}`）",
        f"- **候选**：`{a['sourceSha']}`（v{a['formatVersion']}，dirty={a['sourceDirty']}，`rulesSha256={str(a['rulesSha256'])[:12]}`）",
        f"- **规则摘要一致**：{'是（无规则变化）' if result['identicalRules'] else '否（存在规则变化）'}",
        "",
        "## 改动概览（实改值 vs 仅增删导出项 vs 仅核对未变）",
        "",
        f"- **角色**：共核对 {s['rolesChecked']} 名；**实改值 {s['rolesValueChanged']} 名**，仅增删导出项 {s['rolesSchemaOnly']} 名，未变 {s['rolesUnchanged']} 名"
        + (f"（`{'`, `'.join(s['unchangedRoleIds'])}`）" if s["unchangedRoleIds"] else ""),
        f"- **技能候选**：共核对 {s['skillsChecked']} 个；**实改值 {s['skillsValueChanged']} 个**，仅增删导出项 {s['skillsSchemaOnly']} 个，未变 {s['skillsUnchanged']} 个",
    ]
    if s["globalSectionChanges"]:
        parts = [
            f"{SECTION_LABELS.get(k, k)}（实改 {v['changed']} / 增删 {v['addedOrRemoved']}）"
            for k, v in s["globalSectionChanges"].items()
        ]
        lines.append("- **全局与战场规则**：" + "；".join(parts))
    else:
        lines.append("- **全局与战场规则**：无变化")

    if b["formatVersion"] != a["formatVersion"]:
        lines.append(f"- **跨结构版本提示**：快照从 v{b['formatVersion']} 升至 v{a['formatVersion']}，`added` 项可能包含新导出的既有规则，请区分“真实改数”与“新增导出字段”。")

    effective_limit = limit if limit > 0 else 10**9

    for sec, rows in result["sections"].items():
        changed = [r for r in rows if r["kind"] == "changed"]
        added_removed = [r for r in rows if r["kind"] in ("added", "removed")]
        meta = [r for r in rows if r["kind"] == "meta"]
        lines += ["", f"## {SECTION_LABELS.get(sec, sec)}（实改 {len(changed)} / 增删 {len(added_removed)}）"]
        if changed:
            lines.append("")
            lines.extend(render_rows(changed, effective_limit))
        if added_removed:
            lines += ["", f"### 新增或移除导出项（{len(added_removed)} 项）", ""]
            lines.extend(render_rows(added_removed, effective_limit))
        if meta:
            lines += ["", f"### 元数据变化（{len(meta)} 项）", ""]
            lines.extend(render_rows(meta, effective_limit))

    if result["roles"]:
        lines += ["", "## 角色与技能明细（按实改值优先）", ""]
        ordered_roles = sorted(
            result["roles"].items(),
            key=lambda kv: (-sum(1 for r in kv[1] if r["kind"] == "changed"), kv[0]),
        )
        for rid, rows in ordered_roles:
            changed = [r for r in rows if r["kind"] == "changed"]
            other = [r for r in rows if r["kind"] != "changed"]
            lines += [f"### `{rid}`（实改 {len(changed)} / 增删或其他 {len(other)}）", ""]
            lines.extend(render_rows(changed + other, effective_limit))
            lines.append("")

    if result["presentationReminders"] is not None:
        lines += ["", "## 文案提醒变化", ""]
        lines.append(f"- 改前：{len(result['presentationReminders']['before'])} 条")
        lines.append(f"- 改后：{len(result['presentationReminders']['after'])} 条")

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path, help="基线 balance-snapshot.json 或包含它的目录")
    parser.add_argument("after", type=Path, help="候选 balance-snapshot.json 或包含它的目录")
    parser.add_argument("--role", action="append", default=[], help="只对比指定角色 ID（可重复）")
    parser.add_argument("--include-meta", action="store_true", help="包含 source/reason/condition 等元数据变化")
    parser.add_argument("--limit", type=int, default=40, help="每节最多输出行数，0 表示不截断（默认 40）")
    parser.add_argument("--json", action="store_true", help="输出结构化 JSON 而非 Markdown")
    args = parser.parse_args()

    try:
        b_path, b_cov_path = resolve_snapshot(args.before)
        a_path, a_cov_path = resolve_snapshot(args.after)
        b_snap = json.loads(b_path.read_text(encoding="utf-8"))
        a_snap = json.loads(a_path.read_text(encoding="utf-8"))
        b_cov = json.loads(b_cov_path.read_text(encoding="utf-8")) if b_cov_path else None
        a_cov = json.loads(a_cov_path.read_text(encoding="utf-8")) if a_cov_path else None
    except (OSError, ValueError) as exc:
        print(f"无法对比快照：{exc}", file=sys.stderr)
        return 2

    role_filter = set(args.role) if args.role else None
    result = diff_snapshots(b_snap, a_snap, role_filter=role_filter, include_meta=args.include_meta,
                            before_cov=b_cov, after_cov=a_cov)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(format_markdown(result, args.limit), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
