"""Replay #113 on paired Fabric releases and attach the measured engine outcomes."""

from roster_113_evidence import ENGINE_113, REPLAYS_113, formation_evidence, formation_markdown
from roster_calibration import RosterSpec, main


def augment(report, rules, defaults, configs, args):
    report["formation"] = formation_evidence(rules, defaults, configs, args.baseline_engine)


SPEC = RosterSpec(
    ticket=113,
    title="凌渊、寒泽、墨尘与云笙首轮平衡复跑",
    roles={"lingyuan": "凌渊", "hanze": "寒泽", "xuanchen": "墨尘", "yunsheng": "云笙"},
    replays=REPLAYS_113,
    engine={key: marker for key, marker in ENGINE_113.items() if not key.startswith("balance_roster_113_")},
    battlefield_notes=[
        "四人共享同一批兵线生命与属性购买输入。固定力量、Q／普攻与无己方兵线的读数整体慢于真人参考，"
        "交 #114 并查合法智慧构筑、完整轮转、友方兵线贡献与共享波次强度。",
        "两种单怪均有实际反击与尾刀归属。墨尘及云笙中营清完血量偏低，复查实际站位、"
        "智慧购买、多怪进战与撤退成本后可再选值。",
        "各角色均使用真实普攻及己方兵线推进，合成中塔为取样；正式樱隙只配置外塔与高地塔。"
        "角色与共同战场均沿用现值，跨阵容复查用相同时点的无英雄和带英雄兵线对照。",
    ],
    augment=augment,
    render_extra=formation_markdown,
)


if __name__ == "__main__":
    main(SPEC)
