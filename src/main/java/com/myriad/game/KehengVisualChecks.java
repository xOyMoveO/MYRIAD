package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.keheng.KehengVfx;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Production-mesh spatial and temporal contracts, independent of client graphics classes. */
final class KehengVisualChecks {
    private KehengVisualChecks(){}
    private static AbilityVfxPayload p(int variant,int kind,Vec3 from,Vec3 to,float radius,float stage,int life,long started,int instance){
        return new AbilityVfxPayload(1,-1,11,variant,kind,from,to,radius,stage,life,started,false,instance);
    }
    public static List<AbilityVfxPayload> samples(){
        var out=new ArrayList<AbilityVfxPayload>();
        out.add(p(0,SEAL,Vec3.ZERO,new Vec3(0,0,1),.38f,1,5,100,0));
        out.add(p(0,BURST,new Vec3(0,.9,0),new Vec3(0,.9,1),.45f,3,5,100,0));
        out.add(p(1,SHIELD,Vec3.ZERO,new Vec3(0,1,0),.65f,360,8,100,0));
        out.add(p(2,FLIGHT,Vec3.ZERO,new Vec3(1,.4,.6),.22f,0,3,100,71));
        out.add(p(2,BURST,Vec3.ZERO,new Vec3(1,.4,.6),.55f,0,5,100,0));
        out.add(p(3,TRAIL,new Vec3(-1,.4,0),new Vec3(1.3,.4,.7),.18f,0,7,100,0));
        out.add(p(3,SEAL,Vec3.ZERO,new Vec3(0,1,0),2,0,5,100,72));
        out.add(p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,300,5,100,73));
        out.add(p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,18,5,118,73));
        out.add(p(5,SEAL,Vec3.ZERO,new Vec3(0,1,0),3,18,5,118,74));
        out.add(p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.NATURAL_END,6,150,0));
        out.add(p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.INTERRUPTED_END,4,150,0));
        out.add(p(6,SEAL,Vec3.ZERO,new Vec3(0,1,0),4,18,5,118,75));
        out.add(p(6,ARC,Vec3.ZERO,new Vec3(0,1,0),4,0,5,130,0));
        out.add(p(7,CHARGE,Vec3.ZERO,new Vec3(0,0,12),1.5f,0,5,100,76));
        out.add(p(7,BEAM,new Vec3(0,1.3,0),new Vec3(0,1.3,12),1.5f,0,6,116,0));
        out.add(p(2,BEAM,Vec3.ZERO,new Vec3(0,0,.8),.03f,KehengVfx.BASIC,3,100,0));
        out.add(p(2,BURST,Vec3.ZERO,new Vec3(0,0,1),.3f,KehengVfx.BASIC,3,100,0));
        return out;
    }
    static void verify(GameTestHelper h){
        int count=0;
        for(var recipe:samples())for(float age:new float[]{0,.75f,2}){
            var mesh=AbilityVfxGeometry.build(recipe,age,false);var low=AbilityVfxGeometry.build(recipe,age,true);
            if(recipe.kind()==FLIGHT&&age>=1){h.assertTrue(mesh.isEmpty()&&low.isEmpty(),"No physical needle is left parked on an old segment endpoint");continue;}
            h.assertTrue(!mesh.isEmpty()&&mesh.size()<=AbilityVfxGeometry.maxQuads(recipe)&&low.size()<=mesh.size(),"Keheng production stages retain finite normal/reduced meshes");
            h.assertTrue(mesh.stream().anyMatch(q->q.material()>=KEHENG_HD_RING),"Every stage samples authored textures or actual model faces");
            for(var q:mesh){
                h.assertTrue(q.alpha()>0&&q.alpha()<=1,"Keheng alpha remains bounded");
                h.assertTrue(q.startU()>=0&&q.startU()<q.endU()&&q.endU()<=1&&q.startV()>=0&&q.startV()<q.endV()&&q.endV()<=1,"Real texture UVs stay within their atlas sprite");
                h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()>1E-14,"Actual model and effect faces have positive area");
                for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()})h.assertTrue(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&KehengVfx.bounds(recipe).inflate(.01).contains(v),"Keheng world bounds contain actual mesh vertices");
            }
            h.assertTrue(AbilityVfxGeometry.build(recipe,recipe.life(),false).isEmpty(),"Keheng actual expiry leaves no geometry");count++;
        }
        var nodes=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,300,5,100,70);
        var arcs=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,306,5,106,70);
        h.assertTrue(AbilityVfxGeometry.build(arcs,0,false).size()>AbilityVfxGeometry.build(nodes,0,false).size(),"Boundary starts as four nodes before connecting arcs");
        var active=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,12,5,112,70);
        var refresh=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,15,5,115,70);
        h.assertTrue(AbilityVfxGeometry.build(active,3,false).equals(AbilityVfxGeometry.build(refresh,0,false)),"Fresh TTL does not restart field phase at the same world time");
        h.assertTrue(!AbilityVfxGeometry.build(active,0,false).equals(AbilityVfxGeometry.build(active,2,false)),"Production field layers really move between frames");
        for(int variant:new int[]{3,4,5,6}){
            float radius=variant==6?4:variant==4?3.5f:variant==5?3:2;
            var field=p(variant,SEAL,Vec3.ZERO,new Vec3(0,1,0),radius,18,5,118,70);
            var mesh=AbilityVfxGeometry.build(field,1,false);
            double maxRadius=0;int ringFaces=0;
            for(var q:mesh){
                if(q.material()==KEHENG_HD_RING){
                    ringFaces++;double support=visibleRadius(q,image(q.material()));maxRadius=Math.max(maxRadius,support);
                    h.assertTrue(support<=radius+1E-5,"Visible boundary alpha, not transparent quad corners, stays inside the actual radius");
                }
            }
            h.assertTrue(ringFaces==2&&maxRadius>=radius-.05,"Authored outer boundary visibly reaches actual 2/3/3.5/4 block extent");
            h.assertTrue(mesh.stream().map(q->q.material()).distinct().count()>=3,"Active fields have independent boundary, time-slice detail and pixel fragments");
        }
        for(var direction:List.of(new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(1,.6,-.7).normalize(),new Vec3(0,0,1))){
            var shot=p(2,FLIGHT,Vec3.ZERO,direction.scale(1.2),.22f,0,3,100,9);var mesh=AbilityVfxGeometry.build(shot,.8f,false);
            double foremost=-100,rearmost=100;
            for(var q:mesh)for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()}){double along=v.dot(direction);foremost=Math.max(foremost,along);rearmost=Math.min(rearmost,along);}
            h.assertTrue(Math.abs(foremost-.96)<1E-6&&rearmost<.1,"The actual model tip leads horizontal/vertical/diagonal motion without passing the interpolated endpoint");
            h.assertTrue(mesh.stream().filter(q->q.material()==KEHENG_PROJECTILE_MODEL).count()==KehengVfx.modelFaces(false),"Production flight contains every real source model face once");
            for(var q:AbilityVfxGeometry.build(shot,.8f,true))for(var v:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(v.dot(direction)<=.96+1E-6,"LOD and adornment stay behind the confirmed forward tip");
        }
        var penetration=p(7,BEAM,Vec3.ZERO,new Vec3(0,0,12),1.5f,0,6,116,0);
        var penetrationMesh=AbilityVfxGeometry.build(penetration,.7f,false);
        boolean left=false,right=false;
        for(var q:penetrationMesh){
            double centerX=(q.a().x+q.b().x+q.c().x+q.d().x)*.25;
            if(q.material()==KEHENG_HD_VEIL){left|=centerX<-.1;right|=centerX>.1;}
            if(q.material()==KEHENG_PROJECTILE_MODEL)for(var v:List.of(q.a(),q.b(),q.c(),q.d()))
                h.assertTrue(v.z>=-1E-6&&v.z<=12+1E-6&&Math.hypot(v.x,v.y)<.06,"Penetration stretches only the physical needle axis inside the confirmed corridor, never into a white panel");
        }
        h.assertTrue(left&&right,"Actual textured penetration cut planes occupy both sides of the needle");
        var natural=p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.NATURAL_END,6,150,0);
        var interrupted=p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.INTERRUPTED_END,4,150,0);
        h.assertTrue(!AbilityVfxGeometry.build(natural,1,false).equals(AbilityVfxGeometry.build(interrupted,1,false)),"Natural gathering needle and interrupted scatter are distinct");
        var other=p(4,SEAL,new Vec3(1,0,0),new Vec3(1,1,0),3.5f,15,5,115,71);
        h.assertTrue(KehengVfx.replaces(refresh,active)&&!KehengVfx.replaces(other,active)&&KehengVfx.obsolete(active,refresh),"One instance refreshes; two legal same-skill fields coexist; old snapshots cannot replace new ones");
        var lifetime=new AbilityVisualLifetime();
        var ended=p(4,CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,116,70);lifetime.end(ended,116);
        h.assertTrue(lifetime.finished(refresh,116)&&!lifetime.finished(other,116),"Field end suppresses only its queued visual instance");
        var wake=KehengVfx.wakeForFlight(p(2,FLIGHT,Vec3.ZERO,new Vec3(1,.2,0),.2f,0,3,100,8));
        h.assertTrue(wake!=null&&wake.instance()==0&&wake.kind()==TRAIL&&wake.life()<=5&&AbilityVisualLifetime.expired(wake,105),"Needle wake follows real sampled endpoints and retires within 0.25 seconds");
        textureChecks(h);
        h.assertTrue(KehengVfx.modelParts(false)>KehengVfx.modelParts(true)&&KehengVfx.modelParts(true)>=4&&KehengVfx.modelFaces(false)>=KehengVfx.modelParts(false)*4,"Normal/LOD contain separately authored physical parts and face sets");
        System.out.println("[MYRIAD-KEHENG-VISUAL] PASS: "+count+" real textured stage frames, eight candidates/basic, physical needle faces/UV/LOD, alpha-correct exact boundaries, continuous phase and finite lifecycle");
    }
    private static final Map<Integer,BufferedImage> IMAGES=new HashMap<>();
    private static BufferedImage image(int material){return IMAGES.computeIfAbsent(material,m->{
        String[] names={"ring","arc","stream","impact","veil","shield","motes","spark"};
        String name=m==KEHENG_PROJECTILE_MODEL?"keheng_projectiles_atlas":"keheng_hd_"+names[m-KEHENG_HD_RING];
        try(var in=KehengVisualChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/"+name+".png")){
            if(in==null)throw new IllegalStateException("Missing authored Keheng atlas "+name);return ImageIO.read(in);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    });}
    private static void textureChecks(GameTestHelper h){
        for(int m=KEHENG_HD_RING;m<=KEHENG_HD_SPARK;m++){
            var im=image(m);int size=m>=KEHENG_HD_MOTES?64:256;h.assertTrue(im.getWidth()==size&&im.getHeight()==size,"HD bodies and pixel accents retain their distinct real resolutions");
            int clear=0,visible=0,soft=0;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){int alpha=im.getRGB(x,y)>>>24;if(alpha==0)clear++;else{visible++;if(alpha<255)soft++;}
                if(x==0||y==0||x==size-1||y==size-1)h.assertTrue(alpha==0,"No opaque rectangular sprite background");}
            h.assertTrue(clear>0&&visible>16&&soft>0,"Each actual sprite has real transparent gaps, content, and smooth alpha");
        }
        var ring=image(KEHENG_HD_RING);h.assertTrue((ring.getRGB(128,128)>>>24)==0,"The ground ring keeps its real transparent center");
        var model=image(KEHENG_PROJECTILE_MODEL);h.assertTrue(model.getWidth()==128&&model.getHeight()==128,"Physical needle uses its embedded 128px atlas");
    }
    /** Sample every nonzero-alpha texel corner through the actual production UV and plane map. */
    private static double visibleRadius(AbilityVfxGeometry.Quad q,BufferedImage image){
        double maximum=0;int width=image.getWidth(),height=image.getHeight();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((image.getRGB(x,y)>>>24)>0){
            for(int dy=0;dy<=1;dy++)for(int dx=0;dx<=1;dx++){
                double u=((x+dx)/(double)width-q.startU())/(q.endU()-q.startU());
                double v=((y+dy)/(double)height-q.startV())/(q.endV()-q.startV());if(q.reverseV())v=1-v;
                if(u<0||u>1||v<0||v>1)continue;
                var point=q.a().scale((1-u)*v).add(q.b().scale(u*v)).add(q.c().scale(u*(1-v))).add(q.d().scale((1-u)*(1-v)));
                maximum=Math.max(maximum,Math.hypot(point.x,point.z));
            }
        }return maximum;
    }

}
