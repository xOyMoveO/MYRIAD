package com.myriad.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/** Physical-client objective assertions only; no screenshot or subjective acceptance. */
public final class MyriadClientChecks implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> run());
    }
    private static void run() {
        var checks = new LinkedHashMap<String, java.util.function.Consumer<ClientAssertions>>();
        checks.put("flame_pipeline", FlamePipelineClientChecks::verify);
        checks.put("shuo_arm_model", ShuoArmClientChecks::verify);
        checks.put("shuo_sheathed_grip", ShuoSheathedGripChecks::verify);
        checks.put("shuo_held_transform", ShuoHeldHookClientChecks::verify);
        var results = new LinkedHashMap<String, Object>();
        var assertions = new ClientAssertions();
        Throwable failure = null;
        for (var entry : checks.entrySet()) {
            int before = assertions.count();
            try {
                entry.getValue().accept(assertions);
                results.put(entry.getKey(), java.util.Map.of("passed", true, "assertions", assertions.count() - before));
            } catch (Throwable error) {
                results.put(entry.getKey(), java.util.Map.of("passed", false, "assertions", assertions.count() - before, "error", error.toString()));
                failure = error;
                break;
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", failure == null);
        report.put("assertions", assertions.count());
        report.put("checks", results);
        var path = Path.of(System.getProperty("myriad.clientChecksReport", "../build/reports/fabric-client-checks.json")).toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        if (failure != null) throw new IllegalStateException("Physical client objective checks failed: " + path, failure);
        System.out.println("[MYRIAD-CLIENT-CHECKS] PASS: " + assertions.count() + " assertions; report=" + path);
    }
}
