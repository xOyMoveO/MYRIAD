package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.character.ability.RosterProjectileModels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

public final class LuoxiaGeometry {
    private static final int WHITE = 0xFFF2CB, GOLD = 0xFFC65D, CORAL = 0xFF765B, VIOLET = 0x624A83;
    private static final RosterProjectileModels ARROWS = RosterProjectileModels.load("luoxia_arrows.json");
    private LuoxiaGeometry() {}

    private static boolean ultimate(AbilityMeshBuilder g, AbilityVfxPayload p, Vec3 f, Vec3 side, Vec3 up) {
        boolean fan = p.variant() == 6;
        Vec3 from = p.from(), to = p.to();
        double phase = g.progress;
        int frame = Math.min(3, (int)(phase * 4));
        switch (p.kind()) {
            case BEAM -> {
                // A single locked 3D ray; both narrow planes lie along the same clipped segment.
                double length = from.distanceTo(to);
                if (length < .001) break;
                var mid = from.lerp(to, .5);
                g.line(from, to, side, .024, CORAL, .70);
                g.line(from, to, up, .018, GOLD, .64);
                if (!g.reduced) {
                    g.frame(mid, f, side, length / 2, .042, LANCE_WARNING, frame, .58);
                    g.frame(mid, f, up, length / 2, .042, LANCE_WARNING, frame, .58);
                }
            }
            case CHARGE -> {
                boolean winding = p.angle() == 1;
                int material = winding ? (fan ? VOLLEY_CHARGE : LANCE_CHARGE) : (fan ? VOLLEY_CROWN : LANCE_RELEASE);
                double length = winding ? .23 : fan ? .57 : .74;
                double width = winding ? .13 : fan ? (p.angle() == 5 ? .16 : .22) : .10;
                if (winding && g.reduced) break;
                var mid = winding ? from : from.add(f.scale(length));
                g.frame(mid, f, side, length, width, material, frame, winding ? .52 : .88);
                g.frame(mid, f, up, length, width * .68, material, frame, winding ? .38 : .67);
                if (!winding) g.frame(mid, side, up, width * .48, width * .32, material, frame, .46);
            }
            case TRAIL -> {
                if (g.reduced) return false; // use the existing short four-frame arrow wake
                var tail = from.lerp(to, phase * .63);
                double length = tail.distanceTo(to);
                var mid = tail.lerp(to, .5);
                int material = fan ? VOLLEY_TRAIL : LANCE_TRAIL;
                double width = fan ? .086 : .125;
                if (fan && p.angle() == 3) g.line(tail, to, up, .027, WHITE, .88);
                g.frame(mid, f, up, length / 2, width, material, frame, .85);
                g.frame(mid, f, side, length / 2, width * .85, material, frame, .66);
            }
            case BURST -> {
                boolean graze = p.angle() == 1;
                int material = graze ? LANCE_GRAZE : fan ? VOLLEY_HIT : LANCE_HIT;
                double size = graze ? .15 : fan ? .26 : .33;
                g.frame(from, side, up, size, size, material, frame, graze ? .54 : .93);
                if (!g.reduced) g.frame(from, f, up, size * .8, size * .8, material, frame, graze ? .36 : .65);
            }
            default -> { return false; }
        }
        return true;
    }

    /** Bow-local mesh, nock at zero and tip along -X. Uses the same authored mesh as flight. */
    public static List<Quad> nockedArrow(String model) {
        var out = new ArrayList<Quad>(ARROWS.faces(model));
        Vec3 forward = new Vec3(-1, 0, 0);
        ARROWS.append(out, model, forward, forward, 1, LUOXIA_ARROW_MODEL, 1);
        return List.copyOf(out);
    }

    public static List<Quad> build(AbilityVfxPayload p, float age, boolean reduced) {
        Vec3 at = p.from(), to = p.to(), forward = direction(at, to);
        if (p.kind() == FLIGHT) {
            var arrow = LuoxiaArrow.Shape.fromWire((int)p.angle());
            var faces = new ArrayList<Quad>(ARROWS.faces(arrow.model));
            var tip = at.lerp(to, Math.clamp(age, 0, 1));
            ARROWS.append(faces, arrow.model, tip, forward, arrow.length, LUOXIA_ARROW_MODEL, 1);
            return faces;
        }
        var g = new AbilityMeshBuilder(p, age, reduced);
        Vec3 right = side(forward), up = right.cross(forward);
        double r = p.radius(), phase = g.progress;
        if ((p.variant() == 6 || p.variant() == 7) && ultimate(g, p, forward, right, up)) return g.result();
        switch (p.kind()) {
            case BEAM -> {
                g.line(at, to, up, .052, VIOLET, .85);
                g.line(at, to, right, .030, CORAL, .95);
                g.line(at, to, up, .014, WHITE, 1);
                if (!reduced) for (double x = 2; x < at.distanceTo(to); x += 2) {
                    var point = at.add(forward.scale(x));
                    g.line(point.subtract(right.scale(.064)), point.add(right.scale(.064)), up, .018, GOLD, .85);
                }
            }
            case TRAIL -> {
                boolean step = p.angle() == 2;
                var from = at.lerp(to, phase * .55);
                double length = from.distanceTo(to);
                if (length > .001) {
                    int frame = Math.min(3, (int)(phase*4));
                    g.frame(from.lerp(to,.5), forward, step ? right : up, length*.63, r*2.4,
                            LUOXIA_WAKE, frame, .92*(1-phase*.55));
                    if (!step && !reduced) g.frame(from.lerp(to,.5), forward, right, length*.6, r*1.8,
                            LUOXIA_WAKE, frame, .55*(1-phase));
                }
                g.line(from, to, step ? UP : right, r * 2, VIOLET, .28 * (1-phase));
                g.line(from, to, UP, r, step ? GOLD : CORAL, .70 * (1-phase));
                g.line(from.lerp(to, .45), to, up, r * .34, WHITE, .9 * (1-phase));
                if (!reduced) for (int n = 0; n < 4; n++) {
                    var s = from.lerp(to, (n + .5) / 4).add(right.scale((n % 2 == 0 ? 1 : -1)*r*(.5+phase)));
                    g.line(s, s.add(forward.scale(r*(1.6+phase))), up, r*.32, n % 2 == 0 ? GOLD : CORAL, .8*(1-phase));
                }
            }
            case BURST -> {
                int frame = Math.min(3, (int)(phase*4));
                g.frame(at, right, up, r*(.8+phase*.45), r*(.8+phase*.45), LUOXIA_IMPACT, frame, .98);
                if (!reduced) g.frame(at, forward, up, r*.72, r*.72, LUOXIA_IMPACT, frame, .63);
                for (int i = 0; i < (reduced ? 5 : 9); i++) {
                    double angle = i * Math.PI * 2 / (reduced ? 5 : 9);
                    Vec3 ray = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                    var start = at.add(ray.scale(r*(.15+phase*.62)));
                    var end = start.add(ray.scale(r*(.65-.4*phase))).add(forward.scale(.12));
                    g.line(start, end, forward, .044*(1-phase), i%3==0 ? WHITE : i%3==1 ? GOLD : CORAL, .97*(1-phase));
                }
            }
            case SHIELD -> {
                for (int sign : new int[]{-1, 1}) {
                    Vec3 shift = right.scale(sign * r * .65);
                    Vec3 spine = at.add(shift).add(0,.03,0);
                    Vec3 tip = spine.add(right.scale(sign*r*.9)).add(0,r*.93,0);
                    g.triangle(tip, spine.add(0,r*.34,0), spine.subtract(right.scale(sign*r*.27)), GOLD, .92);
                    g.line(spine, tip, forward, .035, WHITE, 1);
                    g.line(spine.add(0,-.08,0), tip.add(0,-.09,0), forward, .045, VIOLET, .55);
                }
            }
            case CHARGE -> {
                int count = reduced ? 4 : 8;
                for (int n = 0; n < count; n++) {
                    Vec3 radial = right.scale(Math.cos(n * 2*Math.PI/count)).add(up.scale(Math.sin(n * 2*Math.PI/count)));
                    double span = (p.angle() == 1 ? 1-phase*.55 : .25+phase*.88) * r;
                    var start = at.add(radial.scale(span*.40));
                    g.line(start, at.add(radial.scale(span)).subtract(forward.scale(phase*.1)),
                            forward, .032, n%2==0 ? WHITE : CORAL, .88);
                }
            }
            default -> {}
        }
        return g.result();
    }
}
