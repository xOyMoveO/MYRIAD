package com.myriad.character.ability;

import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;

import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Drawing primitives for the layered frost and botanical effects. */
public final class FrostHerbLayers {
    private FrostHerbLayers() {}

    public static boolean owns(AbilityVfxPayload p) {
        return p.theme() == 6 || p.theme() == 7;
    }

    /** Preserve a short trail for high-speed projectiles. The trail carries no model or flight identity. */
    public static AbilityVfxPayload wakeForFlight(AbilityVfxPayload p) {
        if (!owns(p) || p.kind() != FLIGHT || p.instance() == 0 || p.from().distanceToSqr(p.to()) < 1e-7)
            return null;
        double width = p.variant()==0?.11:p.theme()==6&&p.variant()==5?.24:.16;
        return new AbilityVfxPayload(p.owner(), -1, p.theme(), p.variant(), TRAIL, p.from(), p.to(),
                (float)width, 270, 5, p.started(), p.personal());
    }

    public static void ribbon(AbilityMeshBuilder b, Vec3 a, Vec3 z, Vec3 across, double width,
                              int material, double ticks, double alpha) {
        Vec3 delta = z.subtract(a);
        if (delta.lengthSqr() < 1e-7) return;
        Vec3 axis = delta.normalize();
        Vec3 span = across.subtract(axis.scale(axis.dot(across))).normalize();
        if (span.lengthSqr() < 1e-8) span = side(axis);
        b.frame(a.lerp(z,.5),axis,span,delta.length()*.51,width,material,(int)ticks,alpha);
    }

    public static void rim(AbilityMeshBuilder b, Vec3 at, double radius, int material,
                           double ticks, double alpha) {
        b.frame(at, X, Z, radius, radius, material, (int)ticks, alpha);
    }

    public static void chips(AbilityMeshBuilder b, Vec3 at, double radius, double height, int material,
                             double clock, int count, double alpha) {
        int n = b.reduced ? Math.min(count, 4) : count;
        for (int i = 0; i < n; i++) {
            double angle = i * 2.399963 + clock * .11;
            var point = circle(at, radius * (.42 + (i%4)*.16), angle, height*(.26+(i%3)*.32));
            var across = new Vec3(Math.cos(angle),0,Math.sin(angle));
            b.frame(point,across,UP,.22+radius*.036,.25+height*.09,material,(int)(clock+i*.74),alpha*(1-(i%3)*.13));
        }
    }
}
