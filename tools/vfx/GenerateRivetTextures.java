import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Original vector geometry, rasterised by the same paths; no external art, renderer or downloads. */
public final class GenerateRivetTextures {
    private final BufferedImage image;private final Graphics2D g;private final StringBuilder svg=new StringBuilder();
    private GenerateRivetTextures(int width,int height,String title){
        image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" role=\"img\" aria-labelledby=\"title desc\"><title id=\"title\">").append(title)
                .append("</title><desc id=\"desc\">Original neutral-white industrial VFX geometry with straight alpha, tinted at runtime.</desc><g id=\"effect\">");
    }
    private static String n(double v){return String.format(Locale.ROOT,"%.3f",v);}
    private void polygon(int alpha,double... xy){
        var p=new Path2D.Double();p.moveTo(xy[0],xy[1]);StringBuilder points=new StringBuilder();
        for(int i=0;i<xy.length;i+=2){if(i>0)p.lineTo(xy[i],xy[i+1]);points.append(n(xy[i])).append(',').append(n(xy[i+1])).append(' ');}
        p.closePath();g.setColor(new Color(255,255,255,alpha));g.fill(p);
        svg.append("<polygon fill=\"white\" fill-opacity=\"").append(n(alpha/255.0)).append("\" points=\"").append(points).append("\"/>");
    }
    private void line(int alpha,double width,double x,double y,double toX,double toY){
        g.setColor(new Color(255,255,255,alpha));g.setStroke(new BasicStroke((float)width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
        g.draw(new Line2D.Double(x,y,toX,toY));
        svg.append("<path fill=\"none\" stroke=\"white\" stroke-opacity=\"").append(n(alpha/255.0)).append("\" stroke-width=\"").append(n(width))
                .append("\" d=\"M ").append(n(x)).append(' ').append(n(y)).append(" L ").append(n(toX)).append(' ').append(n(toY)).append("\"/>");
    }
    private void radialLine(int alpha,double width,double angle,double inner,double outer){
        line(alpha,width,256+Math.cos(angle)*inner,256+Math.sin(angle)*inner,256+Math.cos(angle)*outer,256+Math.sin(angle)*outer);
    }
    private static GenerateRivetTextures compression(){
        var a=new GenerateRivetTextures(512,512,"Rivet hydraulic compression flash");
        // Eight unequal sheared metal wedges surround a compact four-way impact, leaving open gaps.
        for(int i=0;i<8;i++){
            double r=i%2==0?222:177,angle=i*Math.PI/4+.13,c=Math.cos(angle),s=Math.sin(angle),w=i%2==0?12:8;
            a.polygon(i%2==0?195:140,256+c*30-s*w,256+s*30+c*w,256+c*r,256+s*r,
                    256+c*60+s*w*.32,256+s*60-c*w*.32);
            a.radialLine(120,2,angle+.10,93,r*.92);
        }
        // Broken octagonal pressure rims and short gauge cuts communicate a stamped mechanical contact.
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4,other=angle+Math.PI/4;
            for(int rim=0;rim<2;rim++){
                double r=rim==0?91:139;
                double x=256+Math.cos(angle)*r,y=256+Math.sin(angle)*r;
                double tx=256+Math.cos(other)*r,ty=256+Math.sin(other)*r;
                a.line(rim==0?205:82,rim==0?4:2,x+(tx-x)*.16,y+(ty-y)*.16,x+(tx-x)*.80,y+(ty-y)*.80);
            }
            a.radialLine(168,3,angle,148,160);
        }
        a.polygon(240,210,251,244,244,251,204,260,243,305,251,265,260,257,307,247,264);
        a.polygon(255,230,255,253,249,256,225,260,250,282,256,260,261,255,286,250,260);
        return a;
    }
    private static GenerateRivetTextures friction(){
        var a=new GenerateRivetTextures(512,128,"Rivet machined friction wake");
        a.polygon(45,24,66,170,53,354,40,488,63,370,87,180,75);
        a.polygon(170,30,65,245,58,464,62,486,64,372,69,180,68);
        a.polygon(245,82,64,345,61,486,64,340,66);
        for(int i=0;i<12;i++){
            double x=80+i*31,span=7+5*Math.sin(i*.8);
            a.line(110,1.5,x,50-span,x+22,55-span*.35);
            a.line(95,1.5,x+4,78+span*.3,x+29,71+span*.3);
        }
        a.line(195,2,118,56,430,50);a.line(130,1.5,180,75,466,72);
        return a;
    }
    private void save(Path source,Path textures,String name)throws Exception{
        g.dispose();Files.writeString(source.resolve(name+".svg"),svg.append("</g></svg>\n"));
        ImageIO.write(image,"png",textures.resolve(name+".png").toFile());
        ImageIO.write(image,"png",source.resolve(name+".png").toFile());
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args.length==0?".":args[0]),source=root.resolve("art_source/rivet/vfx"),textures=root.resolve("src/main/resources/assets/myriad/textures/particle/vfx");
        Files.createDirectories(source);Files.createDirectories(textures);
        compression().save(source,textures,"rivet_hd_compression");friction().save(source,textures,"rivet_hd_friction");
        System.out.println("Rivet: generated two original transparent PNGs and matching editable SVG paths");
    }
}
