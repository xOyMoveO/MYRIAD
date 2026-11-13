package com.myriad.tools;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonParser;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;

/** CPU projection of production meshes and actual sprite pixels/UVs. No game scene or client claim. */
public final class PreviewRoleMeshes {
    public record Panel(String label,List<Quad> mesh,double scale,Vec3 focus) {}
    private static final int W=440,H=330,HEADER=36;
    private static Path resourceRoot=Path.of("src/main/resources");
    private static List<String> materialPaths;
    private static final Map<Integer,BufferedImage> textures=new HashMap<>();
    private static final Map<String,BufferedImage> tints=new HashMap<>();
    private PreviewRoleMeshes() {}
    /** Optional explicit project resource root; existing callers may keep using board(...) unchanged. */
    public static void loadMaterials(Path resources) {
        resourceRoot=resources;materialPaths=null;textures.clear();tints.clear();
        try {
            var names=JsonParser.parseString(Files.readString(resources.resolve("assets/myriad/particles/ability_mesh.json"))).getAsJsonObject().getAsJsonArray("textures");
            materialPaths=new ArrayList<>();for(var name:names)materialPaths.add(name.getAsString());
        }catch(IOException failure){throw new IllegalStateException("Cannot load the production particle sprite list",failure);}
    }
    private static BufferedImage texture(int material) {
        if(materialPaths==null)loadMaterials(resourceRoot);
        if(material<0||material>=materialPaths.size())throw new IllegalArgumentException("Unregistered material "+material);
        return textures.computeIfAbsent(material,index->{
            String[] id=materialPaths.get(index).split(":",2);
            String relative="assets/"+id[0]+"/textures/particle/"+id[1]+".png";
            try {
                Path file=resourceRoot.resolve(relative);BufferedImage image;
                if(Files.isRegularFile(file))image=ImageIO.read(file.toFile());
                else try(var stream=PreviewRoleMeshes.class.getResourceAsStream("/"+relative)){
                    if(stream==null)throw new IOException("Missing atlas sprite "+relative);
                    image=ImageIO.read(stream);
                }
                if(image==null)throw new IOException("Invalid PNG "+relative);
                return image;
            }catch(IOException failure){throw new IllegalStateException("Cannot decode material "+index+" / "+relative,failure);}
        });
    }
    private static BufferedImage tint(int material,int rgb) {
        var source=texture(material);
        return tints.computeIfAbsent(material+":"+rgb,key->{
            var result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){
                int p=source.getRGB(x,y),r=((p>>>16)&255)*((rgb>>>16)&255)/255,g=((p>>>8)&255)*((rgb>>>8)&255)/255,b=(p&255)*(rgb&255)/255;
                result.setRGB(x,y,(p&0xFF000000)|(r<<16)|(g<<8)|b);
            }
            return result;
        });
    }
    private static void triangle(Graphics2D graphics,BufferedImage texture,double[][] xy,double[][] uv) {
        double sx=uv[1][0]-uv[0][0],sy=uv[1][1]-uv[0][1],tx=uv[2][0]-uv[0][0],ty=uv[2][1]-uv[0][1];
        double determinant=sx*ty-tx*sy;if(Math.abs(determinant)<1E-10)return;
        double dx=xy[1][0]-xy[0][0],dy=xy[1][1]-xy[0][1],ex=xy[2][0]-xy[0][0],ey=xy[2][1]-xy[0][1];
        double a=(dx*ty-ex*sy)/determinant,b=(dy*ty-ey*sy)/determinant,c=(ex*sx-dx*tx)/determinant,d=(ey*sx-dy*tx)/determinant;
        var transform=new AffineTransform(a,b,c,d,xy[0][0]-a*uv[0][0]-c*uv[0][1],xy[0][1]-b*uv[0][0]-d*uv[0][1]);
        var shape=new Path2D.Double();shape.moveTo(xy[0][0],xy[0][1]);shape.lineTo(xy[1][0],xy[1][1]);shape.lineTo(xy[2][0],xy[2][1]);shape.closePath();
        var child=(Graphics2D)graphics.create();child.clip(shape);child.drawImage(texture,transform,null);child.dispose();
    }
    private static double[] project(Vec3 p) {
        return new double[]{.832*p.x-.555*p.z,-.308*p.x-.832*p.y-.462*p.z};
    }
    private static double depth(Quad q) {
        Vec3 p=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);
        return .462*p.x-.555*p.y+.693*p.z;
    }
    public static BufferedImage board(String title,List<Panel> panels,int columns,boolean dark) {
        int rows=(panels.size()+columns-1)/columns;
        var image=new BufferedImage(W*columns,H*rows+HEADER,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(new Color(17,22,29));g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setColor(new Color(235,235,226));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        g.drawString(title+" / production mesh + atlas CPU projection / NOT a game capture",12,23);
        for(int i=0;i<panels.size();i++) {
            Panel p=panels.get(i);int x=i%columns*W,y=i/columns*H+HEADER;
            g.setColor(dark?new Color(18,26,38):new Color(117,137,107));g.fillRect(x+1,y+1,W-2,H-2);
            var cell=(Graphics2D)g.create();cell.clipRect(x+2,y+27,W-4,H-29);
            cell.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            double ox=x+W*.5,oy=y+H*.62;
            cell.setColor(new Color(240,244,230,24));
            for(int k=-8;k<=8;k++) {
                double[] a=project(new Vec3(k,-.08,-8).subtract(p.focus)),b=project(new Vec3(k,-.08,8).subtract(p.focus));
                cell.drawLine((int)(ox+a[0]*p.scale),(int)(oy+a[1]*p.scale),(int)(ox+b[0]*p.scale),(int)(oy+b[1]*p.scale));
            }
            var mesh=new ArrayList<>(p.mesh);mesh.sort(Comparator.comparingDouble(PreviewRoleMeshes::depth).reversed());
            for(Quad q:mesh) {
                double[][] xy=new double[4][];Vec3[] v={q.a(),q.b(),q.c(),q.d()};
                for(int j=0;j<4;j++){xy[j]=project(v[j].subtract(p.focus));xy[j][0]=ox+xy[j][0]*p.scale;xy[j][1]=oy+xy[j][1]*p.scale;}
                double area=0;for(int j=0;j<4;j++)area+=xy[j][0]*xy[(j+1)%4][1]-xy[(j+1)%4][0]*xy[j][1];
                // Production planes have both windings: rasterize exactly one visible side.
                if(area<=1E-5)continue;
                var sprite=tint(q.material(),q.color());
                cell.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,q.alpha()));
                if(q.material()==0){
                    // Production AbilityMeshState samples the centre texel for material zero.
                    var path=new Path2D.Double();path.moveTo(xy[0][0],xy[0][1]);for(int j=1;j<4;j++)path.lineTo(xy[j][0],xy[j][1]);path.closePath();
                    cell.setColor(new Color(sprite.getRGB(sprite.getWidth()/2,sprite.getHeight()/2),true));cell.fill(path);
                }else{
                    double u0=q.startU()*sprite.getWidth(),u1=q.endU()*sprite.getWidth(),v0=q.startV()*sprite.getHeight(),v1=q.endV()*sprite.getHeight();
                    if(q.reverseV()){double swap=v0;v0=v1;v1=swap;}
                    double[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
                    triangle(cell,sprite,new double[][]{xy[0],xy[1],xy[2]},new double[][]{uv[0],uv[1],uv[2]});
                    triangle(cell,sprite,new double[][]{xy[0],xy[2],xy[3]},new double[][]{uv[0],uv[2],uv[3]});
                }
            }
            cell.dispose();g.setColor(new Color(235,235,226));g.drawString(p.label,x+10,y+20);
        }
        g.dispose();return image;
    }
    public static void png(Path path,BufferedImage image)throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());ImageIO.write(image,"png",path.toFile());
    }
    public static void gif(Path path,List<BufferedImage> frames,int delayMs)throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());var writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(var stream=ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int i=0;i<frames.size();i++) {
                var frame=frames.get(i);var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),null);
                String format=metadata.getNativeMetadataFormatName();var tree=(IIOMetadataNode)metadata.getAsTree(format);
                var control=(IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime",Integer.toString(Math.max(1,delayMs/10)));control.setAttribute("disposalMethod","restoreToBackgroundColor");
                if(i==0){var apps=new IIOMetadataNode("ApplicationExtensions");var app=new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});apps.appendChild(app);tree.appendChild(apps);}
                metadata.setFromTree(format,tree);writer.writeToSequence(new IIOImage(frame,null,metadata),null);
            }
            writer.endWriteSequence();
        }finally{writer.dispose();}
    }
}
