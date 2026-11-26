package com.myriad.client.firstperson;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import static com.myriad.network.ShuoViewPayload.*;

/** Actual exported grip/throat coordinates, in camera blocks. No independent blade offsets. */
public final class ShuoRig {
    public static final float WEAPON_LENGTH=1.55f,WEAPON_WIDTH=1.14f,MODEL_GRIP_TO_MOUTH=14f/64,MODEL_GRIP_TO_TIP=73.8f/64;
    public static final float MODEL_SHEATH_LENGTH=63.25f/64;
    public static final float HAND_GRIP_RAISE=.11f;
    public static final float GRIP_TO_MOUTH=MODEL_GRIP_TO_MOUTH*WEAPON_LENGTH,
        EXTRACT=(MODEL_GRIP_TO_TIP-MODEL_GRIP_TO_MOUTH+.07f)*WEAPON_LENGTH;
    public static final boolean SHOW_OFF_HAND=false;
    private static final Vector3f READY=v(.74f,-.61f,-1.32f),READY_AXIS=v(.18f,.982f,-.055f).normalize();
    // Grip frame is independent of the user's 180-degree item-facing correction.
    // Rotating the item must not flip the skin hand or alter its grip anchor.
    private static final Vector3f READY_EDGE=v(-.24f,0,-.97f).normalize(),SHEATH_EDGE=v(.20f,0,-.98f).normalize();
    private ShuoRig(){}
    public record Frame(Vector3f palm,Vector3f axis,Vector3f edge,Vector3f mouth,Vector3f sheathAxis,float flash,int action,float slashProgress){}
    public static boolean showMainHand(Frame f,boolean drawn){return drawn||switch(f.action){
        case DRAW,BASIC_RISE,BASIC_SWEEP,FLASH,CUT_LEFT,CUT_RIGHT,CUT_DOWN,GUARD,COUNTER -> true;
        default -> false;
    };}
    private static Vector3f v(float x,float y,float z){return new Vector3f(x,y,z);}
    public static float ease(double x){float t=(float)Math.clamp(x,0,1);return t*t*(3-2*t);}
    private static Vector3f mix(Vector3f a,Vector3f b,double t){return new Vector3f(a).lerp(b,ease(t));}
    public static Frame frame(int action,double p,boolean drawn){
        // Mouth at lower-left; the long +Y sheath axis points down, so its lower 2/3 leaves the view.
        var mouth=v(-.64f,-.35f,-1.09f);var sheath=v(-.22f,-.974f,-.045f).normalize();
        var closed=new Vector3f(mouth).fma(-GRIP_TO_MOUTH,sheath);
        var ready=new Vector3f(READY);var readyAxis=new Vector3f(READY_AXIS);
        Vector3f palm=drawn?new Vector3f(ready):new Vector3f(closed),axis=drawn?new Vector3f(readyAxis):new Vector3f(sheath),edge=drawn?new Vector3f(READY_EDGE):new Vector3f(SHEATH_EDGE);
        float flash=0,slashProgress=-1;
        if(action==CHARGE){
            mouth.y+=.035f*ease(p);palm=drawn?mix(ready,v(.28f,-.42f,-1.02f),p):new Vector3f(mouth).fma(-GRIP_TO_MOUTH-.045f*ease(p),sheath);
        }else if(action==SHEATHE){
            var extended=new Vector3f(closed).fma(-EXTRACT,sheath);
            palm=p<.28?mix(ready,extended,p/.28):mix(extended,closed,(p-.28)/.72);
            axis=p<.28?mix(readyAxis,sheath,p/.28).normalize():new Vector3f(sheath);
            edge=mix(READY_EDGE,SHEATH_EDGE,p/.28);
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
            else {double t=(p-.60)/.40;palm=mix(end,ready,t);axis=mix(endAxis,readyAxis,t).normalize();edge=mix(cutEdge,READY_EDGE,t);}
            slashProgress=(float)((p-.18)/.52);flash=slashProgress>0&&slashProgress<1?(float)Math.sin(Math.PI*slashProgress):0;
        }else if(moonCut(action)){
            var left=v(-.31f,-.36f,-1.24f);var right=v(.52f,-.35f,-1.22f);
            var leftAxis=v(-.75f,.38f,-.42f).normalize();var rightAxis=v(.75f,.50f,-.45f).normalize();
            boolean finish=action==MOON_THIRD, wide=action==RETURNING_MOON;
            var from=action==MOON_FIRST&&!drawn?closed:action==MOON_SECOND?left:finish?right:ready;
            var to=finish?v(.16f,-.60f,-1.22f):action==MOON_SECOND?right:wide?v(-.48f,-.50f,-1.30f):left;
            var startAxis=action==MOON_FIRST&&!drawn?sheath:action==MOON_SECOND?leftAxis:finish?rightAxis:readyAxis;
            var endAxis=finish?v(-.22f,-.62f,-.75f).normalize():action==MOON_SECOND?rightAxis:leftAxis;
            double sweep=ease(p/.60);
            palm=mix(from,to,sweep);axis=mix(startAxis,endAxis,sweep).normalize();
            edge=new Vector3f(from).sub(to).normalize();
            flash=p<.65?(float)Math.sin(Math.PI*p/.65):0;
        }else if(action==PURSUIT){
            palm=mix(ready,v(.44f,-.62f,-1.12f),.8);axis=v(.36f,.78f,-.50f).normalize();
        }else if(action==DRAW||action==FLASH||action==CUT_LEFT||action==CUT_RIGHT||action==CUT_DOWN){
            double extract=drawn?0:.32;
            if(p<extract){palm=new Vector3f(closed).fma(-EXTRACT*ease(p/extract),sheath);axis=new Vector3f(sheath);}
            else {
                double t=(p-extract)/(1-extract);boolean reverse=action==CUT_RIGHT;
                var launch=drawn?new Vector3f(ready):new Vector3f(closed).fma(-EXTRACT,sheath);
                var finish=action==CUT_DOWN?v(-.12f,-.64f,-1.06f):v(reverse?.46f:-.34f,-.50f,-1.14f);
                var bladeEnd=action==CUT_DOWN?v(-.15f,-.65f,-.75f):v(reverse?.70f:-.70f,-.30f,-.65f);
                if(t<.52){
                    palm=mix(launch,finish,t/.52);axis=mix(drawn?readyAxis:sheath,bladeEnd,t/.52).normalize();
                    edge=new Vector3f(finish).sub(launch).normalize();
                }else {palm=mix(finish,ready,(t-.52)/.48);axis=mix(bladeEnd,readyAxis,(t-.52)/.48).normalize();
                    edge=mix(new Vector3f(finish).sub(launch).normalize(),READY_EDGE,(t-.52)/.48);}
                flash=t<.66?(float)Math.sin(Math.PI*Math.clamp(t/.66,0,1)):0;
                if(action==DRAW)slashProgress=(float)Math.clamp(t/.66,0,1);
            }
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
    public static Matrix4f blade(Frame f,boolean mirror){return basis(f.palm,f.axis,f.edge,mirror).rotateY((float)Math.PI).scale(WEAPON_WIDTH,WEAPON_LENGTH,WEAPON_WIDTH);}
    public static Matrix4f sheath(Frame f,boolean mirror){return basis(f.mouth,f.sheathAxis,SHEATH_EDGE,mirror).rotateY((float)Math.PI).scale(WEAPON_WIDTH,WEAPON_LENGTH,WEAPON_WIDTH);}
    public static Matrix4f hand(Frame f,boolean off,boolean mirror){
        // Item-only scale: never stretch or bloat the player's fist/skin with the weapon.
        return off?basis(f.mouth,f.sheathAxis,SHEATH_EDGE,mirror).translate(0,.12f,0).scale(.74f)
            // Lift the grasp towards the guard. Skin +Y is wrist-to-fingers,
            // not the blade shaft: permute the grip axes so the forearm reaches
            // forwards from the camera, with a small natural upward wrist tilt.
            // All rotations are about the actual raised palm, never the shoulder.
            :basis(f.palm,f.axis,f.edge,mirror).translate(0,HAND_GRIP_RAISE,0)
                .rotateZ(-(float)Math.PI/2).rotateY(-(float)Math.PI/2).rotateX(.22f).scale(.74f);
    }
}
