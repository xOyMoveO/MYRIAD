import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Slice the accepted single 2x2 atlas; normalize native pixel grid, retain real straight alpha. */
public final class PrepareLingyuanPixelAtlas {
    public static void main(String[] args)throws Exception{
        var source=ImageIO.read(Path.of(args[0]).toFile());
        if(source.getWidth()%2!=0||source.getHeight()%2!=0)throw new IllegalArgumentException("Expected a divisible 2x2 atlas");
        Path out=Path.of(args[1]);Files.createDirectories(out);
        String[] names={"lingyuan_pixel_slash","lingyuan_pixel_stream","lingyuan_pixel_seal","lingyuan_pixel_impact"};
        int w=source.getWidth()/2,h=source.getHeight()/2;
        for(int i=0;i<4;i++){
            var nativePixels=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);var g=nativePixels.createGraphics();
            g.setComposite(AlphaComposite.Src);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int x=i%2*w,y=i/2*h;g.drawImage(source,8,8,120,120,x,y,x+w,y+h,null);g.dispose();
            var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();
            g.setComposite(AlphaComposite.Src);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(nativePixels,0,0,256,256,null);g.dispose();
            int transparent=0,partial=0,max=0;
            for(int row=0;row<256;row++)for(int col=0;col<256;col++){
                int a=image.getRGB(col,row)>>>24;max=Math.max(max,a);if(a==0)transparent++;else if(a<255)partial++;
                if((row<8||row>=248||col<8||col>=248)&&a!=0)throw new IllegalStateException("Effect touches safe border: "+names[i]);
            }
            if(transparent==0||max<180)throw new IllegalStateException("Invalid alpha or too faint: "+names[i]);
            ImageIO.write(image,"png",out.resolve(names[i]+".png").toFile());
            System.out.println(names[i]+": 256x256 RGBA, native 128px grid, transparent="+transparent+", partial="+partial+", maxAlpha="+max);
        }
    }
}
