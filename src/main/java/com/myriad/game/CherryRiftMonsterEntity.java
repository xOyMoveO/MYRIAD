package com.myriad.game;

import com.myriad.registry.ModAttributes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The model and hitbox belong to the combatant itself. MonsterManager owns combat and respawning. */
public final class CherryRiftMonsterEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(
            CherryRiftMonsterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> ATTACK_PROGRESS = SynchedEntityData.defineId(
            CherryRiftMonsterEntity.class, EntityDataSerializers.FLOAT);
    private float lastRenderedAttack = -1F;
    private float renderedAttack = -1F;

    public CherryRiftMonsterEntity(EntityType<? extends CherryRiftMonsterEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.ATTACK_DAMAGE).add(Attributes.FOLLOW_RANGE, 20)
                .add(ModAttributes.PHYSICAL_DEFENSE).add(ModAttributes.MAGIC_RESISTANCE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, MonsterType.CHERRY_FROST_HARE.getId());
        builder.define(ATTACK_PROGRESS, -1F);
    }

    void publishAttackProgress(float progress) {
        entityData.set(ATTACK_PROGRESS, progress);
    }

    /** The server publishes progress for the currently active strike; idle is -1. */
    public float attackAnimationProgress(float partialTick) {
        float current = entityData.get(ATTACK_PROGRESS);
        if (!level().isClientSide() || current < 0F || current != renderedAttack
                || lastRenderedAttack < 0F || lastRenderedAttack > current) {
            return current;
        }
        return Mth.lerp(partialTick, current, Math.min(1F, current + (current - lastRenderedAttack)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            lastRenderedAttack = renderedAttack;
            renderedAttack = entityData.get(ATTACK_PROGRESS);
        }
    }

    public void configure(MonsterType type) {
        entityData.set(VARIANT, type.getId());
    }

    public MonsterType variant() {
        MonsterType type = MonsterType.byId(entityData.get(VARIANT));
        return type != null && type.cherryModel() != null ? type : MonsterType.CHERRY_FROST_HARE;
    }

    // Vanilla calls the other entity's push(Entity) from the ticking entity's doPush.
    // Filter both dispatch directions, leaving explicit knockback and combat hitboxes intact.
    @Override
    public void push(Entity other) {
        if (!(other instanceof Player && variant() == MonsterType.CHERRY_RIFT_CRAB)) super.push(other);
    }

    @Override
    protected void doPush(Entity other) {
        if (!(other instanceof Player && variant() == MonsterType.CHERRY_RIFT_CRAB)) super.doPush(other);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        MonsterType.CherryModel model = variant().cherryModel();
        return EntityDimensions.fixed(model.width(), model.height()).withEyeHeight(model.height() * .75F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (VARIANT.equals(key)) refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("myriad_variant", variant().getId());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(VARIANT, input.getStringOr("myriad_variant", MonsterType.CHERRY_FROST_HARE.getId()));
    }
}
