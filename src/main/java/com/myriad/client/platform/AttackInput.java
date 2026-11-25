package com.myriad.client.platform;

/** One native Minecraft attack attempt, shared by authority gating and local weapon prediction. */
public final class AttackInput {
    private boolean canceled;
    private boolean swingHand = true;
    private boolean meleeAssist;
    public boolean isAttack() { return true; }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
    public boolean shouldSwingHand() { return swingHand; }
    public void setSwingHand(boolean swingHand) { this.swingHand = swingHand; }
    public boolean isMeleeAssist() { return meleeAssist; }
    public void setMeleeAssist(boolean meleeAssist) { this.meleeAssist = meleeAssist; }
}
