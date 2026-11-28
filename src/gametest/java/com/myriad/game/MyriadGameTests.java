package com.myriad.game;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Test-mod entry point; this source set is excluded from the release JAR. */
public final class MyriadGameTests implements ModInitializer {
    @Override
    public void onInitialize() {
        CombatRegressionTests.register();
        Registry.register(BuiltInRegistries.TEST_FUNCTION, Identifier.parse("myriad:battlefield_lane"),
                BattlefieldLaneChecks::run);
        GameTestTicks.initialize();
    }
}
