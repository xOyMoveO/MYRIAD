package com.myriad.client.platform;

/** Mutable view angles applied by the camera mixin before third-person positioning. */
public final class CameraAngles {
    private float yaw, pitch;
    public CameraAngles(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
}
