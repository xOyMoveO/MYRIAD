import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Original editable path/analytic textures. Run with Java 25; no external artwork. */
public class GenerateNightTextures {
    static final int N=256;
    static Graphics2D graphics(BufferedImage image){
        var g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);return g;
    }
    static Path2D path(double... xy){var p=new Path2D.Double();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);p.closePath();return p;}
    static BufferedImage make(int type){
        var image=new BufferedImage(N,N,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);
        if(type==0){ // Right-pointing short dagger: distinct grip, guard and asymmetric blade bevel.
            g.setColor(new Color(73,56,102));g.fill(path(24,119,64,119,69,115,82,115,82,141,69,141,64,137,24,137));
            g.setColor(new Color(196,177,226));g.fill(path(73,94,84,94,93,116,235,128,93,140,84,162,73,162,79,132,79,124));
            g.setColor(new Color(244,239,255));g.fill(path(89,117,136,102,235,128,101,128));
            g.setColor(new Color(128,108,167));g.fill(path(101,128,235,128,136,151,89,139));
            g.setColor(Color.WHITE);g.setStroke(new BasicStroke(2.5f));g.draw(new Line2D.Double(98,119,230,128));
            g.setColor(new Color(226,215,242));for(int x=30;x<65;x+=9)g.drawLine(x,121,x+4,135);
        }else if(type==1){ // Wispy ribbon. White RGB is tinted by the material; alpha carries detail.
            for(int y=8;y<248;y++)for(int x=8;x<248;x++){
                double t=(x-8)/240.0,center=128+16*Math.sin(t*7),width=3+25*Math.pow(t,.6);
                double d=(y-center)/width,grain=.72+.28*Math.sin(x*.23+y*.19)*Math.sin(y*.31-x*.07);
                double a=Math.exp(-d*d*2.4)*Math.pow(Math.sin(t*Math.PI),.5)*grain;
                image.setRGB(x,y,((int)(Math.min(.9,a)*255)<<24)|0xFFFFFF);
            }
        }else if(type==2){ // Ragged smoke, no rectangular opaque backplate.
            for(int y=9;y<247;y++)for(int x=9;x<247;x++){
                double dx=(x-128)/103.0,dy=(y-128)/103.0;
                double r=Math.sqrt(dx*dx+dy*dy),angle=Math.atan2(dy,dx);
                double contour=.79+.09*Math.sin(angle*5)+.07*Math.cos(angle*9);
                double noise=.65+.2*Math.sin(x*.035+Math.sin(y*.025)*2)+.08*Math.cos(y*.07+x*.015);
                double a=Math.pow(Math.max(0,1-r/contour),1.5)*Math.max(.12,noise);
                image.setRGB(x,y,((int)(Math.min(.8,a)*255)<<24)|0xFFFFFF);
            }
        }else if(type==3){ // Tapered, open crescent, readable on all backgrounds.
            var outer=new Path2D.Double();outer.moveTo(26,191);outer.curveTo(35,55,167,12,238,73);
            outer.curveTo(160,42,66,102,26,191);outer.closePath();
            g.setColor(new Color(255,255,255,210));g.fill(outer);
            g.setStroke(new BasicStroke(3.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            var edge=new Path2D.Double();edge.moveTo(27,187);edge.curveTo(38,59,166,13,234,71);g.setColor(Color.WHITE);g.draw(edge);
            g.setColor(new Color(255,255,255,75));g.setStroke(new BasicStroke(1.5f));
            edge=new Path2D.Double();edge.moveTo(35,179);edge.curveTo(78,76,164,38,221,69);g.draw(edge);
        }else if(type==4){ // Blade-hit cross: narrow hot centre, long tapered diagonal splinters.
            g.translate(128,128);g.rotate(.62);
            g.setColor(new Color(255,255,255,85));g.fill(new Ellipse2D.Double(-31,-31,62,62));
            g.setColor(new Color(255,255,255,230));g.fill(path(-112,0,-14,-5,0,-80,9,-5,112,0,12,5,0,88,-8,5));
            g.setColor(Color.WHITE);g.fill(path(-70,0,-5,-2,0,-49,4,-2,72,0,4,2,0,53,-4,2));
        }else{ // Broken assassin-eye seal, not a generic full magic circle.
            g.setColor(new Color(255,255,255,190));g.setStroke(new BasicStroke(3.5f));
            g.draw(new Arc2D.Double(33,33,190,190,20,125,Arc2D.OPEN));g.draw(new Arc2D.Double(33,33,190,190,200,125,Arc2D.OPEN));
            g.setColor(new Color(255,255,255,225));g.fill(path(46,129,102,103,128,70,154,103,210,129,154,154,128,188,102,154,46,129,112,130,128,145,144,130,128,111,112,130));
            g.setColor(Color.WHITE);g.fill(path(125,102,134,128,125,154,120,128));
        }
        g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),preview=Path.of(args[1]);Files.createDirectories(out);Files.createDirectories(preview.getParent());
        String[] names={"night_blade","night_trail","night_smoke","night_slash","night_impact","night_seal"};
        var board=new BufferedImage(6*192,4*192,BufferedImage.TYPE_INT_RGB);var g=graphics(board);
        Color[] backgrounds={new Color(17,22,35),new Color(215,223,220),new Color(72,108,48),new Color(40,125,165)};
        for(int i=0;i<names.length;i++){
            var image=make(i);ImageIO.write(image,"png",out.resolve(names[i]+".png").toFile());
            for(int row=0;row<4;row++){
                int x=i*192,y=row*192;g.setColor(backgrounds[row]);g.fillRect(x,y,192,192);
                g.drawImage(image,x+16,y+20,160,160,null);g.setColor(row==1?Color.BLACK:Color.WHITE);g.drawString(names[i],x+8,y+14);
            }
            int transparent=0,partial=0;for(int y=0;y<N;y++)for(int x=0;x<N;x++){
                int alpha=image.getRGB(x,y)>>>24;if(alpha==0)transparent++;else if(alpha<255)partial++;
                if((x<8||x>=248||y<8||y>=248)&&alpha!=0)throw new IllegalStateException("Unsafe border: "+names[i]);
            }
            if(transparent<10000||partial<50)throw new IllegalStateException("Alpha contract: "+names[i]);
            System.out.println(names[i]+": 256x256 RGBA, transparent="+transparent+", partial="+partial);
        }
        g.dispose();ImageIO.write(board,"png",preview.toFile());
    }
}
