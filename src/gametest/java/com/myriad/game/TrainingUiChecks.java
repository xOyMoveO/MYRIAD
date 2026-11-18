package com.myriad.game;

import com.myriad.client.SkillBarLayout;
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
            for(int count:new int[]{4,5})for(boolean charge:new boolean[]{true,false}){
                var dock=SkillBarLayout.at(size[0],size[1],count,charge,SkillBarLayout.DEFAULT_SCALE);
                var original=SkillBarLayout.at(size[0],size[1],count,charge,1F);
                h.assertTrue(dock.x()>=0&&dock.x()+dock.width()<=size[0]*.5F-96+.01F,"Skills stay left of the hotbar");
                h.assertTrue(dock.y()-23*dock.scale()>=0&&dock.y()+(28+(charge?14:0))*dock.scale()<=size[1]-1.99F,"Icons, ranks, recasts and charges fit above the bottom edge");
                h.assertTrue(dock.scale()>=original.scale()&&dock.scale()<=2F*original.scale()+.001F,"Narrow windows cap the new size without shrinking the old dock");
                if(size[0]>=576)h.assertTrue(Math.abs(dock.scale()-2F*original.scale())<.001F,"Roomy viewports double the physical default icon scale");
                states++;
            }
        }
        h.assertTrue(SkillBarLayout.savedScale(1,1F)==2F&&SkillBarLayout.savedScale(1,.8F)==.8F
                &&SkillBarLayout.savedScale(1,1.3F)==1.3F&&SkillBarLayout.savedScale(2,1F)==1F,
                "Migrate only the legacy default size, retaining custom and current settings");
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
        System.out.println("[MYRIAD-TRAINING-UI] PASS: "+states+" skill docks, responsive training/C panels, server guards, cooldowns, level budget and toggles");
        h.succeed();
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
        h.assertTrue(data.firstGroupAttribute("strength")==1&&data.getGold()==19820&&data.getBuff(id)==1,"One double-click buys exactly one rank, charges the server price and synchronizes its lit-cell buff");
        if(interaction.select(id,true,true))PlayerDataProvider.buyEquipment(player,id);
        h.assertTrue(data.firstGroupAttribute("strength")==1,"Repeated double-click event cannot buy a duplicate rank");
        data.setGold(0);PlayerDataProvider.buyEquipment(player,id);
        h.assertTrue(data.firstGroupAttribute("strength")==1&&data.getBuff(id)==1,"Rejected purchase neither advances rank nor lights another cell");
        data.setGold(20000);
        for(int rank=1;rank<10;rank++)PlayerDataProvider.buyEquipment(player,id);
        PlayerDataProvider.buyEquipment(player,id);
        h.assertTrue(data.firstGroupAttribute("strength")==10,"Server rejects an eleventh rank");
        for(int rank=0;rank<5;rank++)PlayerDataProvider.buyEquipment(player,"g1_attribute_wisdom");
        PlayerDataProvider.buyEquipment(player,"g1_attribute_defense");
        h.assertTrue(data.firstGroupAttribute("wisdom")==5&&data.firstGroupAttribute("defense")==0,"Server rejects a sixteenth total purchase");
        for(String key:com.myriad.character.rework.FirstGroupGrowth.ATTRIBUTES)data.setFirstGroupAttribute(key,0);
        com.myriad.character.rework.FirstGroupGrowth.apply(player,data);data.setGold(originalGold);TrainingMode.setNoGoldCost(free);
        System.out.println("[MYRIAD-ATTRIBUTE-ROWS] PASS: double-click purchase, exact payment/synced rank, insufficient gold, per-stat and total caps");
    }
}
