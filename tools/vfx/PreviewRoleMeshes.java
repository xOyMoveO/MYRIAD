package com.myriad.tools;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;

/** CPU projection of production material-zero meshes. No Minecraft scene, bloom or client claim. */
public final class PreviewRoleMeshes {
    public record Panel(String label,List<Quad> mesh,double scale,Vec3 focus) {}
    private static final int W=440,H=330,HEADER=36;
    private PreviewRoleMeshes() {}
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
        g.drawString(title+" / production mesh CPU projection / NOT a game capture",12,23);
        for(int i=0;i<panels.size();i++) {
            Panel p=panels.get(i);int x=i%columns*W,y=i/columns*H+HEADER;
            g.setColor(dark?new Color(18,26,38):new Color(117,137,107));g.fillRect(x+1,y+1,W-2,H-2);
            var cell=(Graphics2D)g.create();cell.clipRect(x+2,y+27,W-4,H-29);
            double ox=x+W*.5,oy=y+H*.62;
            cell.setColor(new Color(240,244,230,24));
            for(int k=-8;k<=8;k++) {
                double[] a=project(new Vec3(k,-.08,-8).subtract(p.focus)),b=project(new Vec3(k,-.08,8).subtract(p.focus));
                cell.drawLine((int)(ox+a[0]*p.scale),(int)(oy+a[1]*p.scale),(int)(ox+b[0]*p.scale),(int)(oy+b[1]*p.scale));
            }
            var mesh=new ArrayList<>(p.mesh);mesh.sort(Comparator.comparingDouble(PreviewRoleMeshes::depth));
            for(Quad q:mesh) {
                double[][] xy=new double[4][];Vec3[] v={q.a(),q.b(),q.c(),q.d()};
                for(int j=0;j<4;j++){xy[j]=project(v[j].subtract(p.focus));xy[j][0]=ox+xy[j][0]*p.scale;xy[j][1]=oy+xy[j][1]*p.scale;}
                double area=0;for(int j=0;j<4;j++)area+=xy[j][0]*xy[(j+1)%4][1]-xy[(j+1)%4][0]*xy[j][1];
                // Production planes have both windings: rasterize exactly one visible side.
                if(area<=1E-5)continue;
                if(q.material()!=0)throw new IllegalArgumentException("This preview supports the roles' existing material-zero recipes only");
                var path=new Path2D.Double();path.moveTo(xy[0][0],xy[0][1]);for(int j=1;j<4;j++)path.lineTo(xy[j][0],xy[j][1]);path.closePath();
                cell.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,q.alpha()));cell.setColor(new Color(q.color()));cell.fill(path);
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
