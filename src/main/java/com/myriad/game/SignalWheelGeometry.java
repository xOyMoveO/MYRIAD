package com.myriad.game;

/** Screen-independent seven-sector wheel; clockwise from top, with a cancel deadzone. */
public final class SignalWheelGeometry {
    private SignalWheelGeometry(){}
    public static int sector(double dx,double dy){
        return sector(dx,dy,20);
    }
    public static int sector(double dx,double dy,double deadzone){
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(deadzone)||deadzone<0||dx*dx+dy*dy<deadzone*deadzone)return -1;
        double angle=Math.atan2(dy,dx)+Math.PI/2+Math.PI/7;
        return Math.floorMod((int)Math.floor(angle/(Math.PI*2/7)),7);
    }
}
