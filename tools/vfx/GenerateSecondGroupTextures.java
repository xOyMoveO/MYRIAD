import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Original editable silhouettes. Straight-alpha RGBA, 256 square, eight-pixel safe border. */
public class GenerateSecondGroupTextures {
 static final String[] NAMES={"sword_blade","sword_slash","sword_sigil","sword_trail","ice_shard","ice_crest","ice_crack","ice_mist",
  "herb_leaf","herb_lotus","herb_seal","herb_dew","war_blade","war_slash","war_crack","war_dust","spirit_crane","spirit_knot","spirit_seal","spirit_thread"};
 static final Color[] LIGHT={new Color(244,249,235),new Color(235,251,255),new Color(242,240,193),new Color(255,220,155),new Color(255,246,209)};
 static final Color[] BODY={new Color(119,200,210),new Color(117,193,233),new Color(128,196,103),new Color(205,125,76),new Color(133,207,197)};
 static final Color[] DARK={new Color(45,80,112),new Color(37,86,132),new Color(36,88,59),new Color(91,52,46),new Color(45,86,105)};
 static Path2D poly(double...v){var p=new Path2D.Double();p.moveTo(v[0],v[1]);for(int i=2;i<v.length;i+=2)p.lineTo(v[i],v[i+1]);p.closePath();return p;}
 static void stroke(Graphics2D g,Shape shape,Color c,float w){g.setColor(c);g.setStroke(new BasicStroke(w,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);}
 static void fill(Graphics2D g,Shape shape,Color c){g.setColor(c);g.fill(shape);}
 static BufferedImage make(int id){
  var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  int family=id/4,kind=id%4;Color l=LIGHT[family],b=BODY[family],d=DARK[family];
  if(kind==0){switch(family){
   case 0->{fill(g,poly(34,119,196,113,239,128,196,143,34,137),d);fill(g,poly(34,119,196,113,239,128,36,128),l);fill(g,poly(36,128,239,128,196,143,34,137),b);stroke(g,new Line2D.Double(40,94,40,162),new Color(218,184,105),9);stroke(g,new Line2D.Double(14,128,40,128),d,12);}
   case 1->{fill(g,poly(18,128,94,83,236,128,94,173),d);fill(g,poly(18,128,94,83,113,128,236,128),l);fill(g,poly(18,128,113,128,94,173),b);stroke(g,new Line2D.Double(94,83,113,128),l,2);}
   case 2->{fill(g,poly(24,207,41,93,145,25,225,34,210,124,119,198),d);fill(g,poly(24,207,145,25,225,34),l);fill(g,poly(24,207,225,34,210,124,119,198),b);stroke(g,new Line2D.Double(30,202,214,43),new Color(219,209,129),5);for(int i=0;i<5;i++)stroke(g,new Line2D.Double(65+i*24,173-i*22,57+i*29,115-i*13),d,3);}
   case 3->{fill(g,poly(28,150,55,103,198,86,238,108,206,143,48,166),d);fill(g,poly(32,147,62,113,198,95,235,108,203,127),l);fill(g,poly(32,147,203,127,206,143,48,166),b);stroke(g,new Line2D.Double(47,94,47,182),new Color(156,96,56),8);}
   case 4->{fill(g,poly(16,111,112,123,72,29,130,85,183,37,151,129,237,107,175,159,133,141,97,217,109,144),d);fill(g,poly(16,111,112,123,72,29,130,85,130,139,97,217,109,144),l);fill(g,poly(130,85,183,37,151,129,237,107,175,159,133,141),b);stroke(g,new Line2D.Double(112,123,133,141),new Color(215,184,108),3);}
  }}else if(kind==1){
   if(family==0||family==3){var p=new Path2D.Double();p.moveTo(21,214);p.curveTo(28,60,157,21,236,72);p.curveTo(158,51,80,115,21,214);p.closePath();fill(g,p,d);var old=g.getTransform();g.translate(6,-4);g.scale(.96,.96);fill(g,p,b);stroke(g,p,l,family==3?5:3);g.setTransform(old);}
   else if(family==1){for(int i=0;i<6;i++){var old=g.getTransform();g.rotate(i*Math.PI/3,128,128);fill(g,poly(128,18,144,105,128,151,112,105),b);stroke(g,new Line2D.Double(128,25,128,134),l,4);stroke(g,poly(128,56,151,72,128,94,105,72),d,2);g.setTransform(old);}}
   else if(family==2){for(int i=0;i<7;i++){var old=g.getTransform();g.rotate(i*Math.PI*2/7,128,128);fill(g,poly(128,22,157,78,142,128,114,128,99,78),d);fill(g,poly(128,29,148,81,128,123,109,81),b);stroke(g,new Line2D.Double(128,40,128,117),l,3);g.setTransform(old);}fill(g,new Ellipse2D.Double(108,108,40,40),l);}
   else{stroke(g,poly(128,23,185,80,128,137,71,80),d,11);stroke(g,poly(128,23,185,80,128,137,71,80),l,5);stroke(g,poly(128,83,185,140,128,197,71,140),b,10);stroke(g,poly(128,83,185,140,128,197,71,140),l,3);stroke(g,new Line2D.Double(117,191,111,234),d,5);stroke(g,new Line2D.Double(139,191,146,234),l,5);}
  }else if(kind==2){
   if(family==1||family==3){for(int i=0;i<9;i++){double a=i*2.39996;var p=new Path2D.Double();p.moveTo(128,128);for(int j=1;j<=4;j++)p.lineTo(128+Math.cos(a+j*.1)*(23*j),128+Math.sin(a-j*.08)*(23*j));stroke(g,p,d,8);stroke(g,p,b,3);stroke(g,p,l,1.4f);}}
   else{stroke(g,new Ellipse2D.Double(20,20,216,216),d,8);stroke(g,new Ellipse2D.Double(28,28,200,200),l,3);stroke(g,new Ellipse2D.Double(65,65,126,126),b,5);for(int i=0;i<8;i++){var old=g.getTransform();g.rotate(i*Math.PI/4,128,128);stroke(g,new Line2D.Double(114,47,142,47),l,4);stroke(g,new Line2D.Double(128,56,128,77),b,4);g.setTransform(old);}
    if(family==2){for(int i=0;i<5;i++){var old=g.getTransform();g.rotate(i*Math.PI*2/5,128,128);fill(g,poly(128,77,144,104,128,140,112,104),b);stroke(g,new Line2D.Double(128,84,128,124),l,2);g.setTransform(old);}fill(g,new Ellipse2D.Double(120,120,16,16),l);}
    else if(family==4){stroke(g,poly(128,77,154,104,128,132,102,104),l,4);stroke(g,poly(128,113,154,140,128,168,102,140),b,6);stroke(g,new Line2D.Double(120,163,114,184),l,3);stroke(g,new Line2D.Double(136,163,144,184),l,3);}
    else{fill(g,poly(128,92,156,128,128,164,100,128),b);stroke(g,poly(128,102,146,128,128,154,110,128),l,2);}}
  }else if(family==2){fill(g,poly(128,25,173,103,181,156,164,201,128,222,92,201,75,156,83,103),d);fill(g,poly(128,32,161,111,165,162,145,197,105,191,91,155,98,112),b);fill(g,new Ellipse2D.Double(107,102,26,57),l);}
  else{for(int y=9;y<247;y++)for(int x=9;x<247;x++){
   double u=(x-12)/232.0,v=(y-128)/108.0,spread=.08+.56*u,wave=.12*Math.sin(u*15+family),s=Math.exp(-Math.pow((v-wave)/spread,2)*4)*Math.sin(Math.PI*u);
   int alpha=(int)(230*s);if(alpha<=0)continue;Color c=Math.abs(v-wave)<spread*.18?l:b;image.setRGB(x,y,(alpha<<24)|(c.getRGB()&0xFFFFFF));}
  }
  g.dispose();return image;
 }
 public static void main(String[] args)throws Exception{Path out=Path.of(args[0]);Files.createDirectories(out);var review=new BufferedImage(1280,1024,BufferedImage.TYPE_INT_RGB);var g=review.createGraphics();
  Color[] backgrounds={new Color(23,30,37),new Color(235,235,225),new Color(94,117,84),new Color(95,49,103)};
  for(int i=0;i<NAMES.length;i++){var image=make(i);ImageIO.write(image,"png",out.resolve(NAMES[i]+".png").toFile());int col=i%5,row=i/5;g.setColor(backgrounds[row]);g.fillRect(col*256,row*256,256,256);g.drawImage(image,col*256,row*256,null);g.setColor(row==1?Color.BLACK:Color.WHITE);g.drawString(NAMES[i],col*256+8,row*256+247);}
  g.dispose();ImageIO.write(review,"png",Path.of(args[1]).toFile());
 }
}
