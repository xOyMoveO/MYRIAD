import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Original editable material paths for four roles. Straight-alpha PNGs, no external artwork. */
public class GenerateElementTextures {
    static final String[] NAMES={"thunder_bolt","thunder_slash","thunder_scorch","thunder_seal",
            "fire_flame","fire_slash","fire_crack","fire_smoke","ink_talisman","ink_seal","ink_bind","ink_break",
            "wind_blade","wind_streak","wind_sigil","wind_impact"};
    static Path2D polygon(double... xy){var p=new Path2D.Double();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);p.closePath();return p;}
    static Graphics2D graphics(BufferedImage image){var g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);return g;}
    static void stroke(Graphics2D g,Shape path,Color color,float width){g.setColor(color);g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(path);}
    static Path2D crescent(){var p=new Path2D.Double();p.moveTo(22,194);p.curveTo(28,58,163,19,237,75);p.curveTo(165,43,78,99,22,194);p.closePath();return p;}
    static BufferedImage make(int id){
        var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);
        switch(id){
            case 0 -> {
                var p=new Path2D.Double();p.moveTo(17,132);p.lineTo(57,125);p.lineTo(91,140);p.lineTo(116,113);p.lineTo(144,135);p.lineTo(172,117);p.lineTo(203,126);p.lineTo(239,124);
                stroke(g,p,new Color(27,68,116),15);stroke(g,p,new Color(58,153,221),10);stroke(g,p,new Color(240,250,255),3);
                stroke(g,polygon(89,138,72,163,49,174),new Color(128,204,250),2.5f);
                stroke(g,polygon(170,118,182,92,208,80),new Color(166,224,255),2.5f);
            }
            case 1 -> {
                g.setColor(new Color(34,93,152));g.fill(crescent());
                var p=new Path2D.Double();p.moveTo(25,190);p.curveTo(31,65,159,22,233,73);p.lineTo(179,70);p.lineTo(139,85);p.lineTo(111,91);p.lineTo(99,117);p.lineTo(78,123);p.closePath();
                g.setColor(new Color(180,226,255));g.fill(p);stroke(g,crescent(),new Color(242,251,255),2);
            }
            case 2,6 -> {
                boolean fire=id==6;g.setColor(fire?new Color(61,28,18,170):new Color(25,38,48,180));g.fill(new Ellipse2D.Double(31,38,196,178));
                for(int i=0;i<9;i++){
                    double angle=i*2.399963;var p=new Path2D.Double();p.moveTo(128,128);
                    for(int j=1;j<=4;j++){double r=23*j;p.lineTo(128+Math.cos(angle+j*.12)*r,128+Math.sin(angle-j*.09)*r);}
                    stroke(g,p,fire?new Color(134,52,22):new Color(47,75,94),7);
                    stroke(g,p,fire?new Color(255,139,47):new Color(146,211,242),fire?2.5f:1.8f);
                }
                g.setColor(fire?new Color(255,193,82):new Color(222,242,255));g.fill(new Ellipse2D.Double(120,120,16,16));
            }
            case 3,9 -> {
                boolean ink=id==9;Color dark=ink?new Color(47,57,66):new Color(35,99,160),light=ink?new Color(207,175,113):new Color(201,232,252);
                stroke(g,new Ellipse2D.Double(20,20,216,216),dark,7);stroke(g,new Ellipse2D.Double(30,30,196,196),light,2);
                stroke(g,new Ellipse2D.Double(63,63,130,130),dark,5);
                for(int i=0;i<8;i++){
                    var saved=g.getTransform();g.rotate(i*Math.PI/4,128,128);
                    stroke(g,new Line2D.Double(111,42,145,42),light,4);stroke(g,new Line2D.Double(112,51,123,51),dark,4);stroke(g,new Line2D.Double(133,51,144,51),dark,4);
                    stroke(g,new Line2D.Double(128,62,128,79),light,3);g.setTransform(saved);
                }
                g.setColor(dark);g.fill(polygon(128,88,157,128,128,168,99,128));
                stroke(g,polygon(128,102,143,128,128,154,113,128),light,2);
            }
            case 4 -> {
                var p=new Path2D.Double();p.moveTo(50,222);p.curveTo(16,158,90,137,63,85);p.curveTo(113,100,109,49,142,19);p.curveTo(120,92,204,99,183,158);p.curveTo(223,125,226,179,204,216);p.curveTo(164,243,89,241,50,222);p.closePath();
                g.setColor(new Color(150,44,20,235));g.fill(p);
                p=new Path2D.Double();p.moveTo(68,218);p.curveTo(49,173,120,156,101,107);p.curveTo(129,141,137,76,144,52);p.curveTo(149,126,193,125,173,180);p.curveTo(206,169,202,215,182,225);p.closePath();
                g.setColor(new Color(253,132,34,250));g.fill(p);
                p=new Path2D.Double();p.moveTo(94,222);p.curveTo(80,188,143,175,135,137);p.curveTo(176,166,161,203,169,225);p.closePath();
                g.setColor(new Color(255,225,139));g.fill(p);
            }
            case 5 -> {
                g.setColor(new Color(140,43,20));g.fill(crescent());
                g.translate(6,-5);g.scale(.95,.96);g.setColor(new Color(245,114,28));g.fill(crescent());
                stroke(g,crescent(),new Color(255,221,134),3);
                for(int i=0;i<6;i++){double x=44+i*25,y=140-i*12;g.setColor(new Color(255,159,58));g.fill(polygon(x,y,x+5,y-22,x+11,y-5));}
            }
            case 7 -> {
                for(int y=9;y<247;y++)for(int x=9;x<247;x++){
                    double dx=(x-128)/102.0,dy=(y-128)/102.0,r=Math.sqrt(dx*dx+dy*dy);
                    double angle=Math.atan2(dy,dx),edge=.86+.07*Math.sin(angle*7);
                    double noise=.63+.19*Math.sin(x*.043+Math.sin(y*.03)*2);
                    int alpha=(int)(255*.68*Math.pow(Math.max(0,1-r/edge),1.15)*noise);
                    image.setRGB(x,y,(alpha<<24)|0x554A40);
                }
            }
            case 8 -> {
                g.setColor(new Color(89,68,42));g.fill(polygon(73,19,181,23,186,228,155,237,126,228,96,236,70,224));
                g.setColor(new Color(225,204,151));g.fill(polygon(78,24,176,28,181,224,154,232,126,223,97,231,75,220));
                stroke(g,new Rectangle2D.Double(85,36,81,171),new Color(157,105,58),2);
                g.setColor(new Color(57,54,48));g.fill(polygon(115,52,145,51,138,82,155,95,135,113,151,140,132,146,134,182,119,192,111,170,119,147,103,137,121,120,104,102,122,89));
                stroke(g,new Line2D.Double(100,67,155,67),new Color(131,61,40),5);stroke(g,new Line2D.Double(96,158,159,158),new Color(131,61,40),4);
                stroke(g,new Line2D.Double(78,119,180,125),new Color(255,237,193,130),2);
            }
            case 10 -> {
                var p=new Path2D.Double();p.moveTo(17,132);p.curveTo(65,103,95,154,132,127);p.curveTo(172,99,204,145,239,119);
                stroke(g,p,new Color(48,58,67),15);stroke(g,p,new Color(179,154,105),4);
                for(int i=0;i<6;i++){var old=g.getTransform();g.translate(36+i*35,127+Math.sin(i)*7);g.rotate(.4*i);stroke(g,new Rectangle2D.Double(-9,-10,18,20),new Color(213,185,123),3);g.setTransform(old);}
            }
            case 11 -> {
                g.setColor(new Color(52,62,69));g.fill(polygon(27,130,67,97,91,114,118,34,133,95,167,63,156,120,228,143,163,151,181,205,132,174,103,229,100,160,49,185,80,143));
                stroke(g,polygon(118,51,126,129,215,143,127,139,105,213,113,139,42,133,114,128),new Color(223,191,126),3);
            }
            case 12 -> {
                g.setColor(new Color(42,97,83));g.fill(crescent());
                var inner=new Path2D.Double();inner.moveTo(27,182);inner.curveTo(44,73,160,27,226,70);inner.curveTo(163,50,86,110,27,182);inner.closePath();
                g.setColor(new Color(164,213,191));g.fill(inner);stroke(g,crescent(),new Color(234,251,240),3);
                stroke(g,new CubicCurve2D.Double(38,153,86,80,160,47,207,65),new Color(63,135,113),2);
            }
            case 13 -> {
                for(int y=9;y<247;y++)for(int x=9;x<247;x++){
                    double t=(x-9)/238.0,centre=128+12*Math.sin(t*6.2),w=3+19*Math.pow(t,.7);
                    double a=Math.exp(-Math.pow((y-centre)/w,2)*2)*Math.pow(Math.sin(t*Math.PI),.4);
                    int alpha=(int)(255*a*.94);image.setRGB(x,y,(alpha<<24)|0xDAF1E3);
                }
                stroke(g,new CubicCurve2D.Double(26,138,90,117,151,145,233,122),new Color(246,255,249,210),2);
            }
            case 14 -> {
                stroke(g,new Arc2D.Double(31,45,194,165,25,245,Arc2D.OPEN),new Color(66,124,105),8);
                stroke(g,new Arc2D.Double(46,58,161,137,10,235,Arc2D.OPEN),new Color(198,232,212),3);
                g.setColor(new Color(228,249,235));g.fill(polygon(90,154,133,66,124,128,169,112,118,191,134,143));
            }
            case 15 -> {
                for(int i=0;i<5;i++){
                    var old=g.getTransform();g.rotate(i*Math.PI*2/5,128,128);g.translate(16,8);g.scale(.82,.82);
                    g.setColor(new Color(84,155,131,180));g.fill(crescent());stroke(g,crescent(),new Color(223,245,230,220),2);g.setTransform(old);
                }
            }
        }
        g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),preview=Path.of(args[1]);Files.createDirectories(out);Files.createDirectories(preview.getParent());
        var board=new BufferedImage(1120,1120,BufferedImage.TYPE_INT_RGB);var g=graphics(board);
        Color[] backgrounds={new Color(17,23,32),new Color(218,223,217),new Color(61,91,43),new Color(62,121,161)};
        for(int id=0;id<NAMES.length;id++){
            var image=make(id);int transparent=0,partial=0;
            for(int y=0;y<256;y++)for(int x=0;x<256;x++){
                int a=image.getRGB(x,y)>>>24;if(a==0)transparent++;else if(a<255)partial++;
                if((x<8||x>=248||y<8||y>=248)&&a!=0)throw new IllegalStateException("Unsafe edge: "+NAMES[id]+" "+x+","+y);
            }
            if(transparent<6000||partial<50)throw new IllegalStateException("Alpha contract: "+NAMES[id]);
            ImageIO.write(image,"png",out.resolve(NAMES[id]+".png").toFile());
            int x=id%4*280,y=id/4*280;g.setColor(new Color(24,29,37));g.fillRect(x,y,280,280);g.setColor(Color.WHITE);g.drawString(NAMES[id],x+8,y+14);
            for(int j=0;j<4;j++){int dx=x+4+j%2*138,dy=y+20+j/2*128;g.setColor(backgrounds[j]);g.fillRect(dx,dy,134,124);g.drawImage(image,dx+8,dy+3,118,118,null);}
            System.out.println(NAMES[id]+": 256x256 RGBA / transparent="+transparent+" / partial="+partial);
        }
        g.dispose();ImageIO.write(board,"png",preview.toFile());
    }
}
