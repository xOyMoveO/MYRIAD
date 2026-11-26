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
import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.network.LuoxiaBowPosePayload.Phase;
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
        h.assertTrue(LuoxiaBow.NORMAL_WORK == 11 && Math.ceil(11 / LuoxiaBow.drawRate(.7, 1)) == 11
                && Math.ceil(11 / LuoxiaBow.drawRate(1.05, 1)) == 8
                && Math.ceil(11 / LuoxiaBow.drawRate(1.4, 1)) == 6
                && LuoxiaBow.drawRate(1.4, .5) == 1, "Baseline, haste and local time apply once");
        for (int rank = 1; rank <= 5; rank++)
            h.assertTrue(Math.abs(LuoxiaBow.minimumWork(rank) - (11 - .4 * rank)) < .00001, "Quick draw rank " + rank);
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
        var poses = new java.util.ArrayList<LuoxiaBowPosePayload>();
        p.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp
                        && cp.payload() instanceof LuoxiaBowPosePayload pose) poses.add(pose);
            }
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
        for (int tick = 1; tick <= 178; tick++) {
            final int i = tick;
            h.runAfterDelay(tick, () -> {
                if (done[0]) return;
                try {
                    if (i==1 || i==7 || i==23) BasicAttackSystem.tryAttack(p,LuoxiaBow.PRESS);
                    if (i==3 || i==18 || i==34) BasicAttackSystem.tryAttack(p,LuoxiaBow.RELEASE);
                    if (i==5) h.assertTrue(target.getHealth()==initialHealth && !LuoxiaBow.drawing(p), "Premature release never attacks or starts recovery");
                    if (i==6) level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                    if (i==19) {
                        h.assertTrue(target.getHealth()==initialHealth, "Solid wall stops arrows before a target");
                        level.setBlockAndUpdate(wall, previous);
                    }
                    if (i==35) {
                        LuoxiaBow.input(p, LuoxiaBow.PRESS, 10);
                        h.assertTrue(LuoxiaBow.drawing(p), "Can draw on the tick after a release");
                        LuoxiaBow.input(p, LuoxiaBow.RELEASE, 9);
                        LuoxiaBow.input(p, LuoxiaBow.PRESS, 10);
                        h.assertTrue(LuoxiaBow.drawing(p), "Stale release and duplicate press preserve the active draw");
                        LuoxiaBow.input(p, LuoxiaBow.CANCEL, 10);
                        LuoxiaBow.input(p, LuoxiaBow.RELEASE, 10);
                        h.assertTrue(!LuoxiaBow.drawing(p), "A cancelled gesture cannot release");
                    }
                    if (i==39) {
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
                    if (i==88) {
                        data.setSkillLevel(graph.get(7).getId(), 0);
                        data.setSkillLevel(graph.get(6).getId(), 1);
                        TrainingMode.setNoCooldown(true);
                        SkillSystem.cast(p, CharacterType.SLOT_R);
                    }
                    if (i==122) {
                        long[] waves = poses.stream().filter(s -> s.skill()==6 && s.phase()==Phase.RELEASE)
                                .mapToLong(LuoxiaBowPosePayload::tick).toArray();
                        h.assertTrue(waves.length==3 && waves[1]-waves[0]==12 && waves[2]-waves[1]==12
                                && poses.stream().anyMatch(s -> s.skill()==6 && s.phase()==Phase.RELOAD),
                                "Three fan releases and real reloads at twelve-action-tick intervals");
                        data.setSkillLevel(graph.get(2).getId(), 1);
                        SkillSystem.cast(p, CharacterType.SLOT_Q);
                    }
                    if (i==126) {
                        LuoxiaBow.input(p, LuoxiaBow.PRESS, 11);
                        data.setSkillLevel(graph.get(3).getId(), 1);
                        SkillSystem.cast(p, CharacterType.SLOT_W);
                        h.assertTrue(LuoxiaBow.drawing(p), "Slant Step preserves the nocked basic arrow");
                    }
                    if (i==130) p.getAttribute(com.myriad.registry.ModAttributes.ATTACK_SPEED).setBaseValue(1.4);
                    if (i==134) {
                        LuoxiaBow.input(p, LuoxiaBow.RELEASE, 11);
                        LuoxiaBow.input(p, LuoxiaBow.RELEASE, 11);
                        h.assertTrue(poses.stream().filter(s -> s.token()==11 && s.skill()<2
                                && s.phase()==Phase.RELEASE).count()==1, "Speed changed mid-draw without losing work or duplicating the shot");
                    }
                    if (i==143) {
                        LuoxiaBow.input(p, LuoxiaBow.PRESS, 12);
                        data.setBuff("g1_stun", 3);
                    }
                    if (i==144) {
                        h.assertTrue(!LuoxiaBow.drawing(p), "Stun clears an unfinished draw");
                        data.setBuff("g1_stun", 0);
                        data.setSkillLevel(graph.get(5).getId(), 1);
                        SkillSystem.cast(p, CharacterType.SLOT_E);
                    }
                    if (i==148) data.setBuff("g1_stun", 3);
                    if (i==152) {
                        h.assertTrue(poses.stream().anyMatch(s -> s.skill()==5 && s.phase()==Phase.CANCEL)
                                && poses.stream().noneMatch(s -> s.skill()==5 && s.phase()==Phase.RELEASE),
                                "Interrupted Last Sun closes the pose before any release");
                        data.setBuff("g1_stun", 0);
                        SkillSystem.cast(p, CharacterType.SLOT_E);
                    }
                    AbilityRuntime.tick(p);
                    if (i==85) {
                        h.assertTrue(before[1]-target.getHealth() >= 59 && data.getMana()==480, "Ultimate stays on the locked line despite turn");
                        long[] shots = poses.stream().filter(s -> s.skill()==4 && s.phase()==Phase.RELEASE)
                                .mapToLong(LuoxiaBowPosePayload::tick).toArray();
                        h.assertTrue(shots.length==3 && shots[1]-shots[0]==5 && shots[2]-shots[1]==5,
                                "Feather Burst pose matches three distinct release ticks");
                        var locked = poses.stream().filter(s -> s.skill()==7 && s.phase()==Phase.LOCK).findFirst().orElseThrow();
                        var loose = poses.stream().filter(s -> s.skill()==7 && s.phase()==Phase.RELEASE).findFirst().orElseThrow();
                        h.assertTrue(locked.yaw()==loose.yaw() && locked.pitch()==loose.pitch()
                                && loose.tick()-locked.tick()==7, "The arrow uses the authority's locked aim");
                    }
                    if (i==178) {
                        h.assertTrue(poses.stream().filter(s -> s.skill()==5 && s.phase()==Phase.RELEASE).count()==1
                                && poses.stream().filter(s -> s.skill()==2 && s.phase()==Phase.RELEASE).count()==1
                                && poses.stream().anyMatch(s -> s.skill()==3 && s.phase()==Phase.STEP),
                                "Heavy arrow, instant mark and actual step pose events");
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
