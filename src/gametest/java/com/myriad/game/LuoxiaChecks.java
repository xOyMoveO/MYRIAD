package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class LuoxiaChecks {
    static void run(GameTestHelper h) {
        var role = CharacterType.LUOXIA;
        var graph = FirstGroupSkills.candidates(role.getId());
        h.assertTrue(graph.size() == 8 && FirstGroupGrowth.health("luoxia", 20) == 2000, "Complete archer roster and health budget");
        for (int p : new int[]{0,1}) for (int e : new int[]{4,5}) for (int r : new int[]{6,7}) {
            var ranks = Map.of(graph.get(p).getId(),1, graph.get(e).getId(),1, graph.get(r).getId(),1);
            h.assertTrue(FirstGroupSkills.resolve(role,2,ranks)==graph.get(e)
                    && FirstGroupSkills.resolve(role,3,ranks)==graph.get(r)
                    && FirstGroupSkills.selected("luoxia",0,ranks)==graph.get(p), "All eight independent branch combinations");
        }
        var uniqueArrows = new java.util.HashSet<java.util.List<AbilityVfxGeometry.Quad>>();
        var direction = new Vec3(1,2,-3).normalize();
        for (int shape = 0; shape < 8; shape++) {
            var packet = new AbilityVfxPayload(1,-1,14,2,AbilityVfxGeometry.FLIGHT,Vec3.ZERO,direction,.2f,shape,3,0,false,1);
            var geometry = AbilityVfxGeometry.build(packet,.5f,false);
            double front = geometry.stream().flatMap(q -> java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                    .mapToDouble(direction::dot).max().orElseThrow();
            h.assertTrue(uniqueArrows.add(geometry) && packet.valid() && geometry.size() > 24
                    && geometry.size() <= AbilityVfxGeometry.maxQuads(packet)
                    && Math.abs(front - .5) < .00001
                    && geometry.stream().allMatch(q -> q.material() == com.myriad.character.ability.AbilityVfxMaterials.LUOXIA_ARROW_MODEL)
                    && AbilityVfxGeometry.build(packet,3,false).isEmpty(), "Arrow model must be distinct, textured, tip-aligned and finite: "+shape);
        }
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        boolean mana = TrainingMode.isNoMana(), cooldown = TrainingMode.isNoCooldown();
        ModState.setEnabled(true); TrainingMode.setEnabled(true);
        TrainingMode.setNoMana(false); TrainingMode.setNoCooldown(false);
        var level = h.getLevel();
        var p = (ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        var origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,3,4)));
        p.setPos(origin); p.setNoGravity(true); p.setYRot(0); p.setXRot(0);
        PlayerDataProvider.chooseCharacter(p,role);
        h.assertTrue(p.getMainHandItem().is(ModItems.GUIXIA_BOW.get()), "Selecting Luoxia grants the dedicated bow");
        var data = PlayerDataProvider.get(p);
        data.setMana(700);
        data.setSkillLevel(graph.get(4).getId(),7);
        data.setSkillLevel(graph.get(7).getId(),3);
        var target = h.spawn(EntityTypes.PIG,new BlockPos(4,3,8));
        target.setPos(origin.add(0,1,4)); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        float initialHealth = target.getHealth();
        var wall = BlockPos.containing(origin.add(0,1,2));
        var previous = level.getBlockState(wall);
        boolean[] done = {false};
        float[] before = new float[2];
        for (int tick = 1; tick <= 85; tick++) {
            final int i = tick;
            h.runAfterDelay(tick, () -> {
                if (done[0]) return;
                try {
                    if (i==1 || i==7 || i==23) BasicAttackSystem.tryAttack(p,LuoxiaBow.PRESS);
                    if (i==3 || i==13 || i==29) BasicAttackSystem.tryAttack(p,LuoxiaBow.RELEASE);
                    if (i==5) h.assertTrue(target.getHealth()==initialHealth && LuoxiaBow.remaining(p)==0, "Premature release never attacks or starts recovery");
                    if (i==6) level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                    if (i==19) {
                        h.assertTrue(target.getHealth()==initialHealth, "Solid wall stops arrows before a target");
                        level.setBlockAndUpdate(wall, previous);
                    }
                    if (i==36) {
                        h.assertTrue(target.getHealth()<initialHealth && data.getMana()==700, "A charged basic attack is free and hits along its flight");
                        before[0] = target.getHealth();
                    }
                    if (i==42) SkillSystem.cast(p,CharacterType.SLOT_E);
                    if (i==57) {
                        float loss = before[0]-target.getHealth();
                        h.assertTrue(loss > 125 && loss < 150 && data.getMana()==635,
                                "All three arrows hit with the third halved and only one mana payment: "+loss);
                        before[1] = target.getHealth();
                    }
                    if (i==61) SkillSystem.cast(p,CharacterType.SLOT_R);
                    if (i==74) p.setYRot(90);
                    AbilityRuntime.tick(p);
                    if (i==85) {
                        h.assertTrue(before[1]-target.getHealth() >= 59 && data.getMana()==480, "Ultimate stays on the locked line despite turn");
                        done[0] = true;
                        clean(p,h,target,wall,previous,enabled,training,mana,cooldown);
                        h.succeed();
                    }
                } catch (RuntimeException failure) {
                    done[0] = true;
                    clean(p,h,target,wall,previous,enabled,training,mana,cooldown);
                    throw failure;
                }
            });
        }
    }

    private static void clean(ServerPlayer p, GameTestHelper h, net.minecraft.world.entity.Entity target,
            BlockPos wall, net.minecraft.world.level.block.state.BlockState previous, boolean enabled, boolean training,
            boolean mana, boolean cooldown) {
        AbilityRuntime.clear(p); p.discard(); target.discard();
        h.getLevel().setBlockAndUpdate(wall, previous);
        TrainingMode.setNoMana(mana); TrainingMode.setNoCooldown(cooldown); TrainingMode.setEnabled(training); ModState.setEnabled(enabled);
    }
}
