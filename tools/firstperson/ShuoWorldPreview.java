package com.myriad.tools;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.shuo.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import static com.myriad.character.skills.shuo.ShuoSkillVfx.*;
/** Production world-space meshes/UVs composited on a metric block figure. NOT a game screenshot. */
public final class ShuoWorldPreview {
    private static Vec3 eye,forward,right,up;
    private static Vec3 camera(Vec3 v){var d=v.subtract(eye);return new Vec3(d.dot(right),d.dot(up),-d.dot(forward));}
    private static Quad transform(Quad q){return new Quad(camera(q.a()),camera(q.b()),camera(q.c()),camera(q.d()),q.color(),q.alpha(),q.material(),q.reverseV(),q.startU(),q.endU(),q.startV(),q.endV());}
    private static void submit(BufferedImage im,List<Quad> input){
        input.stream().map(ShuoWorldPreview::transform).sorted(Comparator.comparingDouble(q->(q.a().z+q.b().z+q.c().z+q.d().z)/4)).forEach(q->{
            if(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).z<=0)return;
            float v0=q.startV(),v1=q.endV();if(q.reverseV()){float t=v0;v0=v1;v1=t;}
            var uv=new float[]{q.startU(),v1,q.endU(),v1,q.endU(),v0,q.startU(),v0};var v=new Vec3[]{q.a(),q.b(),q.c(),q.d()};
            ShuoVfxPreview.triangle(im,q,v,uv,0,1,2);ShuoVfxPreview.triangle(im,q,v,uv,0,2,3);
        });
    }
    private static void box(List<Quad> out,Vec3 min,Vec3 max,int color){
        Vec3[] v={new Vec3(min.x,min.y,min.z),new Vec3(max.x,min.y,min.z),new Vec3(max.x,max.y,min.z),new Vec3(min.x,max.y,min.z),
            new Vec3(min.x,min.y,max.z),new Vec3(max.x,min.y,max.z),new Vec3(max.x,max.y,max.z),new Vec3(min.x,max.y,max.z)};
        for(int[] f:new int[][]{{0,1,2,3},{4,5,6,7},{0,4,7,3},{1,5,6,2},{0,1,5,4},{3,2,6,7}}){
            out.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color,1,99));out.add(new Quad(v[f[3]],v[f[2]],v[f[1]],v[f[0]],color,1,99));
        }
    }
    private static AbilityVfxPayload phase(int index,int signal,Vec3 at,Vec3 to,float radius,int life){return ShuoVisuals.recipe(1,-1,index,signal,at,to,radius,life,0);}
    private static AbilityVfxPayload cut(int index,int signal,float range,float degrees){return phase(index,signal,new Vec3(0,.9,0),new Vec3(0,.9,degrees/30),range,7);}
    private static List<AbilityVfxPayload> form(int node,boolean drawn){
        return switch(node){
            case 2->drawn?List.of(cut(node,RIGHT,3,90),phase(node,CHASE,new Vec3(0,.75,2),new Vec3(0,.75,0),.45f,5)):
                List.of(phase(node,TRACE,new Vec3(0,.75,2),new Vec3(0,.75,0),.45f,5));
            case 3->drawn?List.of(cut(node,RIGHT,3.5f,100)):List.of(phase(node,GUARDING,new Vec3(0,1.05,0),new Vec3(0,1.05,1),1.2f,2));
            case 4->List.of(phase(node,drawn?CHASE:TRACE,new Vec3(0,.75,-4),new Vec3(0,.75,0),.75f,5),phase(node,CONTACT,new Vec3(0,1,1),new Vec3(0,1,2),.6f,7));
            case 5->drawn?List.of(cut(node,RIGHT,3.5f,100)):List.of(phase(node,RIFT,new Vec3(0,.9,0),new Vec3(0,.9,7),1.2f,6));
            case 6->drawn?List.of(cut(node,DOWN,5,120)):List.of(phase(node,TRACE,new Vec3(0,.75,-6),new Vec3(0,.75,0),.75f,5),phase(node,MARK,new Vec3(0,1,3),new Vec3(0,1,4),.8f,20));
            case 7->List.of(cut(node,DOWN,drawn?5:4.5f,drawn?140:90));
            default->throw new IllegalArgumentException();
        };
    }
    private static BufferedImage frame(List<AbilityVfxPayload> phases,float t,String title,Color bg,int view){
        return frame(phases,t,title,bg,view,false);
    }
    private static BufferedImage frame(List<AbilityVfxPayload> phases,float t,String title,Color bg,int view,boolean absolute){
        eye=view==0?new Vec3(4,3,-5):view==1?new Vec3(-5,2.3,2):new Vec3(0,2.6,7);
        forward=new Vec3(0,.8,1).subtract(eye).normalize();right=forward.cross(new Vec3(0,1,0)).normalize();up=right.cross(forward).normalize();
        var im=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();g.setColor(bg);g.fillRect(0,0,640,360);g.dispose();
        var env=new ArrayList<Quad>();
        for(int x=-7;x<8;x++)for(int z=-6;z<10;z++){
            int c=((x+z)&1)==0?0x516642:0x596F48;
            env.add(new Quad(new Vec3(x,-.02,z),new Vec3(x,-.02,z+1),new Vec3(x+1,-.02,z+1),new Vec3(x+1,-.02,z),c,1,99));
        }
        box(env,new Vec3(-.25,0,-.15),new Vec3(-.02,.75,.15),0x374359);box(env,new Vec3(.02,0,-.15),new Vec3(.25,.75,.15),0x374359);
        box(env,new Vec3(-.32,.75,-.16),new Vec3(.32,1.4,.16),0x8D9BAA);box(env,new Vec3(-.23,1.4,-.23),new Vec3(.23,1.86,.23),0xBEC8D2);
        submit(im,env);var mesh=new ArrayList<Quad>();for(var p:phases)mesh.addAll(AbilityVfxGeometry.build(p,absolute?t-p.started():t*p.life(),false));submit(im,mesh);
        g=im.createGraphics();g.setColor(new Color(10,18,28,235));g.fillRect(0,0,640,43);g.setColor(Color.WHITE);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.drawString(title,12,19);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.drawString("生产几何 / 原生高清透明素材 / 1.86格人物参照 · 离线合成，非游戏截图",12,37);g.dispose();return im;
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]).resolve("src/main/resources/assets/myriad/textures/particle/vfx"),out=Path.of(args[1]);Files.createDirectories(out);
        String[] names={"shuo_basic_hd","shuo_guard_hd","shuo_stream_hd","shuo_impact_hd"};for(int i=0;i<4;i++)ShuoVfxPreview.materials.put(75+i,ImageIO.read(root.resolve(names[i]+".png").toFile()));
        var white=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);white.setRGB(0,0,0xFFFFFFFF);ShuoVfxPreview.materials.put(99,white);
        try(var zip=new java.util.zip.ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            var atlas=com.google.gson.JsonParser.parseString(Files.readString(root.getParent().getParent().getParent().resolve("particles/ability_mesh.json"))).getAsJsonObject().getAsJsonArray("textures");
            String name=atlas.get(0).getAsString().split(":")[1];ShuoVfxPreview.materials.put(0,ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/"+name+".png"))));
        }
        var bg=new Color(29,42,55);String[][] labels={{"纳刀 / 侧退流影","纳刀 / 斩击退步"},{"鞘返 / 横鞘防御","流刃 / 即时反向斩"},{"一闪 / 拔刀突进","追风 / 持刀追击"},{"断流 / 七格窄路截击","回澜 / 先左后右双斩"},{"无声一线 / 穿行留印","断空 / 终点重斩"},{"残月 / 三次不同方向","回月 / 蓄势终斩"}};
        var sheet=new BufferedImage(1280,2160,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        for(int row=0;row<6;row++)for(int col=0;col<2;col++)g.drawImage(frame(form(row+2,col==1),.46f,labels[row][col],bg,0),col*640,row*360,null);
        g.dispose();ImageIO.write(sheet,"png",out.resolve("shuo_world_12_forms.png").toFile());
        var contexts=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);g=contexts.createGraphics();
        for(int col=0;col<3;col++)for(int row=0;row<2;row++)g.drawImage(frame(form(row==0?3:5,false),.46f,row==0?"护鞘 / 外部观察":"断流 / 外部观察",List.of(bg,new Color(112,143,78),new Color(213,225,235)).get(col),col),col*640,row*360,null);
        g.dispose();ImageIO.write(contexts,"png",out.resolve("shuo_world_contexts.png").toFile());
        var animation=new ArrayList<BufferedImage>();
        for(int n=0;n<36;n++){
            int phase=n/12;float t=(n%12+.25f)/12;var recipes=phase==0?List.of(phase(6,CHARGING,Vec3.ZERO,new Vec3(0,0,1),1,14)):
                phase==1?form(6,false):List.of(phase(6,CONTACT,new Vec3(0,1,3),new Vec3(0,1,4),1.1f,7));
            animation.add(frame(recipes,t,phase==0?"无声一线 / 蓄势":phase==1?"穿行 / 留印":"回鞘结算 / 刃星绽开",bg,0));
        }NightDaggerPreview.gif(animation,out.resolve("shuo_world_phases.gif"));
        var dashSheet=new BufferedImage(1920,1440,BufferedImage.TYPE_INT_RGB);g=dashSheet.createGraphics();
        int[] nodes={2,4,6,7};String[] motionNames={"纳刀 · 收束墨影","一闪／追风 · 双侧刃流","无声一线 · 裂空长痕","残月追击 · 短促交锋"};
        for(int row=0;row<4;row++)for(int col=0;col<3;col++){
            float tick=col==0?1.35f:col==1?4.35f:7.6f;
            g.drawImage(frame(dash(nodes[row],tick),tick,motionNames[row]+" / "+(col==0?"起步":col==1?"沿实际路径穿行":"收势"),bg,1,true),col*640,row*360,null);
        }
        g.dispose();ImageIO.write(dashSheet,"png",out.resolve("shuo_dash_layers.png").toFile());
        var dashAnimation=new ArrayList<BufferedImage>();
        for(int n=0;n<45;n++){float tick=n/3f+.1f;dashAnimation.add(frame(dash(6,tick),tick,"无声一线 · 起势／双翼刃流／墨影／收势（离线）",bg,1,true));}
        NightDaggerPreview.gif(dashAnimation,out.resolve("shuo_dash_flow.gif"));
        System.out.println("[MYRIAD-SHUO-DASH-PREVIEW] PASS: four production motion styles, staggered actual-length trail samples, start/cruise/finish and finite afterglow. Offline only.");
        System.out.println("[MYRIAD-SHUO-WORLD-PREVIEW] PASS: actual HD materials/meshes, twelve forms, three external cameras, phased animation. Offline projection, NOT live acceptance.");
    }
    private static List<AbilityVfxPayload> dash(int node,float tick){
        var result=new ArrayList<AbilityVfxPayload>();float stride=node==2?.5f:node==7?.5f:node==6?1.7f:1.15f;
        int steps=node==7?4:7;float current=Math.min(tick,steps)*stride,width=node==2?.55f:node==6?1.05f:.85f;
        var origin=new Vec3(0,.75,-current);
        result.add(ShuoVisuals.recipe(1,-1,node,ShuoDashVfx.DEPART,origin,origin.add(0,0,1),width,7,0));
        for(int i=0;i<Math.min(steps,(int)tick);i++){
            var from=origin.add(0,0,i*stride);var to=origin.add(0,0,(i+1)*stride);
            result.add(ShuoVisuals.recipe(1,-1,node,node==2?TRACE:CHASE,from,to,width,7,i+1));
        }
        if(tick>=steps){var at=new Vec3(0,.75,0);result.add(ShuoVisuals.recipe(1,-1,node,ShuoDashVfx.ARRIVE,at,at.add(0,0,1),width,8,steps));}
        return result;
    }
}
