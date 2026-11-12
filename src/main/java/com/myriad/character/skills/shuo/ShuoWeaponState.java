package com.myriad.character.skills.shuo;

import com.myriad.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Vanilla equipment components replicate stance to trackers and late-joining observers. */
public final class ShuoWeaponState {
    public static final String DRAWN="myriad_shuo_drawn";
    public static final float GRIP_TO_MOUTH=14f/64;
    private ShuoWeaponState(){}
    public static boolean drawn(ItemStack item){
        return item.is(ModItems.SHUO_SWORD.get())&&item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(DRAWN).orElse(false);
    }
    public static void set(ItemStack item,boolean drawn){
        if(!item.is(ModItems.SHUO_SWORD.get())||drawn(item)==drawn)return;
        CustomData.update(DataComponents.CUSTOM_DATA,item,tag->{if(drawn)tag.putBoolean(DRAWN,true);else tag.remove(DRAWN);});
    }
    public static void sync(ServerPlayer player,boolean drawn){
        set(player.getInventory().getItem(0),drawn);set(player.getMainHandItem(),drawn);
    }
}
