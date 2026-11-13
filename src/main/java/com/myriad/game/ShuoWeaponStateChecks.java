package com.myriad.game;

import com.myriad.character.skills.shuo.*;
import com.myriad.network.ShuoViewPayload;
import com.myriad.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Equipment state survives actual stack encoding, without relocating inventory or changing first-person assets. */
final class ShuoWeaponStateChecks {
    static void start(GameTestHelper h){
        var item=ModItems.SHUO_SWORD.get().getDefaultInstance();
        CustomData.update(DataComponents.CUSTOM_DATA,item,tag->tag.putInt("unrelated",42));
        var originalModel=item.get(DataComponents.ITEM_MODEL);
        h.assertTrue(!ShuoWeaponState.drawn(item),"New sword defaults to closed sheath");
        ShuoWeaponState.set(item,true);
        var bytes=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            ItemStack.STREAM_CODEC.encode(bytes,item);var remote=ItemStack.STREAM_CODEC.decode(bytes);
            h.assertTrue(ShuoWeaponState.drawn(remote)&&remote.get(DataComponents.ITEM_MODEL).equals(originalModel),"Vanilla equipment stack replication carries drawn state, preserving the original item model");
            ShuoWeaponState.set(remote,false);
            h.assertTrue(!ShuoWeaponState.drawn(remote)&&remote.get(DataComponents.CUSTOM_DATA).copyTag().getInt("unrelated").orElse(0)==42,"Returning to sheath removes only its own tag");
        }finally{bytes.release();}
        var other=Items.STICK.getDefaultInstance();ShuoWeaponState.set(other,true);
        h.assertTrue(other.get(DataComponents.CUSTOM_DATA)==null,"Other items remain untouched");
        try(var in=ShuoWeaponStateChecks.class.getResourceAsStream("/assets/myriad/models/item/shuo_sword.json");var reader=new java.io.InputStreamReader(in)){
            var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            var root=json.getAsJsonArray("elements").asList().stream().map(e->e.getAsJsonObject()).filter(e->e.get("name").getAsString().equals("sword_blade_face_000")).findFirst().orElseThrow();
            float bladeRoot=(root.getAsJsonArray("from").get(1).getAsFloat()-8)/16;
            h.assertTrue(Math.abs(bladeRoot-ShuoWeaponState.GRIP_TO_MOUTH)<1E-6,"Original blade root and translated sheath mouth coincide exactly");
        }catch(Exception ex){throw new IllegalStateException(ex);}
        var p=ShuoChecks.player(h,new net.minecraft.world.phys.Vec3(30,100,30),new ArrayList<>());
        p.getInventory().setItem(0,item);p.getInventory().setSelectedSlot(0);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,Items.STICK.getDefaultInstance());
        ShuoCombat.clear(p);ShuoCombat.draw(p,ShuoViewPayload.DRAW,9);
        h.assertTrue(ShuoWeaponState.drawn(p.getMainHandItem()),"Actual draw immediately updates tracked weapon equipment state");
        ShuoCombat.sheathe(p);
        h.assertTrue(ShuoWeaponState.drawn(p.getMainHandItem()),"Sword remains visibly drawn during the return animation");
        // Mock player has no automatic private-action tick; preserve the exact eight-tick assertion.
        for(int tick=1;tick<8;tick++)h.runAfterDelay(tick,()->ShuoCombat.tick(p));
        h.runAfterDelay(8,()->{
            ShuoCombat.tick(p);
            h.assertTrue(!ShuoWeaponState.drawn(p.getMainHandItem())&&!ShuoCombat.drawn(p),"The completed return switches third-person state, not the initial key press");
            h.assertTrue(p.getMainHandItem().is(ModItems.SHUO_SWORD.get())&&p.getOffhandItem().is(Items.STICK),"Visual hand selection never moves or overwrites actual equipment");
            ShuoCombat.draw(p,0,9);ShuoCombat.clear(p);
            h.assertTrue(!ShuoWeaponState.drawn(p.getMainHandItem()),"Role/death/reset runtime cleanup restores the closed equipment state");
            p.discard();System.out.println("[MYRIAD-SHUO-HELD-STATE] PASS: actual equipment codec, original assets, exact grip-to-mouth, draw/8-tick return/reset and unchanged inventory/offhand");
        });
    }
}
