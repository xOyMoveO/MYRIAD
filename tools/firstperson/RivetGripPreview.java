package com.myriad.tools;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.character.skills.rivet.RivetWeaponMotion;
import com.myriad.client.firstperson.NightDaggerRig;
import com.myriad.client.firstperson.ShuoArmModel;
import com.myriad.network.AbilityVfxPayload;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Production item, cropped skin arms and motion samples. CPU projection, never a game capture. */
public final class RivetGripPreview {
    private static final int W=960,H=540;
    private static final String[] STATES={"放松 / Idle","普攻起手 / 0.18","普攻打击 / 0.50","Q 起手 / progress 0.85","Q 挥击 / tick 2.6","Q 回收 / tick 6.5"};
    private static final Map<String,Object> audit=new LinkedHashMap<>();
    private static List<NightDaggerPreview.Quad> hammer;
    private static final Map<String,List<NightDaggerPreview.Quad>> arms=new HashMap<>();
    private static BufferedImage wideSkin,slimSkin;
    private static boolean baseline;
    private static boolean finalEvidence;
    private static Method itemMethod,handMethod;
    private static double support(RivetWeaponMotion.Pose p){
        if(!baseline)return 0;
        try{return ((Number)p.getClass().getMethod("support").invoke(p)).doubleValue();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Baseline pose must retain its original support curve",failure);}
    }
    private static RivetWeaponMotion.Pose pose(int state){
        var motion=new RivetWeaponMotion();
        if(state==0)return motion.sample(100,0,false);
        if(state==1||state==2)return motion.sample(100,state==1?.18:.50,false);
        var packet=new AbilityVfxPayload(1,-1,13,2,state==3?CHARGE:ARC,
                Vec3.ZERO,new Vec3(0,0,3),3,state==3?.85f:110,state==3?3:7,100,false);
        motion.accept(packet,100);
        return motion.sample(state==3?102:state==4?102.6:106.5,0,false);
    }
    private static Matrix4f oldBase(RivetWeaponMotion.Pose p,boolean left){
        float side=left?-1:1,deg=(float)(Math.PI/180);
        return new Matrix4f().translation((float)p.x()*side,(float)p.y(),(float)p.z())
                .rotateX((float)p.pitch()*deg).rotateY((float)p.yaw()*side*deg).rotateZ((float)p.roll()*side*deg);
    }
    private static Matrix4f item(RivetWeaponMotion.Pose p,boolean left)throws Exception{
        if(!baseline)return (Matrix4f)itemMethod.invoke(null,p,left);
        // Exact 485ddca RenderHandEvent chain; input mesh is already item-centred by the MC parser helper.
        return oldBase(p,left).scale(.70f).translate(.5f,.15625f,.5f);
    }
    private static Matrix4f hand(RivetWeaponMotion.Pose p,boolean slim,boolean left,boolean off)throws Exception{
        if(!baseline){if(off)throw new IllegalArgumentException("Final renderer uses only the actual main hand");return (Matrix4f)handMethod.invoke(null,p,slim,left);}
        float side=left?-1:1;var base=oldBase(p,left);
        if(off)base.translate(.04f*side,(float)(.18+.10*support(p)),.025f).rotateY(side*.22f);
        return base.rotateZ(-(float)Math.PI/2).rotateY(-(float)Math.PI/2).rotateX(.22f)
                .scale(.74f).mul(NightDaggerRig.arm(slim,off?!left:left));
    }
    private static List<NightDaggerPreview.Quad> arm(boolean slim,boolean left){
        return arms.computeIfAbsent(slim+"/"+left,key->{
            ModelPart part;
            if(baseline)part=ShuoArmModel.bake(slim,left);
            else try{part=(ModelPart)Class.forName("com.myriad.client.firstperson.RivetArmModel").getMethod("bake",boolean.class,boolean.class).invoke(null,slim,left);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Preview must use the corrected renderer's actual arm mesh",failure);}
            part.resetPose();part.setPos(0,0,0);part.setRotation(0,0,0);
            var result=new ArrayList<NightDaggerPreview.Quad>();
            part.visit(new PoseStack(),(matrix,path,index,cube)->{
                for(var polygon:cube.polygons){
                    var points=new Vector3f[4];var uv=new float[8];
                    for(int i=0;i<4;i++){var v=polygon.vertices()[i];
                        points[i]=matrix.pose().transformPosition(new Vector3f(v.worldX(),v.worldY(),v.worldZ()));
                        uv[i*2]=v.u();uv[i*2+1]=v.v();}
                    result.add(new NightDaggerPreview.Quad(points,uv,slim?slimSkin:wideSkin));
                }
            });return result;
        });
    }
    private record Vertex(Vector3f p,float u,float v){
        Vertex mix(Vertex b,float t){return new Vertex(new Vector3f(p).lerp(b.p,t),u+(b.u-u)*t,v+(b.v-v)*t);}
    }
    private static void submit(BufferedImage image,float[] depth,List<NightDaggerPreview.Quad> mesh,Matrix4f matrix,float hudFov){
        for(var q:mesh){
            var points=new Vector3f[4];for(int i=0;i<4;i++)points[i]=matrix.transformPosition(new Vector3f(q.p()[i]));
            var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
            float shade=.65f+.35f*Math.abs(normal.normalize().dot(new Vector3f(.3f,.6f,1).normalize()));
            for(int[] ix:new int[][]{{0,1,2},{0,2,3}}){
                var source=new ArrayList<Vertex>();for(int i:ix)source.add(new Vertex(points[i],q.uv()[2*i],q.uv()[2*i+1]));
                var clipped=new ArrayList<Vertex>();
                for(int i=0;i<3;i++){var a=source.get(i);var b=source.get((i+1)%3);boolean ai=a.p.z<-.05f,bi=b.p.z<-.05f;
                    if(ai)clipped.add(a);if(ai!=bi)clipped.add(a.mix(b,(-.05f-a.p.z)/(b.p.z-a.p.z)));}
                for(int i=1;i+1<clipped.size();i++)triangle(image,depth,clipped.get(0),clipped.get(i),clipped.get(i+1),q.texture(),shade,hudFov);
            }
        }
    }
    private static void triangle(BufferedImage image,float[] depth,Vertex a,Vertex b,Vertex c,BufferedImage texture,float shade,float fov){
        var v=new Vertex[]{a,b,c};float[] x=new float[3],y=new float[3],z=new float[3];
        float focal=(float)(H*.5/Math.tan(Math.toRadians(fov*.5)));
        for(int i=0;i<3;i++){z[i]=1/-v[i].p.z;x[i]=W*.5f+v[i].p.x*z[i]*focal;y[i]=H*.5f-v[i].p.y*z[i]*focal;}
        float area=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);if(Math.abs(area)<1E-6)return;
        int x0=Math.max(0,(int)Math.floor(Math.min(x[0],Math.min(x[1],x[2])))),x1=Math.min(W-1,(int)Math.ceil(Math.max(x[0],Math.max(x[1],x[2]))));
        int y0=Math.max(0,(int)Math.floor(Math.min(y[0],Math.min(y[1],y[2])))),y1=Math.min(H-1,(int)Math.ceil(Math.max(y[0],Math.max(y[1],y[2]))));
        for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){
            float u=((y[1]-y[2])*(xx+.5f-x[2])+(x[2]-x[1])*(yy+.5f-y[2]))/area;
            float w=((y[2]-y[0])*(xx+.5f-x[2])+(x[0]-x[2])*(yy+.5f-y[2]))/area,t=1-u-w;if(u<0||w<0||t<0)continue;
            float d=u*z[0]+w*z[1]+t*z[2];int at=yy*W+xx;if(d<=depth[at])continue;
            float tu=(u*z[0]*a.u+w*z[1]*b.u+t*z[2]*c.u)/d,tv=(u*z[0]*a.v+w*z[1]*b.v+t*z[2]*c.v)/d;
            int color=texture.getRGB(Math.clamp((int)(tu*texture.getWidth()),0,texture.getWidth()-1),Math.clamp((int)(tv*texture.getHeight()),0,texture.getHeight()-1));
            if((color>>>24)<128)continue;
            int r=(int)(((color>>16)&255)*shade),g=(int)(((color>>8)&255)*shade),bb=(int)((color&255)*shade);
            image.setRGB(xx,yy,(r<<16)|(g<<8)|bb);depth[at]=d;
        }
    }
    private static Map<String,Object> counts(float[] mask){
        int all=0,centre=0,bottom=0,minX=W,minY=H,maxX=-1,maxY=-1;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(Float.isFinite(mask[y*W+x])){
            all++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            if(x>=W*.4&&x<W*.6&&y>=H*.3&&y<H*.7)centre++;
            if(x>=W*.30&&x<W*.75&&y>=H*.80)bottom++;
        }
        var result=new LinkedHashMap<String,Object>();result.put("visiblePixels",all);result.put("centralWindowPixels",centre);
        result.put("bottomMiddlePixels",bottom);result.put("rasterBounds",new int[]{minX,minY,maxX,maxY});return result;
    }
    private static float[] depth(){var result=new float[W*H];Arrays.fill(result,Float.NEGATIVE_INFINITY);return result;}
    private static List<NightDaggerPreview.Quad> proximalCaps(List<NightDaggerPreview.Quad> mesh){
        float minY=Float.POSITIVE_INFINITY;for(var q:mesh)for(var v:q.p())minY=Math.min(minY,v.y);
        var result=new ArrayList<NightDaggerPreview.Quad>();
        for(var q:mesh){boolean flat=true;for(var v:q.p())flat&=Math.abs(v.y-q.p()[0].y)<1E-6;
            if(flat&&q.p()[0].y<=minY+1f/32)result.add(q);}
        return result;
    }
    private static BufferedImage draw(int state,boolean slim,boolean left,int worldFov,int hudFov,String revision,boolean record)throws Exception{
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        int horizon=(int)(H*.44);
        g.setPaint(new GradientPaint(0,0,new Color(99,135,166),0,horizon,new Color(159,185,197)));g.fillRect(0,0,W,horizon);
        g.setPaint(new GradientPaint(0,horizon,new Color(108,130,86),0,H,new Color(77,101,66)));g.fillRect(0,horizon,W,H-horizon);
        g.setColor(new Color(91,119,81));g.drawLine(0,horizon,W,horizon);g.dispose();
        var p=pose(state);var union=depth();var weapon=depth();var skin=depth();
        submit(image,union,arm(slim,left),hand(p,slim,left,false),hudFov);
        submit(new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB),skin,arm(slim,left),hand(p,slim,left,false),hudFov);
        if(support(p)>.1){submit(image,union,arm(slim,!left),hand(p,slim,left,true),hudFov);
            submit(new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB),skin,arm(slim,!left),hand(p,slim,left,true),hudFov);}
        var matrix=item(p,left);submit(image,union,hammer,matrix,hudFov);
        submit(new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB),weapon,hammer,matrix,hudFov);
        var caps=depth();submit(new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB),caps,proximalCaps(arm(slim,left)),hand(p,slim,left,false),hudFov);
        int visibleCaps=0;for(int i=0;i<caps.length;i++)if(Float.isFinite(caps[i])&&caps[i]>=union[i]-1E-5)visibleCaps++;
        var skinCounts=counts(skin);var skinBounds=(int[])skinCounts.get("rasterBounds");
        boolean reachesEdge=skinBounds[0]==0||skinBounds[1]==0||skinBounds[2]==W-1||skinBounds[3]==H-1;
        if(finalEvidence&&!baseline&&(visibleCaps>0||!reachesEdge))
            throw new IllegalStateException("Actual forearm ends on screen: state="+state+" left="+left+" slim="+slim+" hud="+hudFov+" visibleCaps="+visibleCaps+" reachesEdge="+reachesEdge);
        if(record){
            float near=-Float.MAX_VALUE;for(var q:hammer)for(var v:q.p())near=Math.max(near,matrix.transformPosition(new Vector3f(v)).z);
            var entry=new LinkedHashMap<String,Object>();entry.put("pose",p);entry.put("worldFov",worldFov);entry.put("hudFov",hudFov);
            entry.put("union",counts(union));entry.put("weapon",counts(weapon));entry.put("skin",skinCounts);entry.put("nearestWeaponZ",near);
            entry.put("visibleProximalCapPixels",visibleCaps);entry.put("forearmReachesViewportEdge",reachesEdge);
            var grip=matrix.transformPosition(new Vector3f(-.5f,5.5f/16-.5f,-.5f));
            var main=hand(p,slim,left,false);boolean physicalLeft=left;
            var palm=main.transformPosition(new Vector3f((slim?.5f:1f)*(physicalLeft?1:-1)/16,8f/16,0));
            entry.put("mainPalmGripErrorBlocks",palm.distance(grip));entry.put("itemDeterminant",matrix.determinant3x3());entry.put("mainHandDeterminant",main.determinant3x3());
            if(!baseline){
                if(palm.distance(grip)>2E-6||matrix.determinant3x3()<=0||main.determinant3x3()<=0)
                    throw new IllegalStateException("Actual main palm/item grip or positive winding failed: "+state+"/"+left+"/"+slim);
            }
            audit.put(STATES[state]+"/"+(slim?"slim":"wide")+"/"+(left?"left":"right")+"/hud"+hudFov,entry);
        }
        g=image.createGraphics();g.setColor(new Color(242,242,220,140));g.drawLine(W/2-4,H/2,W/2+4,H/2);g.drawLine(W/2,H/2-4,W/2,H/2+4);
        g.setColor(new Color(9,17,26,230));g.fillRect(0,0,W,64);g.setFont(new Font("SansSerif",Font.PLAIN,22));g.setColor(new Color(236,222,179));
        g.drawString(revision+" | "+STATES[state]+" | "+(left?"Left":"Right")+" / "+(slim?"Slim":"Wide"),16,27);
        g.setFont(new Font("SansSerif",Font.PLAIN,15));g.setColor(new Color(185,204,206));
        g.drawString("World FOV "+worldFov+" / hand HUD FOV "+hudFov+" | Real meshes + poses / illustrative background / OFFLINE",16,52);g.dispose();return image;
    }
    private static void save(BufferedImage image,Path path)throws Exception{ImageIO.write(image,"png",path.toFile());}
    public static void main(String[] args)throws Exception{
        Path project=Path.of(args[0]),out=Path.of(args[1]),clientJar=Path.of(args[2]);baseline=args.length>3&&args[3].equals("--baseline");
        finalEvidence=args.length>3&&args[3].equals("--final");Files.createDirectories(out);
        if(!baseline){var rig=Class.forName("com.myriad.character.skills.rivet.RivetWeaponRig");
            itemMethod=rig.getMethod("item",RivetWeaponMotion.Pose.class,boolean.class);
            handMethod=rig.getMethod("hand",RivetWeaponMotion.Pose.class,boolean.class,boolean.class);}
        Path assets=project.resolve("src/main/resources/assets/myriad");NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/rivet_metal.png").toFile());
        hammer=NightDaggerPreview.loadBlade(assets,"rivet_hammer");
        try(var zip=new ZipFile(clientJar.toFile())){
            wideSkin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
            slimSkin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/slim/alex.png")));}
        String revision=baseline?"实装基线 485ddca":args.length>3&&args[3].equals("--final")?"最终 POV 修正":"POV 候选草图";
        var sheet=new BufferedImage(W*3,H*2,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        for(int i=0;i<STATES.length;i++){var frame=draw(i,false,false,70,70,revision,true);save(frame,out.resolve("state-"+i+".png"));g.drawImage(frame,(i%3)*W,(i/3)*H,null);}
        g.dispose();save(sheet,out.resolve("motion.png"));
        var hands=new BufferedImage(W*2,H*2,BufferedImage.TYPE_INT_RGB);g=hands.createGraphics();
        for(int i=0;i<4;i++)g.drawImage(draw(0,i>=2,i%2==1,70,70,revision,true),(i%2)*W,(i/2)*H,null);
        g.dispose();save(hands,out.resolve("hands.png"));
        var fovs=new BufferedImage(W*2,H*2,BufferedImage.TYPE_INT_RGB);g=fovs.createGraphics();int[] world={40,70,110,70};
        for(int i=0;i<4;i++)g.drawImage(draw(0,false,false,world[i],i==3?60:70,revision,true),(i%2)*W,(i/2)*H,null);
        g.dispose();save(fovs,out.resolve("fov.png"));
        for(int state=0;state<6;state++)for(boolean slim:new boolean[]{false,true})for(boolean left:new boolean[]{false,true})
            for(int hud:new int[]{60,70})draw(state,slim,left,70,hud,revision,true);
        var summary=new LinkedHashMap<String,Object>();summary.put("provenance","Actual cuboid item/UVs, "+(baseline?"baseline ShuoArmModel cropped skin geometry":"corrected RivetArmModel skin/fist mesh with bounded proximal forearm continuation")+" and production motion; CPU depth projection. No game/client launched.");
        summary.put("baseline",baseline);summary.put("viewport",new int[]{W,H});summary.put("normalHudFov",70);summary.put("worldFovDoesNotReplaceHudFov",true);
        summary.put("limitations","Illustrative lighting and opaque depth raster; no actual client lighting, GPU face-culling, camera bob, HUD configuration, latency or feel acceptance.");summary.put("cases",audit);
        Files.writeString(out.resolve("projection-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(summary)+"\n");
        System.out.println("[MYRIAD-RIVET-GRIP-PROJECTION] "+revision+": "+audit.size()+" actual pose/skin/hand/FOV cases; offline only");
    }
}
