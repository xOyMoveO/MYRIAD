package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import static com.myriad.network.ShuoViewPayload.*;

/** Actual exported grip/throat coordinates, in camera blocks. No independent blade offsets. */
public final class ShuoRig {
    public static final float WEAPON_LENGTH=1.55f,WEAPON_WIDTH=1.14f,MODEL_GRIP_TO_MOUTH=14f/64,MODEL_GRIP_TO_TIP=73.8f/64;
    public static final float MODEL_SHEATH_LENGTH=63.25f/64;
    public static final float HAND_GRIP_RAISE=.11f,SUPPORT_GRIP_OFFSET=.275f;
    public static final float GRIP_TO_MOUTH=MODEL_GRIP_TO_MOUTH*WEAPON_LENGTH,
        EXTRACT=(MODEL_GRIP_TO_TIP-MODEL_GRIP_TO_MOUTH+.07f)*WEAPON_LENGTH;
    private static final Vector3f READY=v(.46f,-.71f,-1.15f),READY_AXIS=v(.16f,.925f,-.345f).normalize();
    public static final float ARM_LENGTH=1.06f;
    private static final float MAIN_WIDTH=1.00f,SUPPORT_WIDTH=.86f;
    // Grip frame is independent of the user's 180-degree item-facing correction.
    // Rotating the item must not flip the skin hand or alter its grip anchor.
    private static final Vector3f READY_EDGE=v(-.58f,0,-.815f).normalize(),SHEATH_EDGE=v(.20f,0,-.98f).normalize();
    private ShuoRig(){}
    public record Frame(Vector3f palm,Vector3f axis,Vector3f edge,Vector3f mouth,Vector3f sheathAxis,float flash,int action,float slashProgress){}
    public static boolean showSupportHand(Frame f,boolean drawn){
        // During extraction the blade and scabbard still share their longitudinal axis.
        return !drawn || f.action==SHEATHE || f.action!=CANCEL && f.axis.dot(f.sheathAxis)>.98f;
    }
    public static boolean showMainHand(Frame f,boolean drawn){return drawn||switch(f.action){
        case DRAW,BASIC_RISE,BASIC_SWEEP,FLASH,CUT_LEFT,CUT_RIGHT,CUT_DOWN,GUARD,COUNTER -> true;
        default -> false;
    };}
    private static Vector3f v(float x,float y,float z){return new Vector3f(x,y,z);}
    public static float ease(double x){float t=(float)Math.clamp(x,0,1);return t*t*(3-2*t);}
    private static Vector3f mix(Vector3f a,Vector3f b,double t){return new Vector3f(a).lerp(b,ease(t));}
    // Radius fitted to the exported curved edge, in camera blocks at its fixed display scale.
    // Rotation about this centre starts tangent to the throat and feeds the curved tip through it.
    private static final float DRAW_RADIUS = (MODEL_GRIP_TO_TIP-MODEL_GRIP_TO_MOUTH)*WEAPON_LENGTH
            / (float)Math.toRadians(7.5);
    private static final Vector3f DOCK=v(-.245f,-.255f,-1.12f);
    private static final Vector3f SHEATH_PULL=v(-.18f,-.17f,.36f);
    private record Passage(Vector3f palm,Vector3f axis,Vector3f edge) {}
    private static Passage passage(Vector3f mouth,Vector3f sheath,float distance) {
        var across=new Vector3f(SHEATH_EDGE).fma(-SHEATH_EDGE.dot(sheath),sheath).normalize().negate();
        float sin=distance/DRAW_RADIUS, cos=(float)Math.sqrt(1-sin*sin);
        var axis=new Vector3f(sheath).mul(cos).fma(-sin,across);
        var edge=new Vector3f(across).mul(-cos).fma(-sin,sheath);
        var root=new Vector3f(mouth).fma(-distance,sheath).fma(distance*sin/(1+cos),across);
        return new Passage(root.fma(-GRIP_TO_MOUTH,axis),axis,edge);
    }

    static Frame guided(int action,float fraction) {
        var mouth=new Vector3f(DOCK).fma(fraction,SHEATH_PULL);
        var shaft=v(-.11f,-.986f,.12f).normalize();
        var blade=passage(mouth,shaft,EXTRACT*fraction);
        return new Frame(blade.palm,blade.axis,blade.edge,mouth,shaft,0,action,-1);
    }
    static float guidedFraction(Frame frame) {
        return Math.clamp(new Vector3f(frame.mouth).sub(DOCK).dot(SHEATH_PULL)/SHEATH_PULL.lengthSquared(),0,1);
    }

    public static Frame frame(int action,double p,boolean drawn){
        // Mouth at lower-left; the long +Y sheath axis points down, so its lower 2/3 leaves the view.
        var mouth=new Vector3f(DOCK);var sheath=v(-.11f,-.986f,.12f).normalize();
        boolean drawing=!drawn && (action==DRAW || action==FLASH || action==CUT_LEFT ||
                action==CUT_RIGHT || action==CUT_DOWN || action==MOON_FIRST);
        float pull=action==SHEATHE?1-ease((p-.28)/.72):drawing?ease(p/.32):drawn?1:0;
        mouth.fma(pull,SHEATH_PULL);
        var exit=passage(mouth,sheath,EXTRACT);
        if ((drawing && p<=.32) || (action==SHEATHE && p>=.28)) return guided(action,pull);
        var closed=new Vector3f(mouth).fma(-GRIP_TO_MOUTH,sheath);
        var ready=new Vector3f(READY);var readyAxis=new Vector3f(READY_AXIS);
        Vector3f palm=drawn?new Vector3f(ready):new Vector3f(closed),axis=drawn?new Vector3f(readyAxis):new Vector3f(sheath),edge=drawn?new Vector3f(READY_EDGE):new Vector3f(SHEATH_EDGE);
        float flash=0,slashProgress=-1;
        if(action==CHARGE){
            mouth.y+=.035f*ease(p);palm=drawn?mix(ready,v(.28f,-.42f,-1.02f),p):new Vector3f(mouth).fma(-GRIP_TO_MOUTH-.045f*ease(p),sheath);
        }else if(action==SHEATHE){
            // Turn beside the mouth first. Approach the opening only after the tip points down it.
            var beside=new Vector3f(exit.palm).add(.34f,.10f,-.30f);
            palm=p<.19?mix(ready,beside,p/.19):mix(beside,exit.palm,(p-.19)/.09);
            axis=mix(readyAxis,exit.axis,p/.19).normalize();
            edge=mix(READY_EDGE,exit.edge,p/.19);
        }else if(action==GUARD||action==COUNTER){
            float g=ease(p/.18)*(1-ease((p-.82)/.18));
            if(!drawn){
                // Closed blade and sheath remain coaxial while lifted horizontally across the chest.
                mouth=mix(mouth,v(.70f,-.09f,-1.32f),g);
                sheath=mix(sheath,v(-.999f,.035f,.025f).normalize(),g).normalize();
                axis=new Vector3f(sheath);palm=new Vector3f(mouth).fma(-GRIP_TO_MOUTH,sheath);
            }else palm=mix(ready,v(.24f,-.40f,-.98f),g);
            flash=action==COUNTER?g:0;
        }else if(action==BASIC_RISE||action==BASIC_SWEEP){
            // A short anticipation, committed cut, then a separate relaxed recovery.
            // Actual item +X is reversed by blade(), so -edge follows the cutting direction.
            boolean rise=action==BASIC_RISE;
            var begin=rise?v(-.42f,-.62f,-1.42f):v(-.45f,-.22f,-1.48f);
            var end=rise?v(.48f,-.14f,-1.44f):v(.56f,-.34f,-1.48f);
            var beginAxis=rise?v(-.67f,.23f,-.40f).normalize():v(-.62f,.58f,-.38f).normalize();
            var endAxis=rise?v(.45f,.76f,-.36f).normalize():v(.70f,.28f,-.36f).normalize();
            var cutEdge=new Vector3f(begin).sub(end).normalize();
            if(p<.18){palm=mix(ready,begin,p/.18);axis=mix(readyAxis,beginAxis,p/.18).normalize();edge=mix(READY_EDGE,cutEdge,p/.18);}
            else if(p<.60){double t=(p-.18)/.42;palm=mix(begin,end,t);axis=mix(beginAxis,endAxis,t).normalize();edge=new Vector3f(cutEdge);}
            else {
                double t=(p-.60)/.40;
                var coast=rise?v(.55f,-.09f,-1.51f):v(.65f,-.36f,-1.53f);
                var low=v(.46f,-.77f,-1.36f);
                palm=t<.20?mix(end,coast,t/.20):t<.64?mix(coast,low,(t-.20)/.44):mix(low,ready,(t-.64)/.36);
                axis=mix(endAxis,readyAxis,t).normalize();edge=mix(cutEdge,READY_EDGE,t);
            }
            slashProgress=(float)((p-.18)/.52);flash=slashProgress>0&&slashProgress<1?(float)Math.sin(Math.PI*slashProgress):0;
        }else if(moonCut(action)){
            var left=v(-.31f,-.36f,-1.24f);var right=v(.52f,-.35f,-1.22f);
            var leftAxis=v(-.75f,.38f,-.42f).normalize();var rightAxis=v(.75f,.50f,-.45f).normalize();
            boolean finish=action==MOON_THIRD, wide=action==RETURNING_MOON;
            boolean extracting=action==MOON_FIRST&&!drawn;
            var from=extracting?exit.palm:action==MOON_SECOND?left:finish?right:ready;
            var to=finish?v(.16f,-.60f,-1.22f):action==MOON_SECOND?right:wide?v(-.48f,-.50f,-1.30f):left;
            var startAxis=extracting?exit.axis:action==MOON_SECOND?leftAxis:finish?rightAxis:readyAxis;
            var endAxis=finish?v(-.22f,-.62f,-.75f).normalize():action==MOON_SECOND?rightAxis:leftAxis;
            double clear=extracting?.32:0;
            double sweep=ease((p-clear)/(.60-clear));
            palm=mix(from,to,sweep);axis=mix(startAxis,endAxis,sweep).normalize();
            var cutEdge=new Vector3f(from).sub(to).normalize();
            edge=extracting?mix(exit.edge,cutEdge,(p-clear)/.14):cutEdge;
            flash=p<.65?(float)Math.sin(Math.PI*(p-clear)/(.65-clear)):0;
        }else if(action==PURSUIT){
            palm=mix(ready,v(.44f,-.62f,-1.12f),.8);axis=v(.36f,.78f,-.50f).normalize();
        }else if(action==DRAW||action==FLASH||action==CUT_LEFT||action==CUT_RIGHT||action==CUT_DOWN){
            double extract=drawn?0:.32;
            double t=(p-extract)/(1-extract);boolean reverse=action==CUT_RIGHT;
            var launch=drawn?new Vector3f(ready):exit.palm;
            var finish=action==CUT_DOWN?v(-.12f,-.64f,-1.06f):v(reverse?.46f:-.34f,-.50f,-1.14f);
            var bladeEnd=action==CUT_DOWN?v(-.15f,-.65f,-.75f):v(reverse?.70f:-.70f,-.30f,-.65f);
            if(t<.52){
                palm=mix(launch,finish,t/.52);axis=mix(drawn?readyAxis:exit.axis,bladeEnd,t/.52).normalize();
                edge=mix(drawn?READY_EDGE:exit.edge,new Vector3f(finish).sub(launch).normalize(),t/.22);
            }else {
                double returning=(t-.52)/.48;
                if (action==DRAW) {
                    var coast=v(-.43f,-.59f,-1.26f);
                    var low=v(.09f,-.78f,-1.31f);
                    palm=returning<.22?mix(finish,coast,returning/.22):returning<.67?
                            mix(coast,low,(returning-.22)/.45):mix(low,ready,(returning-.67)/.33);
                } else palm=mix(finish,ready,returning);
                axis=mix(bladeEnd,readyAxis,(t-.52)/.48).normalize();
                edge=mix(new Vector3f(finish).sub(launch).normalize(),READY_EDGE,(t-.52)/.48);}
            flash=t<.66?(float)Math.sin(Math.PI*Math.clamp(t/.66,0,1)):0;
            if(action==DRAW)slashProgress=(float)Math.clamp(t/.66,0,1);
        }else if(action==RETREAT){palm.add(0,-.06f*(float)Math.sin(Math.PI*p),.04f);}
        return new Frame(palm,axis,edge,mouth,sheath,Math.max(0,flash),action,slashProgress);
    }
    public static Matrix4f basis(Vector3f origin,Vector3f axis,Vector3f edge,boolean mirrored){
        var y=new Vector3f(axis).normalize();var x=new Vector3f(edge).fma(-edge.dot(y),y);
        if(x.lengthSquared()<1E-6)x.set(0,1,0).fma(-y.y,y);
        x.normalize();var z=new Vector3f(x).cross(y).normalize();
        // Reflection of position and two basis axes preserves positive handedness and culling.
        // Reverse thickness Z rather than X to preserve the mirrored grip frame.
        var p=new Vector3f(origin);if(mirrored){p.x=-p.x;y.x=-y.x;x.x=-x.x;z.set(z.x,-z.y,-z.z);}
        return new Matrix4f().setColumn(0,new Vector4f(x,0)).setColumn(1,new Vector4f(y,0))
                .setColumn(2,new Vector4f(z,0)).setColumn(3,new Vector4f(p,1));
    }
    // Turn both fitted items about their own long axis: shaft and anchors stay
    // fixed, and the curved blade still slides into the matching hollow sheath.
    static float occupiedOffset(Frame f,boolean mirror) {
        float docked=ease((f.axis().dot(f.sheathAxis())-.25f)/.72f);
        return (mirror?-1:1)*-2*f.mouth().x*docked;
    }
    public static Matrix4f blade(Frame f,boolean mirror){return basis(f.palm,f.axis,f.edge,mirror).rotateY((float)Math.PI).scale(WEAPON_WIDTH,WEAPON_LENGTH,WEAPON_WIDTH);}
    public static Matrix4f sheath(Frame f,boolean mirror){return basis(f.mouth,f.sheathAxis,SHEATH_EDGE,mirror).rotateY((float)Math.PI).scale(WEAPON_WIDTH,WEAPON_LENGTH,WEAPON_WIDTH);}
    public static Matrix4f occupiedHand(Frame f,boolean mirror){
        var normal=hand(f,false,mirror);
        var seated=new Vector3f(f.mouth).fma(-GRIP_TO_MOUTH,f.sheathAxis);
        float weight=(1-ease((f.palm.distance(seated)-EXTRACT)/.45f))*ease((f.axis.dot(f.sheathAxis)-.70f)/.30f);
        if(weight<=0)return normal;
        // While the hilt is docked the arm reaches in from the lower outer side.
        // Rotate about the real palm so drawing the blade can release this wrist pose continuously.
        var palm=normal.getTranslation(new Vector3f());
        var closed=basis(new Vector3f(),v(-.62f,.78f,-.06f),v(.78f,.62f,0),mirror);
        var rotation=normal.getUnnormalizedRotation(new Quaternionf()).slerp(closed.getNormalizedRotation(new Quaternionf()),weight);
        return new Matrix4f().translation(palm).rotate(rotation).scale(MAIN_WIDTH,ARM_LENGTH,MAIN_WIDTH);
    }
    public static Matrix4f hand(Frame f,boolean off,boolean mirror){
        // Item-only scale: never stretch or bloat the player's fist/skin with the weapon.
        if(off)return basis(new Vector3f(f.mouth).fma(SUPPORT_GRIP_OFFSET,f.sheathAxis),v(.04f,.66f,-.75f),v(.11f,.986f,-.12f),mirror).scale(SUPPORT_WIDTH,ARM_LENGTH,SUPPORT_WIDTH);
            // Lift the grasp towards the guard. Skin +Y is wrist-to-fingers,
            // not the blade shaft: permute the grip axes so the forearm reaches
            // forwards from the camera, with a small natural upward wrist tilt.
            // All rotations are about the actual raised palm, never the shoulder.
        var hand=basis(f.palm,f.axis,f.edge,mirror).translate(0,HAND_GRIP_RAISE,0)
                .rotateZ(-(float)Math.PI/2).rotateY(-(float)Math.PI/2).rotateX(.22f);
        float atRest=ease(1-f.palm.distance(READY)/.28f)*ease((f.axis.dot(READY_AXIS)-.7f)/.3f);
        if(atRest>0){
            var resting=basis(new Vector3f(),v(-.32f,.60f,-.73f),READY_AXIS,mirror);
            var rotation=hand.getNormalizedRotation(new Quaternionf()).slerp(resting.getNormalizedRotation(new Quaternionf()),atRest);
            hand.translationRotate(hand.getTranslation(new Vector3f()),rotation);
        }
        return hand.scale(MAIN_WIDTH,ARM_LENGTH,MAIN_WIDTH);
    }
}
