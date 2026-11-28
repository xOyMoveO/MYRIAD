#!/usr/bin/env python
"""从万象平衡证据记录生成 Markdown。仅使用 Python 标准库。"""
import argparse
import json
import math
import sys
from pathlib import Path


MEASURED = {"源码核对", "理论上界", "离线推演", "引擎抽检", "真人反馈", "策划目标"}
UNMEASURED = {"未测", "缺输入", "不适用"}
RUN_STATES = {"通过", "失败", "未运行", "证据不足"}


def required_text(record, field):
    value = record.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field} 应当填写非空文字")
    return value


def records(report, field):
    items = report.get(field)
    if not isinstance(items, list) or not all(isinstance(item, dict) for item in items):
        raise ValueError(f"{field} 应当是对象列表")
    return items


def table_row(*cells):
    values = (str(cell).replace("\\", "\\\\").replace("|", "\\|").replace("\n", "<br>")
              for cell in cells)
    return "| " + " | ".join(values) + " |"


def observation_row(item):
    metric = required_text(item, "metric")
    basis = required_text(item, "basis")
    note = required_text(item, "note")
    source = item.get("source", "")
    unit = item.get("unit", "")
    if not isinstance(unit, str) or not isinstance(source, str):
        raise ValueError(f"{metric} 的 unit 与 source 应当是文字")
    if "value" not in item:
        raise ValueError(f"{metric} 缺少 value")
    value = item["value"]
    if basis in UNMEASURED:
        if value is not None:
            raise ValueError(f"{metric} 的依据是{basis}，value 应当为 null")
        reading = "—"
    elif basis in MEASURED:
        if not source.strip():
            raise ValueError(f"{metric} 的{basis}缺少来源")
        if isinstance(value, bool) or not isinstance(value, (str, int, float)):
            raise ValueError(f"{metric} 应当填写数值或定性读数")
        if isinstance(value, str) and not value.strip():
            raise ValueError(f"{metric} 的定性读数为空")
        if isinstance(value, float) and not math.isfinite(value):
            raise ValueError(f"{metric} 的数值必须有限")
        reading = f"{value} {unit}".strip()
    else:
        raise ValueError(f"{metric} 的依据无法识别：{basis}")
    level = item.get("level")
    if level is not None and (type(level) is not int or level not in (1, 2, 3)):
        raise ValueError(f"{metric} 的提醒级别应当为 1、2 或 3")
    return table_row(metric, basis, reading, f"{level} 级" if level is not None else "—", note, source or "—")


def render(report):
    if not isinstance(report, dict):
        raise ValueError("报告根元素应当是对象")
    title = required_text(report, "title")
    baseline = required_text(report, "baseline")
    scenario = required_text(report, "scenario")
    conditions = required_text(report, "conditions")
    observations = records(report, "observations")
    if not observations:
        raise ValueError("请至少填写一项观察，尚缺读数的指标可以标为未测")

    lines = [f"# {title}", "", f"**规则版本**　{baseline}", "", f"**场景**　{scenario}",
             "", f"**初态**　{conditions}", "", "## 读数与依据", "",
             table_row("指标", "依据", "读数", "提醒", "范围与解释", "来源"),
             table_row(*(["---"] * 6))]
    lines.extend(observation_row(item) for item in observations)
    checks = records(report, "checks")
    if checks:
        lines += ["", "## 技术执行", "", table_row("项目", "状态", "来源"), table_row("---", "---", "---")]
    for check in checks:
        name = required_text(check, "name")
        status = required_text(check, "status")
        if status not in RUN_STATES:
            raise ValueError(f"{name} 的执行状态无法识别：{status}")
        source = check.get("source", "")
        if not isinstance(source, str) or (status in {"通过", "失败"} and not source.strip()):
            raise ValueError(f"{name} 的执行来源无效或缺失")
        lines.append(table_row(name, status, source or "—"))

    steps = report.get("reproduce")
    if not isinstance(steps, list) or not all(isinstance(s, str) and s.strip() for s in steps):
        raise ValueError("reproduce 应当是复查步骤的文字列表")
    if steps:
        lines += ["", "## 复查步骤", ""]
        lines.extend(f"{i}. {step}" for i, step in enumerate(steps, 1))
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="按随附模板填写的 UTF-8 JSON 文件")
    args = parser.parse_args()
    try:
        report = json.loads(args.report.read_text(encoding="utf-8"))
        print(render(report))
    except (OSError, ValueError) as exc:
        print(f"无法生成报告：{exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
