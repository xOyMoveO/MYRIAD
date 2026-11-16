package com.myriad.game;

import net.fabricmc.api.ModInitializer;

/** Test-mod entry point; this source set is excluded from the release JAR. */
public final class MyriadGameTests implements ModInitializer {
    @Override
    public void onInitialize() {
        CombatRegressionTests.register();
        GameTestTicks.initialize();
    }
}
