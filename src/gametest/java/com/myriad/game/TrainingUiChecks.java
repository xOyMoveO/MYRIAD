package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.client.CombatHudLayout;
import com.myriad.client.HudLayoutConfig;
import com.myriad.client.gui.TrainLayout;
import com.myriad.character.CharacterType;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Server requests plus shared responsive geometry, not a graphical acceptance test. */
final class TrainingUiChecks {
    static void run(GameTestHelper h){
        verifyDeferredOpening(h);
        SkillChoiceUiChecks.verify(h);
        CharacterChoiceUiChecks.verify(h);
        CombatHudStateChecks.run(h);
        CombatChatChecks.verify(h::assertTrue);
        verifyHudMigration(h);
        int states=0;
        for(var size:new int[][]{{320,180},{320,240},{427,240},{576,344},{854,480},{960,540}}){
            var train=TrainLayout.create(size[0],size[1],9999);
            h.assertTrue(train.panel().x()>=0&&train.panel().right()<=size[0]&&train.panel().bottom()<=size[1],"Training panel fits the window");
            for(int row=0;row<TrainLayout.ROWS;row++){
                var initial=TrainLayout.create(size[0],size[1],0);
                var moved=TrainLayout.create(size[0],size[1],initial.row(row).y()-initial.viewport().y());
                var box=moved.row(row);
                h.assertTrue(box.y()>=moved.viewport().y()&&box.bottom()<=moved.viewport().bottom(),"All training controls can be fully scrolled into view");
                var label=moved.label(row);var actions=moved.actions(row);
                h.assertTrue(label.right()<actions.x()&&actions.right()<box.right()&&actions.y()>=box.y()&&actions.bottom()<=box.bottom(),"Left labels never overlap right-hand switches/actions");
            }
            for (float scale : new float[]{.5F, 1F, 2F}) {
                var layout = CombatHudLayout.at(size[0], size[1], 0, 1, scale, 3, 2, true);
                var panel = layout.bounds();
                h.assertTrue(panel.x() == 6 && panel.y() >= 6 && panel.right() <= size[0] - 6,
                        "The default panel stays within the left edge and viewport");
                h.assertTrue(panel.right() <= size[0] / 2 - 98 || panel.bottom() <= size[1] - 29,
                        "Large and compact combat panels clear the original hotbar");
                h.assertTrue(layout.contentX() + CombatHudLayout.SLOT_STEP * 4 + CombatHudLayout.SLOT
                        <= layout.width() - 6, "All five slots fit");
                states++;
            }
        }
        var moved = CombatHudLayout.at(960, 540, .5F, .6F, 1.25F, 0, 0, false);
        h.assertTrue(moved.bounds().x() == 480 && moved.bounds().bottom() == 324 && moved.scale() == 1.25F,
                "A dragged panel uses one scale and retains its normalized bottom-left anchor");
        var regular = CombatHudLayout.at(960, 540, 0, 1, 1.5F, 0, 0, false);
        var larger = CombatHudLayout.at(960, 540, 0, 1, 1.55F, 0, 0, false);
        h.assertTrue(!regular.compact() && !larger.compact() && larger.bounds().width() > regular.bounds().width(),
                "Increasing the editor scale does not switch to smaller compact contents");
        var offhand = CombatHudLayout.hotbar(576, 344, 29, 0);
        var armed = CombatHudLayout.at(576, 344, 0, 1, 1F, 0, 0, false, offhand);
        h.assertTrue(armed.bounds().bottom() <= offhand.y() - CombatHudLayout.GAP,
                "The equipped offhand extender is treated as part of the vanilla hotbar");
        boolean mod=ModState.isEnabled(),training=TrainingMode.isEnabled(),cd=TrainingMode.isNoCooldown(),mana=TrainingMode.isNoMana(),gold=TrainingMode.isNoGoldCost();
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        var identity=new net.minecraft.server.players.NameAndId(p.getGameProfile());
        var list=h.getLevel().getServer().getPlayerList();boolean alreadyOp=list.isOp(identity);
        try{
            ModState.setEnabled(true);TrainingMode.setEnabled(false);
            PlayerDataProvider.chooseCharacter(p,CharacterType.TIANXING);var data=PlayerDataProvider.get(p);
            var skill=com.myriad.character.rework.FirstGroupSkills.candidates("tianxing").get(2);
            data.setCooldown(skill.getId(),140);
            TrainingActions.apply(p,"refresh",true);
            h.assertTrue(data.getCooldown(skill.getId())==140,"Refresh is rejected outside training");
            TrainingActions.apply(p,"enabled",true);
            h.assertTrue(!TrainingMode.isEnabled(),"An unprivileged packet cannot enable global training");
            list.op(identity,java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER),java.util.Optional.empty());
            TrainingActions.apply(p,"enabled",true);
            h.assertTrue(TrainingMode.isEnabled(),"An operator can enable training through the panel request");
            verifyAttributePurchases(h,p);
            data.setBuff("unrelated_effect",60);TrainingActions.apply(p,"refresh",true);
            h.assertTrue(data.getCooldown(skill.getId())==0&&data.getBuff("unrelated_effect")==60,"Refresh clears cooldowns but preserves unrelated effects");
            TrainingActions.apply(p,"level:20",true);
            h.assertTrue(data.getLevel()==20&&data.getSkillPoints()==20,"Direct level selection grants the correct total skill budget");
            TrainingActions.apply(p,"level:20",true);
            h.assertTrue(data.getSkillPoints()==20,"Repeated level setting never duplicates points");
            data.setSkillLevel(skill.getId(),3);data.setSkillPoints(17);
            TrainingActions.apply(p,"level:5",true);
            h.assertTrue(data.getLevel()==5&&data.getSkillPoints()==5&&data.getSkillLevels().isEmpty(),"Lowering resets branches and refunds only the new level budget");
            TrainingActions.apply(p,"level:21",true);TrainingActions.apply(p,"level:NaN",true);
            h.assertTrue(data.getLevel()==5,"Malformed/out-of-range levels are ignored on the server");
            data.setCooldown(skill.getId(),140);TrainingActions.apply(p,"nocooldown",true);
            h.assertTrue(TrainingMode.isNoCooldown()&&data.getCooldown(skill.getId())==0,"Enabling no-CD refreshes an existing cooldown immediately");
            data.setMana(0);TrainingActions.apply(p,"nomana",true);
            h.assertTrue(TrainingMode.isNoMana()&&data.getMana()>0,"Unlimited mana activates and visibly restores mana");
            TrainingActions.apply(p,"enabled",false);
            h.assertTrue(!TrainingMode.isEnabled()&&!TrainingMode.isNoCooldown()&&!TrainingMode.isNoMana()&&!TrainingMode.isNoGoldCost(),"Leaving training clears training cheats");
            data.setCooldown(skill.getId(),140);ModState.setEnabled(false);TrainingMode.setEnabled(true);
            TrainingActions.apply(p,"refresh",true);
            h.assertTrue(data.getCooldown(skill.getId())==140,"Disabled mod cannot change gameplay through training packets");
        }finally{
            com.myriad.character.ability.AbilityRuntime.clear(p);
            if(!alreadyOp)list.deop(identity);
            p.discard();TrainingMode.setEnabled(training);TrainingMode.setNoCooldown(cd);TrainingMode.setNoMana(mana);TrainingMode.setNoGoldCost(gold);ModState.setEnabled(mod);
        }
        System.out.println("[MYRIAD-TRAINING-UI] PASS: "+states+" combat HUD layouts, responsive training/C panels, server guards, cooldowns, level budget and toggles");
        h.succeed();
    }
    private static void verifyHudMigration(GameTestHelper h) {
        var legacy = JsonParser.parseString("""
                {"skillsDockVersion": 2, "status": {"x": 0.45, "visible": false},
                 "skills": {"x": 0.19, "scale": 1.65}, "attacks": {"y": 0.72, "scale": 1.2},
                 "minimap": {"x": 0.68, "y": 0.31, "scale": 0.8, "visible": false},
                 "target": {"x": 0.59, "y": 0.11, "scale": 1.1, "visible": true}}
                """).getAsJsonObject();
        var combat = HudLayoutConfig.stateFrom(legacy, HudLayoutConfig.Element.COMBAT);
        var minimap = HudLayoutConfig.stateFrom(legacy, HudLayoutConfig.Element.MINIMAP);
        var target = HudLayoutConfig.stateFrom(legacy, HudLayoutConfig.Element.TARGET);
        h.assertTrue(combat.equals(new HudLayoutConfig.State(0, 1, 1, true)),
                "Three legacy anchors migrate to one default-visible left-bottom combat panel");
        h.assertTrue(minimap.equals(new HudLayoutConfig.State(.68F, .31F, .8F, false))
                        && target.equals(new HudLayoutConfig.State(.59F, .11F, 1.1F, true)),
                "Migration preserves the independent minimap and target preferences");
        legacy.add("combat", JsonParser.parseString("""
                {"x": 0.17, "y": 0.81, "scale": 1.35, "visible": false}
                """));
        h.assertTrue(HudLayoutConfig.stateFrom(legacy, HudLayoutConfig.Element.COMBAT)
                        .equals(new HudLayoutConfig.State(.17F, .81F, 1.35F, false)),
                "Current panel preferences take precedence when a legacy configuration also exists");
    }

    private static void verifyDeferredOpening(GameTestHelper h){
        var q=new com.myriad.client.DeferredScreenRequest();Object session=new Object(),world=new Object();
        int[] opened={0};String[] screen={"chat"};
        q.submit(session,world,()->{opened[0]++;screen[0]="train";});
        h.assertTrue(opened[0]==0,"Command interception must never open inside the chat submit stack");
        q.tick(session,world,true,false);
        h.assertTrue(opened[0]==0,"Retained chat screens defer opening");
        screen[0]=null; // Vanilla ChatScreen.keyPressed closes chat after the command callback returns.
        q.tick(session,world,false,false);q.tick(session,world,false,false);
        h.assertTrue(opened[0]==1&&"train".equals(screen[0]),"Next-tick opening survives chat closure and runs exactly once");
        q.submit(session,world,()->opened[0]++);q.tick(new Object(),world,false,false);
        q.tick(session,world,false,false);h.assertTrue(opened[0]==1,"Reconnect cancels stale requests");
        q.submit(session,world,()->opened[0]++);q.tick(session,new Object(),false,false);
        q.submit(session,world,()->opened[0]++);q.tick(session,world,false,true);
        q.submit(session,world,()->opened[0]++);q.clear();q.tick(session,world,false,false);
        h.assertTrue(opened[0]==1,"World changes, unrelated screens and explicit disconnect cleanup cancel requests");
        System.out.println("[MYRIAD-DEFERRED-UI] PASS: deferred chat closure, one-shot opening and session cancellation");
    }
    private static void verifyAttributePurchases(GameTestHelper h,ServerPlayer player){
        var data=PlayerDataProvider.get(player);
        var interaction=new com.myriad.client.gui.SkillChoiceInteraction();
        String id="g1_attribute_strength";int originalGold=data.getGold();boolean free=TrainingMode.isNoGoldCost();
        TrainingMode.setNoGoldCost(false);data.setGold(20000);
        h.assertTrue(!interaction.select(id,false,true),"Attribute single-click only selects");
        if(interaction.select(id,true,true))PlayerDataProvider.buyEquipment(player,id);
        h.assertTrue(data.firstGroupAttribute("strength")==1&&data.getGold()==19640&&data.getBuff(id)==1,
                "One double-click buys one point, charges 360 and synchronizes the count");
        if(interaction.select(id,true,true))PlayerDataProvider.buyEquipment(player,id);
        h.assertTrue(data.firstGroupAttribute("strength")==1,"Repeated double-click event cannot buy a duplicate rank");
        data.setGold(0);PlayerDataProvider.buyEquipment(player,id);
        h.assertTrue(data.firstGroupAttribute("strength")==1&&data.getBuff(id)==1,"Rejected purchase leaves both server and synced counts unchanged");
        data.setGold(20000);
        for(int rank=1;rank<10;rank++)PlayerDataProvider.buyEquipment(player,id);
        PlayerDataProvider.buyEquipment(player,id);
        h.assertTrue(data.firstGroupAttribute("strength")==11&&data.getBuff(id)==11,"Server and client count an eleventh point");
        for(int rank=0;rank<5;rank++)PlayerDataProvider.buyEquipment(player,"g1_attribute_wisdom");
        PlayerDataProvider.buyEquipment(player,"g1_attribute_defense");
        h.assertTrue(data.firstGroupAttribute("wisdom")==5&&data.firstGroupAttribute("defense")==1,"Purchases continue above fifteen total points");
        data.setFirstGroupAttribute("strength", 0);
        data.setGold(800);
        var first = new com.myriad.network.ProgressAction(1, data.getCharacterId(), id, 0, 360);
        first.handle(player);
        first.handle(player);
        h.assertTrue(data.getGold() == 440 && data.firstGroupAttribute("strength") == 1, "Duplicate receipt charges once");
        new com.myriad.network.ProgressAction(2, data.getCharacterId(), id, 0, 360).handle(player);
        h.assertTrue(data.getGold() == 440 && data.firstGroupAttribute("strength") == 1,
                "Reject a stale quote even when the wallet can pay 432");
        new com.myriad.network.ProgressAction(3, data.getCharacterId(), id, 1, 432).handle(player);
        h.assertTrue(data.getGold() == 8 && data.firstGroupAttribute("strength") == 2, "Explicit new price may settle");
        for(String key:com.myriad.character.rework.FirstGroupGrowth.ATTRIBUTES)data.setFirstGroupAttribute(key,0);
        com.myriad.character.rework.FirstGroupGrowth.apply(player,data);data.setGold(originalGold);TrainingMode.setNoGoldCost(free);
        System.out.println("[MYRIAD-ATTRIBUTE-ROWS] PASS: double-click purchase, exact payment/synced rank, insufficient gold, and counts above the old limits");
    }
}
