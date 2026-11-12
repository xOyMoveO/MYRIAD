import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
/** Add transparent safety margins only; all native generated pixels remain unchanged. */
class ShuoSkillAssets {
    public static void main(String[] args)throws Exception{
        for(String name:new String[]{"guard","stream","impact"}){
            var src=ImageIO.read(Path.of(args[0],"shuo_"+name+"_hd.png").toFile());
            if(src==null||!src.getColorModel().hasAlpha())throw new IllegalStateException("RGBA master required");
            int w=name.equals("stream")?2432:1536,h=name.equals("stream")?1024:1536;
            if(src.getWidth()>w-200||src.getHeight()>h-200)throw new IllegalStateException("Insufficient padding");
            var dest=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            dest.setRGB((w-src.getWidth())/2,(h-src.getHeight())/2,src.getWidth(),src.getHeight(),src.getRGB(0,0,src.getWidth(),src.getHeight(),null,0,src.getWidth()),0,src.getWidth());
            var at=Path.of(args[1],"shuo_"+name+"_hd.png");Files.createDirectories(at.getParent());ImageIO.write(dest,"png",at.toFile());
            System.out.println(name+": "+src.getWidth()+"x"+src.getHeight()+" -> "+w+"x"+h+", transparent padding only");
        }
    }
}
