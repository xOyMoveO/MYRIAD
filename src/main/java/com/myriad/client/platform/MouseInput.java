package com.myriad.client.platform;

/** A native mouse press before gameplay dispatch. */
public final class MouseInput {
    private final int button, action;
    private boolean canceled;
    public MouseInput(int button, int action) { this.button=button; this.action=action; }
    public int getButton() { return button; }
    public int getAction() { return action; }
    public void setCanceled(boolean canceled) { this.canceled=canceled; }
    public boolean isCanceled() { return canceled; }
}
