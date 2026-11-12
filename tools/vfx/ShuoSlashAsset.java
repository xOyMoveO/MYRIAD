import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
/** Mechanical transparent padding only: preserve the generated art without resampling. */
class ShuoSlashAsset {
    public static void main(String[] args)throws Exception{
        var source=ImageIO.read(Path.of(args[0]).toFile());
        if(source==null||!source.getColorModel().hasAlpha()||source.getWidth()>1400||source.getHeight()>1400)throw new IllegalStateException("Unexpected master");
        var out=new BufferedImage(1536,1536,BufferedImage.TYPE_INT_ARGB);
        int dx=(1536-source.getWidth())/2,dy=(1536-source.getHeight())/2;
        int[] pixels=source.getRGB(0,0,source.getWidth(),source.getHeight(),null,0,source.getWidth());
        out.setRGB(dx,dy,source.getWidth(),source.getHeight(),pixels,0,source.getWidth());
        Path target=Path.of(args[1]);Files.createDirectories(target.getParent());ImageIO.write(out,"png",target.toFile());
        System.out.println("1536px RGBA padded master; original pixels preserved without resampling");
    }
}
