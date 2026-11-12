package com.myriad.game;
import com.myriad.registry.ModAttributes;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;

/** Dedicated lane combatant: no vanilla iron-golem AI, mesh, sounds or collision. */
public final class LaneMinionEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> STYLE=SynchedEntityData.defineId(LaneMinionEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(LaneMinionEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DISPLAY_HEALTH=SynchedEntityData.defineId(LaneMinionEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DISPLAY_MAX_HEALTH=SynchedEntityData.defineId(LaneMinionEntity.class,EntityDataSerializers.FLOAT);
    private int attackTicks;
    public LaneMinionEntity(EntityType<? extends LaneMinionEntity> type,Level level){super(type,level);setNoAi(true);setPersistenceRequired();setCanPickUpLoot(false);}
    public static AttributeSupplier.Builder createAttributes(){return createMobAttributes().add(Attributes.MAX_HEALTH,95)
        .add(Attributes.MOVEMENT_SPEED,.28).add(Attributes.FOLLOW_RANGE,48).add(Attributes.KNOCKBACK_RESISTANCE,1)
        .add(ModAttributes.PHYSICAL_DEFENSE).add(ModAttributes.MAGIC_RESISTANCE);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(STYLE,0);builder.define(MOVING,false);builder.define(DISPLAY_HEALTH,1.0F);builder.define(DISPLAY_MAX_HEALTH,1.0F);}
    public void configure(Faction faction,int kind){entityData.set(STYLE,(faction==Faction.XUANXIAO?0:3)+kind);}
    public int style(){return entityData.get(STYLE);}
    public boolean moving(){return entityData.get(MOVING);}
    public void setMoving(boolean value){entityData.set(MOVING,value);}
    public float displayHealth(){return entityData.get(DISPLAY_HEALTH);}
    public float displayMaxHealth(){return entityData.get(DISPLAY_MAX_HEALTH);}
    public void setDisplayHealth(float health,float maximum){entityData.set(DISPLAY_HEALTH,Math.max(0,health));entityData.set(DISPLAY_MAX_HEALTH,Math.max(1,maximum));}
    public int attackTicks(){return attackTicks;}
    public void attackAnimation(){attackTicks=10;level().broadcastEntityEvent(this,(byte)4);}
    @Override public void handleEntityEvent(byte event){if(event==4)attackTicks=10;else super.handleEntityEvent(event);}
    @Override public void tick(){super.tick();if(attackTicks>0)attackTicks--;}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity entity){}
    @Override public void push(double x,double y,double z){}
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out){super.addAdditionalSaveData(out);out.putInt("myriad_style",style());}
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in){super.readAdditionalSaveData(in);entityData.set(STYLE,in.getIntOr("myriad_style",0));}
}
