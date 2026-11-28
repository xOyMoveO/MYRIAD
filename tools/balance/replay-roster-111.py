"""Replay the first four roster additions and attach their Fabric evidence."""

from chixiao_calibration import ChixiaoReplayMixin
from combo_calibration import Replay
from roster_calibration import RosterSpec, main
from shuo_calibration import ShuoReplayMixin
from yike_calibration import YikeReplayMixin


class YikeReplay(YikeReplayMixin, Replay):
    pass


class ShuoReplay(ShuoReplayMixin, Replay):
    pass


class ChixiaoReplay(ChixiaoReplayMixin, Replay):
    pass


SPEC = RosterSpec(
    ticket=111,
    title="伊珂、朔、铆钉与赤霄首轮平衡复跑",
    roles={"yike": "伊珂", "shuo": "朔", "rivet": "铆钉", "chixiao": "赤霄"},
    replays={"yike": YikeReplay, "shuo": ShuoReplay, "chixiao": ChixiaoReplay},
    engine={
        "first_group": None,
        "balance_vertical": "BALANCE_VERTICAL ",
        "balance_haste": "BALANCE_VERTICAL ",
        "yike": "BALANCE_YIKE ",
        "balance_shuo_chixiao": "MYRIAD_ROSTER_111 ",
        "rivet": "BALANCE_CALIBRATION ",
    },
    battlefield_notes=[
        "主图带己方兵对线、正常走位、真实操作及其它清线角色用于区分技能效率与共同兵线基准。",
        "野区这里只观察侦察机和重装步行机单怪，正式路线含一营多怪、跑位和仇恨转移，首领继续用 #109 的独立场景。",
        "塔在模拟地图重放一、二、三档，正式樱隙前线只有一、三塔。伊珂当前炮弹跳过防御塔，保留现值，交全阵容跨定位复查。",
    ],
)


if __name__ == "__main__":
    main(SPEC)
