package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.CharacterType;
import com.myriad.combat.VanillaMeleeAttack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;

/** Guard against stale compiled actions/resources returning to a distribution. */
final class ActionRemovalChecks {
    private ActionRemovalChecks() { }
    static void verify(GameTestHelper h) {
        var loader = ActionRemovalChecks.class.getClassLoader();
        for (String type : List.of(
                "client/animation/AnimationPoseBlend", "client/animation/AnimationRotations",
                "client/animation/BlenderAnimationLibrary", "client/animation/BlenderBoneAnimation",
                "client/animation/BlenderPlayerRig", "client/animation/CombatAnimation",
                "client/animation/CombatAnimationController", "client/animation/CombatAnimationRegistry",
                "client/animation/WeaponGripCalibration", "combat/CombatAnimations",
                "combat/CombatAnimationPolicy", "combat/ComboSequence", "network/CombatAnimationPayload",
                "mixin/CombatHandAnimationMixin", "mixin/CombatPlayerAnimationMixin",
                "mixin/CombatItemAnimationMixin", "mixin/ItemStackGripAccessor", "mixin/ItemLayerGripAccessor")) {
            h.assertTrue(loader.getResource("com/myriad/" + type + ".class") == null,
                    "Removed player-action class must not be loadable: " + type);
        }
        for (String resource : List.of("animations/index.json", "blender_animations/index.json", "weapon_grips.json")) {
            h.assertTrue(loader.getResource("assets/myriad/" + resource) == null,
                    "Removed player-action resource must not be loadable: " + resource);
        }
        try (var stream = loader.getResourceAsStream("myriad.mixins.json")) {
            h.assertTrue(stream != null, "Normal gameplay mixins remain registered");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            // 26.3 creates packet-spawned entities with EntitySpawnRequest(LOAD, true) natively;
            // the old ClientEntitySpawnMixin redirect no longer has a target.
            var clients = json.getAsJsonArray("client").asList().stream().map(v -> v.getAsString()).toList();
            h.assertTrue(clients.equals(List.of("ClientInputAccessor", "PlayerTabOverlayMixin", "ShuoHeldLightMixin", "ClientAttackTimeMixin", "YikeArmPoseMixin",
                    "MyriadKeyboardMixin", "MyriadMouseMixin", "MyriadMinecraftClientMixin", "ChatComponentAccessor",
                    "MyriadLocalPlayerMixin",
                    "MyriadHandRendererMixin", "MyriadCameraMixin", "MyriadLivingRendererMixin", "MyriadNameTagMixin")),
                    "Gameplay, local time, attached light and Yike local weapon pose hooks remain; no retired player-action mixin returns");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        var ranged = Set.of("xuanchen", "hanze", "qingluo", "yunsheng", "yike", "luoxia");
        for (var role : CharacterType.values()) {
            h.assertTrue(VanillaMeleeAttack.usesVanilla(role) == !ranged.contains(role.getId()),
                    "Removing actions must not reroute specialized ranged attacks: " + role.getId());
        }
        h.assertTrue(!VanillaMeleeAttack.usesVanilla(null), "Unselected players remain vanilla, without a mod attack route");
        System.out.println("[MYRIAD-ACTION-REMOVAL] PASS: no retired player-action classes/assets/mixins, current roster attack routes preserved; new Night Shadow local first-person view is separate");
    }
}
