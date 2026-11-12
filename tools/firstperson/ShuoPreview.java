package com.myriad.tools;
import com.myriad.client.firstperson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.myriad.network.ShuoViewPayload.*;
/** Actual exported meshes, UVs, player skin and production rig. Offline projection, NOT game capture. */
public final class ShuoPreview {
    private static List<NightDaggerPreview.Quad> blade,sheath;
    private static BufferedImage texture;private static int centralIdle;
    private static List<NightDaggerPreview.Quad> arm(Matrix4f hand,boolean slim,boolean left){
        var model=ShuoArmModel.bake(slim,left);
        var out=new ArrayList<NightDaggerPreview.Quad>();
        model.visit(new PoseStack(),(pose,path,index,cube)->{for(var face:cube.polygons){
            var vertices=new Vector3f[4];var uv=new float[8];for(int i=0;i<4;i++){var v=face.vertices()[i];
                vertices[i]=pose.pose().transformPosition(new Vector3f(v.worldX(),v.worldY(),v.worldZ()));uv[2*i]=v.u();uv[2*i+1]=v.v();}
            out.add(new NightDaggerPreview.Quad(vertices,uv,NightDaggerPreview.skin));
        }});return out;
    }
    private static BufferedImage draw(int action,double t,boolean drawn,boolean mirror,String title){
        return draw(ShuoRig.frame(action,t,drawn),drawn,mirror,title);
    }
    static BufferedImage draw(ShuoRig.Frame f,boolean drawn,boolean mirror,String title){
        var im=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(30,45,60),0,360,new Color(75,92,82)));g.fillRect(0,0,640,360);
        g.setColor(new Color(180,200,195));g.drawLine(315,180,325,180);g.drawLine(320,175,320,185);g.dispose();
        var depth=new float[640*360];Arrays.fill(depth,Float.NEGATIVE_INFINITY);int action=f.action();
        for(boolean off:new boolean[]{true,false}){
            if(off&&!ShuoRig.SHOW_OFF_HAND)continue;
            if(!off&&!ShuoRig.showMainHand(f,drawn))continue;
            boolean left=off?!mirror:mirror;var hand=ShuoRig.hand(f,off,mirror);
            NightDaggerPreview.submit(im,depth,arm(hand,mirror,left),new Matrix4f(hand).mul(NightDaggerRig.arm(mirror,left)));
        }
        NightDaggerPreview.submit(im,depth,sheath,ShuoRig.sheath(f,mirror));NightDaggerPreview.submit(im,depth,blade,ShuoRig.blade(f,mirror));
        if(action==CANCEL&&!drawn){int total=0,central=0;for(int y=0;y<360;y++)for(int x=0;x<640;x++)if(Float.isFinite(depth[y*640+x])){
            total++;if(x>=256&&x<384&&y>=108&&y<252)central++;
        }NightDaggerPreview.require(total>1000,"Sheathed hilt and visible one-third sheath remain visible without any hand");NightDaggerPreview.require(central<100,"Central aiming window should be clear: "+central);centralIdle=central;
            System.out.println("[MYRIAD-SHUO-IDLE] mirror/slim="+mirror+" total="+total+" center="+central);}
        g=im.createGraphics();g.setColor(new Color(10,18,28,230));g.fillRect(0,0,420,47);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(219,239,255));g.drawString(title,12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(165,185,196));g.drawString("实际模型 / 真实皮肤 / 生产握持矩阵 · 离线投影，非游戏截图",12,39);g.dispose();return im;
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);Path assets=root.resolve("src/main/resources/assets/myriad");
        texture=ImageIO.read(assets.resolve("textures/item/shuo_crescent.png").toFile());NightDaggerPreview.dagger=texture;
        blade=NightDaggerPreview.loadBlade(assets,"shuo_sword");sheath=NightDaggerPreview.loadBlade(assets,"shuo_scabbard");
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        int samples=0;for(int action=0;action<=MAX_ACTION;action++)for(int n=0;n<=80;n++)for(boolean drawn:new boolean[]{false,true})for(boolean mirror:new boolean[]{false,true}){
            var f=ShuoRig.frame(action,n/80.0,drawn);var transforms=List.of(ShuoRig.blade(f,mirror),ShuoRig.sheath(f,mirror));int k=0;
            for(var mesh:List.of(blade,sheath)){var mat=transforms.get(k++);for(var q:mesh)for(var p:q.p()){
                var v=mat.transformPosition(new Vector3f(p));if(!v.isFinite()||v.z>=-.051)throw new IllegalStateException("Actual model crossed near plane, action="+action+" time="+n+" pos="+v);
            }}samples++;
        }
        var sheet=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        int[] actions={CANCEL,CHARGE,DRAW,DRAW,SHEATHE,CANCEL};double[] times={0,.7,.2,.6,.7,0};boolean[] drawn={false,false,false,false,true,true};
        String[] titles={"朔 · 收鞘不露手","收鞘蓄势 / 不露手","向前伸手 / 靠近护手握柄","出刀后持续握柄","握柄回鞘 / 完成后隐手","前臂朝前 / 握点上移"};
        for(int i=0;i<6;i++)g.drawImage(draw(actions[i],times[i],drawn[i],false,titles[i]),i%3*640,i/3*360,null);g.dispose();ImageIO.write(sheet,"png",out.resolve("shuo_pose_sheet.png").toFile());
        // Turning the curved tip towards the camera changes its projection.
        // The default drawn pose must retain the entire weapon inside the view.
        float top=360,bottom=0;var ready=ShuoRig.blade(ShuoRig.frame(CANCEL,0,true),false);
        for(var q:blade)for(var p:q.p()){
            var v=ready.transformPosition(new Vector3f(p));float y=180-v.y/(-v.z)*(float)(180/Math.tan(Math.toRadians(35)));
            top=Math.min(top,y);bottom=Math.max(bottom,y);
        }
        NightDaggerPreview.require(top>=4&&bottom<=356,"Drawn sword, including its turned curved tip, fits the default view: "+top+".."+bottom);
        ImageIO.write(draw(CANCEL,0,false,true,"左惯用手 / 细臂 · 收鞘待机"),"png",out.resolve("shuo_left_slim.png").toFile());
        ImageIO.write(draw(CANCEL,0,true,false,"朔 · 前臂朝前伸出 / 靠近护手握柄"),"png",out.resolve("shuo_drawn_ready.png").toFile());
        ImageIO.write(draw(CANCEL,0,true,true,"左惯用手 / 细臂 · 向前握刀"),"png",out.resolve("shuo_drawn_left_slim.png").toFile());
        var frames=new ArrayList<BufferedImage>();
        for(int i=0;i<10;i++)frames.add(draw(CANCEL,0,false,false,"收鞘待机"));
        for(int i=0;i<12;i++)frames.add(draw(CHARGE,i/11.0,false,false,"低位蓄势"));
        for(int i=0;i<12;i++)frames.add(draw(DRAW,i/11.0,false,false,"右手抽刀 · 瞬斩"));
        for(int i=0;i<8;i++)frames.add(draw(CANCEL,0,true,false,"出鞘随势"));
        for(int i=0;i<10;i++)frames.add(draw(SHEATHE,i/9.0,true,false,"回鞘 · 金属扣合"));
        for(int i=0;i<10;i++)frames.add(draw(CANCEL,0,false,false,"收鞘待机"));
        NightDaggerPreview.gif(frames,out.resolve("shuo_draw_sheathe.gif"));
        ImageIO.write(draw(GUARD,.5,false,false,"鞘返格挡 · 横鞘在屏幕中央"),"png",out.resolve("shuo_horizontal_guard.png").toFile());
        var guardFrames=new ArrayList<BufferedImage>();
        for(int i=0;i<24;i++)guardFrames.add(draw(GUARD,i/23.0,false,false,"鞘返 · 提鞘 / 横架 / 收势"));
        NightDaggerPreview.gif(guardFrames,out.resolve("shuo_horizontal_guard.gif"));
        var comboSheet=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);var cg=comboSheet.createGraphics();
        int[] basicActions={DRAW,BASIC_RISE,BASIC_SWEEP};String[] names={"第一式 · 斜斩","第二式 · 反向上挑","第三式 · 横向收斩"};
        var comboFrames=new ArrayList<BufferedImage>();
        for(int row=0;row<3;row++){
            for(int col=0;col<3;col++)cg.drawImage(draw(basicActions[row],new double[]{.18,.42,.60}[col],true,false,names[row]),col*640,row*360,null);
            for(int n=0;n<18;n++)comboFrames.add(draw(basicActions[row],n/17.0,true,false,names[row]));
            for(int n=0;n<6;n++)comboFrames.add(draw(CANCEL,0,true,false,"出鞘待机"));
        }cg.dispose();ImageIO.write(comboSheet,"png",out.resolve("shuo_three_basics.png").toFile());NightDaggerPreview.gif(comboFrames,out.resolve("shuo_three_basics.gif"));
        System.out.println("[MYRIAD-SHUO-PREVIEW] PASS: "+samples+" full exported mesh near-plane scans, real skin hand persists while drawn, center clear ("+centralIdle+" pixels); real MC item JSON parser accepts both models. Not live-client acceptance.");
    }
}
