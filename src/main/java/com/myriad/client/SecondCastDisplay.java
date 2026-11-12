package com.myriad.client;

/** 二段技能 HUD 约定：服务器同步 <技能ID>_recast，技能图标上方便自动显示剩余窗口。 */
public final class SecondCastDisplay {
    private SecondCastDisplay() { }

    public static int remainingTicks(String skillId) {
        return Math.max(0, ClientDataHolder.buffs().getOrDefault(skillId + "_recast", 0));
    }
}
